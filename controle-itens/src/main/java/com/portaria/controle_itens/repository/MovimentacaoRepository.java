package com.portaria.controle_itens.repository;

import com.portaria.controle_itens.model.Movimentacao;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

    Optional<Movimentacao> findTopByItemIdAndDataDevolucaoIsNullOrderByDataRetiradaDesc(Long itemId);
    
    List<Movimentacao> findByDataDevolucaoIsNullAndTipoEqualsAndStatusPrazoEquals(String tipo, String statusPrazo);

    List<Movimentacao> findByItemIdAndDataDevolucaoIsNullOrderByDataRetiradaAsc(Long itemId); 

    @Transactional
    void deleteByItemId(Long itemId);
}