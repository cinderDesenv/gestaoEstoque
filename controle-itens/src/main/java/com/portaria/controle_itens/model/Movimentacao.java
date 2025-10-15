package com.portaria.controle_itens.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "movimentacao")
public class Movimentacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private int quantidade; 

    @Column(nullable = false)
    private String tipo; 

    @Column(nullable = false)
    private String funcionarioSolicitante; 
    
    @Column(nullable = false)
    private LocalDateTime dataRetirada;

    private LocalDate dataPrevistaDevolucao; 
    
    private LocalDateTime dataDevolucao;

    @Column(nullable = false)
    private String statusPrazo; 
    
}