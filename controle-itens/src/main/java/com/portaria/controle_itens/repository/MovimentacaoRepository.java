package com.portaria.controle_itens.repository;

import com.portaria.controle_itens.model.Movimentacao;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

    Optional<Movimentacao> findTopByItem_IdAndDataDevolucaoIsNullOrderByDataRetiradaDesc(Long itemId);
    
    List<Movimentacao> findByDataDevolucaoIsNullAndTipoEqualsAndStatusPrazoEquals(String tipo, String statusPrazo);

    List<Movimentacao> findByItem_IdAndDataDevolucaoIsNullOrderByDataRetiradaAsc(Long itemId); 

    @Transactional
    void deleteByItem_Id(Long itemId);

    List<Movimentacao> findByDataDevolucaoIsNullOrderByDataRetiradaAsc();

    List<Movimentacao> findByItem_Id(Long itemId);
}
