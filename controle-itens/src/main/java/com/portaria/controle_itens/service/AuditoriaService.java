package com.portaria.controle_itens.service;

import com.portaria.controle_itens.model.AuditoriaLog;
import com.portaria.controle_itens.repository.AuditoriaLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditoriaService {

    @Autowired
    private AuditoriaLogRepository auditoriaLogRepository;

    public void registrarLog(String acao, Long itemId, String detalhes) {
        AuditoriaLog log = new AuditoriaLog();
        log.setAcao(acao);
        log.setItemIdAfetado(itemId);
        log.setUsuarioResponsavel("Portaria Admin"); 
        log.setDataRegistro(LocalDateTime.now());
        log.setDetalhes(detalhes);

        auditoriaLogRepository.save(log);
    }
}