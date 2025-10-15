package com.portaria.controle_itens.controller;

import com.portaria.controle_itens.model.Estoque;
import com.portaria.controle_itens.model.Item;
import com.portaria.controle_itens.model.Movimentacao;
import com.portaria.controle_itens.repository.EstoqueRepository;
import com.portaria.controle_itens.repository.ItemRepository;
import com.portaria.controle_itens.repository.MovimentacaoRepository;
import com.portaria.controle_itens.service.AuditoriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/movimentacao")
@CrossOrigin(origins = "*")
public class MovimentacaoController {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private EstoqueRepository estoqueRepository;

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;
    
    @Autowired
    private AuditoriaService auditoriaService;

    @PostMapping("/retirar/{itemId}")
    public ResponseEntity<?> registrarRetirada(
            @PathVariable Long itemId, 
            @RequestBody Map<String, Object> requisicao) {

        Optional<Item> itemOpt = itemRepository.findById(itemId);
        if (itemOpt.isEmpty()) {
            return new ResponseEntity<>("Item não encontrado.", HttpStatus.NOT_FOUND);
        }

        Item item = itemOpt.get();
        
        Integer quantidadeRetirada = (Integer) requisicao.get("quantidade");
        if (quantidadeRetirada == null || quantidadeRetirada <= 0) {
            return new ResponseEntity<>("A quantidade a ser retirada é obrigatória e deve ser > 0.", HttpStatus.BAD_REQUEST);
        }

        Optional<Estoque> estoqueOpt = estoqueRepository.findByItemId(itemId);
        if (estoqueOpt.isEmpty()) {
             return new ResponseEntity<>("Dados de estoque não encontrados para este item.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        Estoque estoque = estoqueOpt.get();
        
        if (quantidadeRetirada > estoque.getQuantidadeDisponivel()) {
            return new ResponseEntity<>("Estoque insuficiente. Disponível: " + estoque.getQuantidadeDisponivel(), HttpStatus.BAD_REQUEST);
        }

        String funcionario = (String) requisicao.get("funcionarioSolicitante");
        String tipo = (String) requisicao.getOrDefault("tipo", "RETIRADA"); 

        if (funcionario == null || funcionario.isEmpty()) {
             return new ResponseEntity<>("Nome do funcionário solicitante é obrigatório.", HttpStatus.BAD_REQUEST);
        }
        
        Movimentacao movimentacao = new Movimentacao();
        movimentacao.setItem(item);
        movimentacao.setFuncionarioSolicitante(funcionario);
        movimentacao.setDataRetirada(LocalDateTime.now());
        movimentacao.setTipo(tipo); 
        movimentacao.setQuantidade(quantidadeRetirada);

        if ("RETIRADA".equalsIgnoreCase(tipo)) {
            String dataStr = (String) requisicao.get("dataPrevistaDevolucao");
            if (dataStr == null || dataStr.isEmpty()) {
                return new ResponseEntity<>("Data prevista de devolução é obrigatória para o tipo RETIRADA.", HttpStatus.BAD_REQUEST);
            }
            try {
                movimentacao.setDataPrevistaDevolucao(LocalDate.parse(dataStr));
                movimentacao.setStatusPrazo("PENDENTE");
            } catch (Exception e) {
                 return new ResponseEntity<>("Formato de data inválido. Use AAAA-MM-DD.", HttpStatus.BAD_REQUEST);
            }
            
        } else if ("CEDIDO".equalsIgnoreCase(tipo)) {
            movimentacao.setTipo("CEDIDO");
            movimentacao.setStatusPrazo("PENDENTE");
            
        } else {
            return new ResponseEntity<>("Tipo de movimentação inválido. Use RETIRADA ou CEDIDO.", HttpStatus.BAD_REQUEST);
        }

        estoque.setQuantidadeDisponivel(estoque.getQuantidadeDisponivel() - quantidadeRetirada);
        estoqueRepository.save(estoque);

        movimentacaoRepository.save(movimentacao);

        String dataPrevistaStr = movimentacao.getDataPrevistaDevolucao() != null ? 
                                 movimentacao.getDataPrevistaDevolucao().toString() : 
                                 "Indeterminado";

        auditoriaService.registrarLog("RETIRADA_" + tipo.toUpperCase(), itemId, 
            String.format("Retirada de %d unidades por %s. Prazo: %s", quantidadeRetirada, funcionario, dataPrevistaStr));


        return new ResponseEntity<>(movimentacao, HttpStatus.CREATED);
    }

    @PostMapping("/devolver/{itemId}")
    @Transactional
    public ResponseEntity<?> registrarDevolucao(@PathVariable Long itemId, @RequestBody Map<String, Integer> requisicao) {
        
        Integer quantidadeDevolvida = requisicao.get("quantidadeDevolvida");
        if (quantidadeDevolvida == null || quantidadeDevolvida <= 0) {
            return new ResponseEntity<>("A quantidade a ser devolvida é obrigatória e deve ser maior que zero.", HttpStatus.BAD_REQUEST);
        }

        Optional<Item> itemOpt = itemRepository.findById(itemId);
        Optional<Estoque> estoqueOpt = estoqueRepository.findByItemId(itemId);
        
        if (itemOpt.isEmpty() || estoqueOpt.isEmpty()) {
            return new ResponseEntity<>("Item ou Estoque não encontrado.", HttpStatus.NOT_FOUND);
        }
        
        Estoque estoque = estoqueOpt.get();
        int unidadesFora = estoque.getQuantidadeTotal() - estoque.getQuantidadeDisponivel();
        
        if (quantidadeDevolvida > unidadesFora) {
             return new ResponseEntity<>("Erro: A quantidade devolvida excede as unidades atualmente fora de estoque (" + unidadesFora + ").", HttpStatus.BAD_REQUEST);
        }
        
        // 1. Aumentar o estoque
        estoque.setQuantidadeDisponivel(estoque.getQuantidadeDisponivel() + quantidadeDevolvida);
        estoqueRepository.save(estoque);

        // 2. Fechar as Movimentações ativas (FIFO)
        List<Movimentacao> movimentacoesAtivas = movimentacaoRepository.findByItemIdAndDataDevolucaoIsNullOrderByDataRetiradaAsc(itemId);
        int restanteParaFechar = quantidadeDevolvida;
        
        LocalDateTime agora = LocalDateTime.now();
        int quantidadeEfetivamenteFechada = 0;

        for (Movimentacao mov : movimentacoesAtivas) {
            if (restanteParaFechar <= 0) break;

            int quantidadeAtiva = mov.getQuantidade();
            
            if (restanteParaFechar >= quantidadeAtiva) {
                // Fecha completamente
                mov.setDataDevolucao(agora);
                mov.setStatusPrazo(determinarStatusFinal(mov)); 
                movimentacaoRepository.save(mov);
                restanteParaFechar -= quantidadeAtiva;
                quantidadeEfetivamenteFechada += quantidadeAtiva;
                
            } else {
                // Devolução Parcial: Reduz a quantidade do registro mais antigo
                int quantidadeRemanescente = quantidadeAtiva - restanteParaFechar;
                
                mov.setQuantidade(quantidadeRemanescente);
                movimentacaoRepository.save(mov);
                quantidadeEfetivamenteFechada += restanteParaFechar; 
                restanteParaFechar = 0; 
            }
        }
        
        // REGISTRO DE AUDITORIA: DEVOLUÇÃO
        auditoriaService.registrarLog("DEVOLUCAO_ITEM", itemId, 
            String.format("Devolução de %d unidades. Total fechado no histórico: %d.", quantidadeDevolvida, quantidadeEfetivamenteFechada));
        
        return new ResponseEntity<>("Devolução registrada com sucesso. Estoque atualizado.", HttpStatus.OK);
    }

    private String determinarStatusFinal(Movimentacao mov) {
        if ("RETIRADA".equalsIgnoreCase(mov.getTipo())) {
            LocalDate dataLimite = mov.getDataPrevistaDevolucao();
            LocalDate dataDevolucao = mov.getDataDevolucao().toLocalDate();
            
            if (dataLimite != null && dataDevolucao.isAfter(dataLimite)) {
                return "ATRASADO";
            }
        }
        return "CONCLUIDO";
    }

    @GetMapping("/ativa/{itemId}")
    public ResponseEntity<Movimentacao> getMovimentacaoAtiva(@PathVariable Long itemId) {
        // Busca a movimentação mais recente que ainda está ATIVA
        Optional<Movimentacao> movimentacaoOpt = movimentacaoRepository.findTopByItemIdAndDataDevolucaoIsNullOrderByDataRetiradaDesc(itemId);
        
        return movimentacaoOpt.map(ResponseEntity::ok)
                   .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    @GetMapping("/ativas/{itemId}")
    public ResponseEntity<List<Movimentacao>> getMovimentacoesAtivas(@PathVariable Long itemId) {
        List<Movimentacao> movimentacoes = movimentacaoRepository.findByItemIdAndDataDevolucaoIsNullOrderByDataRetiradaAsc(itemId);
        return ResponseEntity.ok(movimentacoes); 
    }
    
    @GetMapping("/estoque/{itemId}")
    public ResponseEntity<Estoque> getEstoquePorItem(@PathVariable Long itemId) {
        Optional<Estoque> estoqueOpt = estoqueRepository.findByItemId(itemId);
        
        return estoqueOpt.map(ResponseEntity::ok)
                   .orElseGet(() -> ResponseEntity.notFound().build());
    }
}