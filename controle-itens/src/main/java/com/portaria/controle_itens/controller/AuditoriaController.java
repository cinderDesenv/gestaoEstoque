package com.portaria.controle_itens.controller;

import com.portaria.controle_itens.model.AuditoriaLog;
import com.portaria.controle_itens.repository.AuditoriaLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/auditoria")
@CrossOrigin(origins = "*")
public class AuditoriaController {

    @Autowired
    private AuditoriaLogRepository auditoriaLogRepository;

    @GetMapping
    public List<AuditoriaLog> listarLogs() {
        return auditoriaLogRepository.findAllByOrderByDataRegistroDesc();
    }
}