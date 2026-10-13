package com.fiscalhub.backend.entity;

import com.fiscalhub.backend.entity.enums.StatusDeclaracao;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "declaracao")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Declaracao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tributo", length = 20)
    private TipoTributo tributo;

    @NotNull
    @Column(nullable = false)
    private LocalDate periodoInicio;

    @NotNull
    @Column(nullable = false)
    private LocalDate periodoFim;

    @NotNull
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal;

    @Column(precision = 15, scale = 2)
    private BigDecimal valorImposto;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusDeclaracao status = StatusDeclaracao.EM_ABERTO;

    @Column(name = "data_vencimento")
    private LocalDate dataVencimento;

    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    @Size(max = 500)
    @Column(length = 500)
    private String observacoes;

    @CreationTimestamp
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @UpdateTimestamp
    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @JsonIgnore
    @OneToMany(mappedBy = "declaracao", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<SimulacaoParcelamento> simulacoes = new HashSet<>();
}
