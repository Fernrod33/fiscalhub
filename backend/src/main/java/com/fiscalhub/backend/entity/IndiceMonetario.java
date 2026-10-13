package com.fiscalhub.backend.entity;

import com.fiscalhub.backend.entity.enums.TipoIndice;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * Armazena as taxas mensais dos índices utilizados nos cálculos fiscais:
 * SELIC (Receita Federal), UFPR (Receita Estadual PR), IPCA.
 * Chave única: tipo + ano + mês.
 */
@Entity
@Table(name = "indice_monetario",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tipo", "ano", "mes"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IndiceMonetario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoIndice tipo;

    @NotNull
    @Column(nullable = false)
    private Integer ano;

    @NotNull
    @Column(nullable = false)
    private Integer mes;

    /**
     * Taxa percentual mensal. Exemplos:
     * - SELIC: 0.9700 representa 0,97% ao mês
     * - UFPR: valor da unidade naquele mês (ex.: 108.45)
     */
    @NotNull
    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal valor;

    /** De onde veio o valor (ex.: BCB_SGS_4390) e quando foi coletado. */
    @Column(length = 30)
    private String origem;

    @Column(name = "data_coleta")
    private LocalDateTime dataColeta;

    public YearMonth getYearMonth() {
        return YearMonth.of(ano, mes);
    }
}
