package com.portaria.controle_itens.repository;

import com.portaria.controle_itens.model.AuditoriaLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuditoriaLogRepository extends JpaRepository<AuditoriaLog, Long> {

    List<AuditoriaLog> findAllByOrderByDataRegistroDesc();
    
    List<AuditoriaLog> findByItemIdAfetadoOrderByDataRegistroDesc(Long itemIdAfetado);
}