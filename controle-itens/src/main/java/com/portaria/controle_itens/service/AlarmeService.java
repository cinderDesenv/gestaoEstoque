package com.portaria.controle_itens.service;

import com.portaria.controle_itens.model.Movimentacao;
import com.portaria.controle_itens.repository.MovimentacaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlarmeService {

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;

    @Scheduled(cron = "*/10 * * * * *") 
    public void verificarAtrasos() {
        System.out.println("--- Executando Verificação de Atrasos: " + LocalDateTime.now() + " ---");
        
        List<Movimentacao> movimentacoesPendentes = movimentacaoRepository
            .findByDataDevolucaoIsNullAndTipoEqualsAndStatusPrazoEquals("RETIRADA", "PENDENTE");

        LocalDate hoje = LocalDate.now();
        int itensAtrasados = 0;

        for (Movimentacao mov : movimentacoesPendentes) {
            LocalDate dataLimite = mov.getDataPrevistaDevolucao();

            if (dataLimite != null && hoje.isAfter(dataLimite)) {
                
                mov.setStatusPrazo("ATRASADO");
                movimentacaoRepository.save(mov);
                itensAtrasados++;
                
                System.out.println("🚨 ALARME: Item ATRASADO! ID: " + mov.getItem().getId() 
                    + ", Nome: " + mov.getItem().getNome() 
                    + ", Limite: " + dataLimite);
            }
        }
        
        System.out.println("--- Verificação Concluída. Total de itens atrasados detectados: " + itensAtrasados + " ---");
    }
}