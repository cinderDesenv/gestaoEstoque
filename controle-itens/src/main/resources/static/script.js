/* script.js
   Arquivo completo e integrado:
   - CRUD itens (criar, listar, excluir)
   - Retirada / Devolução
   - Listas: movimentações ativas e devoluções realizadas
   - Auditoria (carregar, buscar localmente)
   - Formatação de datas e descrições
   - Modais, toasts, timeout nas requisições e tratamento robusto de erros
   - Login transition animation
*/

const API_BASE = 'http://localhost:8080';
const API_PREFIX = '/api';
const ROLE_KEY = 'userRole';
const DEFAULT_TIMEOUT_MS = 10000;

/* ---------------- Helpers ---------------- */
const qs = sel => document.querySelector(sel);
const qsa = sel => Array.from(document.querySelectorAll(sel));
const encode = v => encodeURIComponent(String(v));

function buildUrl(path) {
  if (!path) path = '';
  if (!path.startsWith('/')) path = '/' + path;
  return `${API_BASE}${API_PREFIX}${path}`;
}

function isNetworkError(err) {
  return err && (err instanceof TypeError || /Network error|aborted|Failed to fetch/i.test(err.message));
}

/* Fetch with timeout using AbortController */
async function fetchWithTimeout(url, opts = {}, timeoutMs = DEFAULT_TIMEOUT_MS) {
  const controller = new AbortController();
  const id = setTimeout(() => controller.abort(), timeoutMs);
  opts.signal = controller.signal;
  try {
    const res = await fetch(url, opts);
    return res;
  } finally {
    clearTimeout(id);
  }
}

async function parseResponse(res) {
  const ct = (res.headers.get('content-type') || '').toLowerCase();
  if (res.status === 204) return null;
  if (ct.includes('application/json')) return res.json();
  return res.text().catch(() => null);
}

async function fetchJsonUrl(url, opts = {}) {
  let res;
  try {
    res = await fetchWithTimeout(url, opts, DEFAULT_TIMEOUT_MS);
  } catch (err) {
    const e = new Error('Network error or request aborted');
    e.cause = err;
    throw e;
  }

  if (!res.ok) {
    let body = null;
    try { body = await parseResponse(res); } catch { body = null; }
    const err = new Error(`${res.status} ${res.statusText} — ${body || '(sem corpo)'}`);
    err.status = res.status;
    err.body = body;
    throw err;
  }

  return parseResponse(res);
}

/* Simple API wrappers */
async function apiGet(path) {
  return fetchJsonUrl(buildUrl(path), { method: 'GET' });
}
async function apiPost(path, body) {
  return fetchJsonUrl(buildUrl(path), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
}
async function apiDelete(path) {
  return fetchJsonUrl(buildUrl(path), { method: 'DELETE' });
}

/* ---------------- UI Utilities ---------------- */
function createToastContainer() {
  let c = qs('#toastContainer');
  if (!c) {
    c = document.createElement('div');
    c.id = 'toastContainer';
    c.className = 'toast-container';
    c.setAttribute('aria-live', 'polite');
    document.body.appendChild(c);
  }
  return c;
}
function showToast(text, type = 'info') {
  const container = createToastContainer();
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.textContent = text;
  container.appendChild(el);
  requestAnimationFrame(() => el.classList.add('visible'));
  setTimeout(() => el.classList.remove('visible'), 3000);
  setTimeout(() => el.remove(), 3400);
}

function escapeHtml(s = '') {
  return String(s).replace(/[&<>"']/g, c => ({
    '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
  })[c]);
}

function formatDateTimeIsoToBr(iso) {
  if (!iso) return '-';
  try {
    const d = (typeof iso === 'string') ? new Date(iso) : iso;
    if (isNaN(d)) return iso;
    const pad = n => n.toString().padStart(2, '0');
    const day = pad(d.getDate());
    const month = pad(d.getMonth() + 1);
    const year = d.getFullYear();
    const hours = pad(d.getHours());
    const mins = pad(d.getMinutes());
    return `${day}/${month}/${year} ${hours}:${mins}`;
  } catch {
    return iso;
  }
}

function formatDescription(text, maxLen = 120) {
  if (!text) return '-';
  const t = String(text).trim();
  if (t.length <= maxLen) return escapeHtml(t);
  const short = t.slice(0, maxLen - 3) + '...';
  return `<span title="${escapeHtml(t)}">${escapeHtml(short)}</span>`;
}

/* ---------------- State ---------------- */
let auditLogs = [];
let currentRetItem = null;
let currentDevItem = null;
let deleteTargetId = null;

/* ---------------- Data loaders ---------------- */
async function fetchStockForItem(itemId) {
  try {
    const data = await apiGet(`movimentacao/estoque/${encode(itemId)}`);
    return data || { quantidadeTotal: 0, quantidadeDisponivel: 0 };
  } catch (err) {
    console.warn('[fetchStockForItem] failed for', itemId, err);
    return { quantidadeTotal: 0, quantidadeDisponivel: 0 };
  }
}

async function carregarItens(filter = '') {
  const emptyEl = qs('#itensEmpty');
  if (emptyEl) emptyEl.textContent = 'Carregando itens...';
  try {
    const itens = await apiGet('itens');
    const tbody = qs('#itemList');
    if (!tbody) {
      if (emptyEl) emptyEl.textContent = '';
      return;
    }
    tbody.innerHTML = '';

    if (!Array.isArray(itens) || itens.length === 0) {
      if (emptyEl) emptyEl.textContent = 'Nenhum item encontrado.';
      return;
    }

    const withStock = await Promise.all(itens.map(async it => {
      const estoque = await fetchStockForItem(it.id);
      return { item: it, estoque };
    }));

    const filtered = withStock.filter(({ item }) =>
      item.nome.toLowerCase().includes(String(filter).toLowerCase())
    );

    if (filtered.length === 0) {
      if (emptyEl) emptyEl.textContent = 'Nenhum item corresponde à busca.';
      return;
    }

    if (emptyEl) emptyEl.textContent = '';

    const tpl = qs('#item-row-template');
    filtered.forEach(({ item, estoque }) => {
      const row = tpl.content.firstElementChild.cloneNode(true);
      row.querySelector('.cell-name').textContent = item.nome || '-';
      row.querySelector('.cell-patrimonio').textContent = item.patrimonio || '-';
      row.querySelector('.cell-desc').innerHTML = formatDescription(item.descricao || '-');
      row.querySelector('.cell-available').textContent = estoque.quantidadeDisponivel ?? 0;
      row.querySelector('.cell-total').textContent = estoque.quantidadeTotal ?? 0;

      const actions = row.querySelector('.cell-actions');
      actions.innerHTML = '';

      const retBtn = document.createElement('button');
      retBtn.className = 'btn small';
      retBtn.textContent = 'Movimentar';
      retBtn.addEventListener('click', () => abrirRetidaModal(item.id, item.nome));
      actions.appendChild(retBtn);

      const delBtn = document.createElement('button');
      delBtn.className = 'btn small danger';
      delBtn.textContent = 'Excluir';
      delBtn.addEventListener('click', () => openDeleteConfirm(item.id, item.nome));
      actions.appendChild(delBtn);

      tbody.appendChild(row);
    });
  } catch (err) {
    console.error('[carregarItens] error', err);
    if (emptyEl) {
      if (isNetworkError(err)) emptyEl.textContent = 'Erro de rede ao carregar itens.';
      else if (err.status) emptyEl.textContent = `Erro ${err.status} ao carregar itens.`;
      else emptyEl.textContent = 'Erro ao carregar itens.';
    }
    showToast('Erro ao carregar itens. Veja console.', 'error');
  }
}

/* ---------------- Movimentações ---------------- */
async function carregarMovimentacoes() {
  const emptyAt = qs('#movEmpty');
  const emptyDev = qs('#movDevolucoesEmpty');
  if (emptyAt) emptyAt.textContent = 'Carregando movimentações ativas...';
  if (emptyDev) emptyDev.textContent = 'Carregando devoluções realizadas...';

  try {
    let movsAtivas = [];
    try {
      movsAtivas = await apiGet('movimentacao/ativas');
    } catch (err) {
      if (isNetworkError(err)) {
        try { movsAtivas = await apiGet('movimentacoes'); } catch { movsAtivas = []; }
        movsAtivas = Array.isArray(movsAtivas) ? movsAtivas.filter(m => !(m.dataDevolucao || m.data_devolucao || m.dataDevolucao !== undefined)) : [];
      } else {
        throw err;
      }
    }

    const tbodyAt = qs('#movList');
    if (tbodyAt) tbodyAt.innerHTML = '';
    if (!Array.isArray(movsAtivas) || movsAtivas.length === 0) {
      if (emptyAt) emptyAt.textContent = 'Nenhuma movimentação ativa encontrada.';
    } else {
      if (emptyAt) emptyAt.textContent = '';
      movsAtivas.forEach(m => {
        const itemName = m.itemNome ?? (m.item ? m.item.nome : (m.itemId ?? '-'));
        const solicitante = m.funcionarioSolicitante ?? m.solicitante ?? '-';
        const tipo = m.tipo ?? '-';
        const quantidade = m.quantidade ?? '-';
        const dataPrev = m.dataPrevistaDevolucao ?? m.data_prevista_devolucao ?? null;
        const dataReg = m.dataRegistro ?? m.data ?? m.dataRetirada ?? null;

        const tr = document.createElement('tr');
        const isOverdue = dataPrev && (new Date(dataPrev) < new Date()) && !(m.dataDevolucao || m.data_devolucao);
        if (isOverdue) tr.classList.add('overdue');

        tr.innerHTML = `
          <td>${escapeHtml(itemName)}</td>
          <td>${escapeHtml(solicitante)}</td>
          <td>${escapeHtml(tipo)}</td>
          <td>${escapeHtml(String(quantidade))}</td>
          <td>${dataPrev ? formatDateTimeIsoToBr(dataPrev) : '-'}</td>
          <td>${dataReg ? formatDateTimeIsoToBr(dataReg) : '-'}</td>
        `;

        const actionsTd = document.createElement('td');
        actionsTd.className = 'cell-actions';
        const devolverBtn = document.createElement('button');
        devolverBtn.className = 'btn small secondary';
        devolverBtn.textContent = 'Registrar Devolução';
        devolverBtn.addEventListener('click', () => {
          const itemId = m.itemId ?? (m.item ? m.item.id : null);
          if (!itemId) { showToast('Item inválido para devolução.', 'error'); return; }
          currentDevItem = itemId;
          qs('#devItemName').textContent = itemName;
          qs('#devQuantidade').value = m.quantidade ?? 1;
          qs('#devError').textContent = '';
          openModal(qs('#devolucaoModal'));
        });
        actionsTd.appendChild(devolverBtn);
        tr.appendChild(actionsTd);

        tbodyAt.appendChild(tr);
      });
    }

    // Devoluções realizadas (histórico)
    let allMovs = [];
    try { allMovs = await apiGet('movimentacoes'); } catch (err2) {
      if (isNetworkError(err2)) {
        try { allMovs = await apiGet('movimentacao/movimentacoes'); } catch { allMovs = []; }
      } else {
        allMovs = [];
      }
    }

    const devols = Array.isArray(allMovs) ? allMovs.filter(m => (m.dataDevolucao || m.data_devolucao || m.dataDevolucao !== undefined)) : [];

    const tbodyDev = qs('#movDevolucoesList');
    if (tbodyDev) tbodyDev.innerHTML = '';
    if (!devols || devols.length === 0) {
      if (emptyDev) emptyDev.textContent = 'Nenhuma devolução registrada.';
    } else {
      if (emptyDev) emptyDev.textContent = '';
      devols.forEach(m => {
        const itemName = m.itemNome ?? (m.item ? m.item.nome : (m.itemId ?? '-'));
        const solicitante = m.funcionarioSolicitante ?? m.solicitante ?? '-';
        const tipo = m.tipo ?? '-';
        const quantidade = m.quantidade ?? '-';
        const dataDev = m.dataDevolucao ?? m.data_devolucao ?? null;
        const dataReg = m.dataRegistro ?? m.data ?? null;
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td>${escapeHtml(itemName)}</td>
          <td>${escapeHtml(solicitante)}</td>
          <td>${escapeHtml(tipo)}</td>
          <td>${escapeHtml(String(quantidade))}</td>
          <td>${dataDev ? formatDateTimeIsoToBr(dataDev) : '-'}</td>
          <td>${dataReg ? formatDateTimeIsoToBr(dataReg) : '-'}</td>
        `;
        tbodyDev.appendChild(tr);
      });
    }
  } catch (err) {
    console.error('[carregarMovimentacoes] error', err);
    if (qs('#movEmpty')) qs('#movEmpty').textContent = 'Erro ao carregar movimentações.';
    if (qs('#movDevolucoesEmpty')) qs('#movDevolucoesEmpty').textContent = 'Erro ao carregar devoluções.';
    showToast('Erro ao carregar movimentações. Veja console.', 'error');
  }
}

/* ---------------- Auditoria ---------------- */
async function carregarAuditoria() {
  const emptyEl = qs('#auditEmpty');
  if (emptyEl) emptyEl.textContent = 'Carregando auditoria...';
  try {
    auditLogs = await apiGet('auditoria') || [];
    renderAuditList(auditLogs);
    if (emptyEl) emptyEl.textContent = auditLogs.length ? '' : 'Nenhum registro de auditoria encontrado.';
  } catch (err) {
    console.error('[carregarAuditoria] error', err);
    if (emptyEl) emptyEl.textContent = 'Erro ao carregar auditoria.';
    showToast('Erro ao carregar auditoria. Veja console.', 'error');
  }
}

function renderAuditList(list) {
  const tbody = qs('#auditList');
  if (!tbody) return;
  tbody.innerHTML = '';
  if (!Array.isArray(list) || list.length === 0) return;
  list.forEach(log => {
    const usuario = log.usuarioResponsavel ?? log.usuario ?? '-';
    const acao = log.acao ?? '-';
    const item = log.itemIdAfetado ?? log.item ?? '-';
    let detalhesRaw = log.detalhes ?? '-';
    detalhesRaw = String(detalhesRaw).replace(/,?\s*estoque removido.*$/i, '').trim();
    const detalhes = formatDescription(detalhesRaw, 160);
    const data = log.dataRegistro ?? log.data ?? null;
    const dataFmt = data ? formatDateTimeIsoToBr(data) : '-';
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${escapeHtml(usuario)}</td>
      <td>${escapeHtml(acao)}</td>
      <td>${escapeHtml(String(item))}</td>
      <td>${detalhes}</td>
      <td>${dataFmt}</td>
    `;
    tbody.appendChild(tr);
  });
}

/* ---------------- Modals and forms ---------------- */
function openModal(el) { if (!el) return; el.setAttribute('aria-hidden', 'false'); el.classList.add('open'); }
function closeModal(el) { if (!el) return; el.setAttribute('aria-hidden', 'true'); el.classList.remove('open'); }

function abrirRetidaModal(itemId, nome) {
  currentRetItem = itemId;
  qs('#retItemName').textContent = nome || '-';
  qs('#retQuantidade').value = 1;
  qs('#retFuncionario').value = '';
  qs('#retDataPrev').value = '';
  qs('#retError').textContent = '';
  openModal(qs('#retiradaModal'));
}

function abrirDevolucaoModal(itemId, nome) {
  currentDevItem = itemId;
  qs('#devItemName').textContent = nome || '-';
  qs('#devQuantidade').value = 1;
  qs('#devError').textContent = '';
  openModal(qs('#devolucaoModal'));
}

function openDeleteConfirm(id, nome) {
  deleteTargetId = id;
  qs('#deleteConfirmText').textContent = `Deseja excluir o item "${nome}"? Esta ação removerá o item e o estoque, mas preservará o histórico e auditoria.`;
  openModal(qs('#deleteConfirmModal'));
}

/* Novo item form */
function setupNovoItemForm() {
  qs('#novoItemBtn')?.addEventListener('click', () => {
    qs('#novoNome').value = '';
    qs('#novoPatrimonio').value = '';
    qs('#novoDescricao').value = '';
    qs('#novoQuantidadeTotal').value = 1;
    qs('#novoItemError').textContent = '';
    openModal(qs('#novoItemModal'));
  });

  qs('#novoItemForm')?.addEventListener('submit', async e => {
    e.preventDefault();
    const errEl = qs('#novoItemError'); errEl.textContent = '';
    const nome = qs('#novoNome').value.trim();
    const patrimonio = qs('#novoPatrimonio').value.trim();
    const descricao = qs('#novoDescricao').value.trim();
    const quantidadeTotal = Number(qs('#novoQuantidadeTotal').value);

    if (!nome) { errEl.textContent = 'Nome é obrigatório.'; return; }
    if (!quantidadeTotal || quantidadeTotal <= 0) { errEl.textContent = 'Quantidade total deve ser maior que zero.'; return; }

    try {
      const payload = { nome, patrimonio, descricao, quantidadeTotal };
      await apiPost('itens', payload);
      closeModal(qs('#novoItemModal'));
      showToast('Item criado com sucesso.', 'success');
      carregarItens();
      carregarMovimentacoes();
      if (localStorage.getItem(ROLE_KEY) === 'admin') carregarAuditoria();
    } catch (err) {
      console.error('[NovoItem] error', err);
      errEl.textContent = err.status === 400 ? (err.body || 'Erro ao criar item.') : 'Erro ao criar item. Veja console.';
    }
  });
}

/* Delete confirm */
function setupDeleteConfirm() {
  qs('#confirmDeleteBtn')?.addEventListener('click', async () => {
    if (!deleteTargetId) return;
    try {
      await apiDelete(`itens/${encode(deleteTargetId)}`);
      closeModal(qs('#deleteConfirmModal'));
      showToast('Item excluído.', 'success');
      deleteTargetId = null;
      carregarItens();
      carregarMovimentacoes();
      if (localStorage.getItem(ROLE_KEY) === 'admin') carregarAuditoria();
    } catch (err) {
      console.error('[DeleteItem] error', err);
      showToast(err.status ? (err.body || `Erro ${err.status}`) : 'Erro ao excluir item. Veja console.', 'error');
    }
  });
}

/* Retirada form */
function setupRetiradaForm() {
  qs('#retiradaForm')?.addEventListener('submit', async e => {
    e.preventDefault();
    const errEl = qs('#retError'); errEl.textContent = '';
    const qty = Number(qs('#retQuantidade').value);
    const funcionario = qs('#retFuncionario').value.trim();
    const tipo = qs('#retTipo')?.value || 'RETIRADA';
    const dataPrev = qs('#retDataPrev').value || null;

    if (!currentRetItem) { errEl.textContent = 'Item inválido.'; return; }
    if (!qty || qty <= 0) { errEl.textContent = 'Informe quantidade válida.'; return; }
    if (!funcionario) { errEl.textContent = 'Informe o funcionário.'; return; }
    if (tipo.toUpperCase() === 'RETIRADA' && (!dataPrev || dataPrev === '')) { errEl.textContent = 'Data prevista obrigatória.'; return; }

    const payload = { quantidade: qty, funcionarioSolicitante: funcionario, tipo, dataPrevistaDevolucao: dataPrev };

    try {
      await apiPost(`movimentacao/retirar/${encode(currentRetItem)}`, payload);
      closeModal(qs('#retiradaModal'));
      showToast('Retirada registrada.', 'success');
      carregarItens();
      carregarMovimentacoes();
      if (localStorage.getItem(ROLE_KEY) === 'admin') carregarAuditoria();
    } catch (err) {
      console.error('[Retirada] error', err);
      if (err.status === 400) qs('#retError').textContent = err.body || 'Erro ao registrar retirada.';
      else qs('#retError').textContent = 'Erro ao registrar retirada. Veja console.';
    }
  });
}

/* Devolução form */
function setupDevolucaoForm() {
  qs('#devolucaoForm')?.addEventListener('submit', async e => {
    e.preventDefault();
    const errEl = qs('#devError'); errEl.textContent = '';
    const qty = Number(qs('#devQuantidade').value);

    if (!currentDevItem) { errEl.textContent = 'Item inválido.'; return; }
    if (!qty || qty <= 0) { errEl.textContent = 'Informe quantidade válida.'; return; }

    try {
      await apiPost(`movimentacao/devolver/${encode(currentDevItem)}`, { quantidadeDevolvida: qty });
      closeModal(qs('#devolucaoModal'));
      showToast('Devolução registrada.', 'success');
      carregarItens();
      carregarMovimentacoes();
      if (localStorage.getItem(ROLE_KEY) === 'admin') carregarAuditoria();
    } catch (err) {
      console.error('[Devolucao] error', err);
      if (err.status === 400) qs('#devError').textContent = err.body || 'Erro ao registrar devolução.';
      else qs('#devError').textContent = 'Erro ao registrar devolução. Veja console.';
    }
  });
}

/* ---------------- Search handlers and UI wiring ---------------- */
function setupSearchHandlers() {
  qs('#searchItem')?.addEventListener('input', debounce(e => carregarItens(e.target.value.trim()), 250));

  const auditInput = qs('#searchAudit');
  if (auditInput) {
    auditInput.addEventListener('input', debounce(e => {
      const q = (e.target.value || '').toLowerCase();
      if (!q) renderAuditList(auditLogs);
      else {
        const filtered = (auditLogs || []).filter(l => {
          const usuario = (l.usuarioResponsavel ?? l.usuario ?? '').toString().toLowerCase();
          const acao = (l.acao ?? '').toString().toLowerCase();
          const item = (l.itemIdAfetado ?? l.item ?? '').toString().toLowerCase();
          const detalhes = (l.detalhes ?? '').toString().toLowerCase();
          return usuario.includes(q) || acao.includes(q) || item.includes(q) || detalhes.includes(q);
        });
        renderAuditList(filtered);
      }
    }, 200));
  }
}

function setupCloseButtons() {
  qsa('[data-action="close"]').forEach(btn => {
    btn.addEventListener('click', () => {
      closeModal(qs('#retiradaModal'));
      closeModal(qs('#devolucaoModal'));
      closeModal(qs('#novoItemModal'));
      closeModal(qs('#deleteConfirmModal'));
    });
  });
}

/* ---------------- Login transition ---------------- */
function initLogin() {
  const form = qs('#loginForm');
  if (!form) return;
  const errEl = qs('#loginError');
  const loginContainer = qs('.login-container') || document.body;

  form.addEventListener('submit', e => {
    e.preventDefault();
    errEl.textContent = '';

    const user = qs('#username').value.trim();
    const pass = qs('#password').value.trim();

    if (user === 'admin' && pass === '@gestaoAuditoriaP') {
      localStorage.setItem(ROLE_KEY, 'admin');
      triggerLoginTransition(loginContainer, 'dashboard.html');
      return;
    }
    if (user === 'portaria' && pass === 'gestaoEstoque') {
      localStorage.setItem(ROLE_KEY, 'portaria');
      triggerLoginTransition(loginContainer, 'dashboard.html');
      return;
    }

    errEl.textContent = 'Usuário ou senha incorretos.';
  });
}

function triggerLoginTransition(containerEl, targetUrl) {
  if (!containerEl) { window.location.href = targetUrl; return; }
  containerEl.classList.add('page-exit');
  document.documentElement.classList.add('body-animating');
  let done = false;

  function finalize() {
    if (done) return;
    done = true;
    window.location.href = targetUrl;
  }

  containerEl.addEventListener('animationend', () => finalize(), { once: true });

  const fallbackMs = 900;
  setTimeout(() => finalize(), fallbackMs);
}

/* ---------------- Initialization ---------------- */
function requireAuth() {
  const role = localStorage.getItem(ROLE_KEY);
  if (!role) {
    window.location.href = 'index.html';
    return null;
  }
  if (role !== 'admin') qsa('.admin-only').forEach(el => (el.style.display = 'none'));
  return role;
}

function initDashboard() {
  const role = requireAuth();
  if (!role) return;

  qsa('.nav-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      qsa('.nav-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      qsa('.section').forEach(s => s.classList.remove('active'));
      const sec = qs('#' + btn.dataset.section);
      if (sec) sec.classList.add('active');
    });
  });

  qs('#logoutBtn')?.addEventListener('click', () => {
    localStorage.removeItem(ROLE_KEY);
    window.location.href = 'index.html';
  });

  setupCloseButtons();
  setupNovoItemForm();
  setupDeleteConfirm();
  setupRetiradaForm();
  setupDevolucaoForm();
  setupSearchHandlers();

  carregarItens();
  carregarMovimentacoes();
  if (role === 'admin') carregarAuditoria();
}

/* Auto-run with dashboard entry animation */
document.addEventListener('DOMContentLoaded', () => {
  const dash = qs('.dashboard-body');
  if (dash) {
    dash.classList.add('page-enter');
    setTimeout(() => dash.classList.remove('page-enter'), 900);
  }
  if (qs('#loginForm')) initLogin();
  if (qs('.dashboard-body')) initDashboard();
});

/* ---------------- Utility: debounce ---------------- */
function debounce(fn, ms = 250) {
  let t;
  return (...args) => {
    clearTimeout(t);
    t = setTimeout(() => fn(...args), ms);
  };
}
