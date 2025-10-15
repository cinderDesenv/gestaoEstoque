package com.portaria.controle_itens.controller;

import com.portaria.controle_itens.model.Estoque;
import com.portaria.controle_itens.model.Item;
import com.portaria.controle_itens.repository.EstoqueRepository;
import com.portaria.controle_itens.repository.ItemRepository;
import com.portaria.controle_itens.repository.MovimentacaoRepository;
import com.portaria.controle_itens.service.AuditoriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/itens")
@CrossOrigin(origins = "*")
public class ItemController {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private EstoqueRepository estoqueRepository;

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    @PostMapping
    public ResponseEntity<?> criarItemEmVolume(@RequestBody Map<String, Object> requisicao) {
        
        String nome = (String) requisicao.get("nome");
        Integer quantidadeTotal = (Integer) requisicao.get("quantidadeTotal");
        
        if (nome == null || quantidadeTotal == null || quantidadeTotal <= 0) {
            return new ResponseEntity<>("Nome e Quantidade Total (> 0) são obrigatórios.", HttpStatus.BAD_REQUEST);
        }

        Item novoItem = new Item();
        novoItem.setNome(nome);
        novoItem.setPatrimonio((String) requisicao.get("patrimonio"));
        novoItem.setDescricao((String) requisicao.get("descricao"));
        Item itemSalvo = itemRepository.save(novoItem);

        Estoque novoEstoque = new Estoque();
        novoEstoque.setItem(itemSalvo);
        novoEstoque.setQuantidadeTotal(quantidadeTotal);
        novoEstoque.setQuantidadeDisponivel(quantidadeTotal);
        estoqueRepository.save(novoEstoque);

        auditoriaService.registrarLog("CRIACAO_ITEM", itemSalvo.getId(), "Novo item: " + nome + " (Qtd: " + quantidadeTotal + ")");

        return new ResponseEntity<>(itemSalvo, HttpStatus.CREATED);
    }

    @GetMapping
    public List<Item> listarTodos() {
        return itemRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Item> buscarPorId(@PathVariable Long id) {
        Optional<Item> item = itemRepository.findById(id);
        
        return item.map(ResponseEntity::ok)
                   .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Item> atualizarItem(@PathVariable Long id, @RequestBody Item itemDetalhes) {
        return itemRepository.findById(id)
            .map(item -> {
                item.setNome(itemDetalhes.getNome());
                item.setPatrimonio(itemDetalhes.getPatrimonio());
                item.setDescricao(itemDetalhes.getDescricao());

                Item atualizado = itemRepository.save(item);
                return ResponseEntity.ok(atualizado);
            }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deletarItem(@PathVariable Long id) {
        Optional<Item> itemOpt = itemRepository.findById(id);
        if (itemOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        String nomeItem = itemOpt.get().getNome();

        movimentacaoRepository.deleteByItemId(id);
        Optional<Estoque> estoque = estoqueRepository.findByItemId(id);
        estoque.ifPresent(e -> estoqueRepository.delete(e));
        itemRepository.deleteById(id);
        
        auditoriaService.registrarLog("EXCLUSAO_ITEM", id, "Item excluído: " + nomeItem + " (Exclusão total, incluindo histórico).");

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/estoque/{itemId}")
    public ResponseEntity<?> atualizarEstoqueTotal(@PathVariable Long itemId, @RequestBody Map<String, Integer> requisicao) {
        Optional<Estoque> estoqueOpt = estoqueRepository.findByItemId(itemId);

        if (estoqueOpt.isEmpty()) {
            return new ResponseEntity<>("Estoque não encontrado para este item.", HttpStatus.NOT_FOUND);
        }

        Integer novaQuantidade = requisicao.get("quantidadeTotal");
        if (novaQuantidade == null || novaQuantidade < 0) {
            return new ResponseEntity<>("Nova quantidade total inválida.", HttpStatus.BAD_REQUEST);
        }

        Estoque estoque = estoqueOpt.get(); 
        
        int diferenca = novaQuantidade - estoque.getQuantidadeTotal();
        
        estoque.setQuantidadeTotal(novaQuantidade);
        estoque.setQuantidadeDisponivel(estoque.getQuantidadeDisponivel() + diferenca);

        if (estoque.getQuantidadeDisponivel() < 0) {
            estoque.setQuantidadeDisponivel(0);
        }

        estoqueRepository.save(estoque);

        String detalhes = String.format("Ajuste de QTD: De %d para %d (Diferença: %+d)", 
                                        estoque.getQuantidadeTotal() - diferenca, novaQuantidade, diferenca);
        auditoriaService.registrarLog("AJUSTE_ESTOQUE", itemId, detalhes);

        return new ResponseEntity<>(estoque, HttpStatus.OK);
    }
}