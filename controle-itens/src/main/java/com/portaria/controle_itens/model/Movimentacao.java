package com.portaria.controle_itens.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "movimentacao")
public class Movimentacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = true)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(name = "item_nome", length = 512)
    private String itemNome;

    private Integer quantidade;

    private String tipo;

    @Column(name = "funcionario_solicitante", length = 255)
    private String funcionarioSolicitante;

    @Column(name = "data_retirada")
    private LocalDateTime dataRetirada;

    @Column(name = "data_prevista_devolucao")
    private LocalDate dataPrevistaDevolucao;

    @Column(name = "data_devolucao")
    private LocalDateTime dataDevolucao;

    @Column(name = "status_prazo", length = 50)
    private String statusPrazo;

    @Column(name = "data_registro")
    private LocalDateTime dataRegistro;

    public Movimentacao() {}

    @PrePersist
    public void prePersist() {
        if (this.dataRegistro == null) {
            this.dataRegistro = LocalDateTime.now();
        }
        if (this.item != null && (this.itemNome == null || this.itemNome.isBlank())) {
            this.itemNome = this.item.getNome();
        }
    }

    public Long getId() {
        return id;
    }

    public Item getItem() {
        return item;
    }

    public String getItemNome() {
        return itemNome;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public String getTipo() {
        return tipo;
    }

    public String getFuncionarioSolicitante() {
        return funcionarioSolicitante;
    }

    public LocalDateTime getDataRetirada() {
        return dataRetirada;
    }

    public LocalDate getDataPrevistaDevolucao() {
        return dataPrevistaDevolucao;
    }

    public LocalDateTime getDataDevolucao() {
        return dataDevolucao;
    }

    public String getStatusPrazo() {
        return statusPrazo;
    }

    public LocalDateTime getDataRegistro() {
        return dataRegistro;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public void setItemNome(String itemNome) {
        this.itemNome = itemNome;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public void setFuncionarioSolicitante(String funcionarioSolicitante) {
        this.funcionarioSolicitante = funcionarioSolicitante;
    }

    public void setDataRetirada(LocalDateTime dataRetirada) {
        this.dataRetirada = dataRetirada;
    }

    public void setDataPrevistaDevolucao(LocalDate dataPrevistaDevolucao) {
        this.dataPrevistaDevolucao = dataPrevistaDevolucao;
    }

    public void setDataDevolucao(LocalDateTime dataDevolucao) {
        this.dataDevolucao = dataDevolucao;
    }

    public void setStatusPrazo(String statusPrazo) {
        this.statusPrazo = statusPrazo;
    }

    public void setDataRegistro(LocalDateTime dataRegistro) {
        this.dataRegistro = dataRegistro;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Movimentacao)) return false;
        Movimentacao that = (Movimentacao) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
