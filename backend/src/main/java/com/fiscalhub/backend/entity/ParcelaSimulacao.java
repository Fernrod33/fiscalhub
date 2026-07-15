package com.fiscalhub.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Representa cada parcela individual de uma simulação de parcelamento.
 */
@Entity
@Table(name = "parcela_simulacao")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParcelaSimulacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "numero_parcela", nullable = false)
    private Integer numeroParcela;

    @NotNull
    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    @NotNull
    @Column(name = "valor_parcela", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorParcela;

    /** Saldo devedor após o pagamento desta parcela */
    @NotNull
    @Column(name = "saldo_restante", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoRestante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulacao_id", nullable = false)
    private SimulacaoParcelamento simulacao;
}
