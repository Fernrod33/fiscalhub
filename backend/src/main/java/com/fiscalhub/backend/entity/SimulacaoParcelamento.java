package com.fiscalhub.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fiscalhub.backend.entity.enums.ModalidadeParcelamento;
import com.fiscalhub.backend.entity.enums.OrgaoReceita;
import jakarta.persistence.*;
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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "simulacao_parcelamento")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimulacaoParcelamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Identificação da modalidade ---
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrgaoReceita orgao;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ModalidadeParcelamento modalidade;

    // --- Dados do débito base ---
    @NotNull
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valorOriginal;

    @NotNull
    @Column(name = "data_vencimento_original", nullable = false)
    private LocalDate dataVencimentoOriginal;

    @NotNull
    @Column(name = "data_calculo", nullable = false)
    private LocalDate dataCalculo;

    @Column(name = "dias_em_atraso")
    private Integer diasEmAtraso;

    // --- Penalidades calculadas ---
    @Column(name = "percentual_multa_mora", precision = 8, scale = 4)
    private BigDecimal percentualMultaMora;

    @Column(name = "valor_multa_mora", precision = 15, scale = 2)
    private BigDecimal valorMultaMora;

    @Column(name = "percentual_multa_oficio", precision = 8, scale = 4)
    private BigDecimal percentualMultaOficio;

    @Column(name = "valor_multa_oficio", precision = 15, scale = 2)
    private BigDecimal valorMultaOficio;

    @Column(name = "percentual_juros_mora", precision = 8, scale = 4)
    private BigDecimal percentualJurosMora;

    @Column(name = "valor_juros", precision = 15, scale = 2)
    private BigDecimal valorJuros;

    @Column(name = "indice_utilizado", length = 10)
    private String indiceUtilizado;

    // --- Totais ---
    @Column(name = "valor_atualizado", precision = 15, scale = 2)
    private BigDecimal valorAtualizado;

    @Column(name = "valor_entrada", precision = 15, scale = 2)
    private BigDecimal valorEntrada;

    @Column(name = "numero_parcelas")
    private Integer numeroParcelas;

    @Column(name = "valor_parcela", precision = 15, scale = 2)
    private BigDecimal valorParcela;

    /** Custo total = soma de todas as parcelas + entrada */
    @Column(name = "custo_total", precision = 15, scale = 2)
    private BigDecimal custoTotal;

    /** Economia obtida em modalidades com redução de multa/juros */
    @Column(name = "economia_obtida", precision = 15, scale = 2)
    private BigDecimal economiaObtida;

    @Column(nullable = false)
    private Boolean aprovada = false;

    @Size(max = 500)
    @Column(length = 500)
    private String observacoes;

    // --- Memória de cálculo (JSON) ---
    @Column(name = "memoria_calculo_json", columnDefinition = "TEXT")
    private String memoriaCalculoJson;

    @CreationTimestamp
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @UpdateTimestamp
    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /** Débitos consolidados nesta simulação. */
    @JsonIgnore
    @ManyToMany
    @JoinTable(name = "simulacao_debito",
            joinColumns = @JoinColumn(name = "simulacao_id"),
            inverseJoinColumns = @JoinColumn(name = "debito_id"))
    private List<Debito> debitos = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "simulacao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ParcelaSimulacao> parcelas = new ArrayList<>();
}
