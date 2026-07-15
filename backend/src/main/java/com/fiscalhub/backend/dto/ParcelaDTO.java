package com.fiscalhub.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Representa uma linha da tabela de parcelas na resposta da simulação.
 */
@Data
public class ParcelaDTO {

    private Integer numeroParcela;
    private LocalDate dataVencimento;
    private BigDecimal valorParcela;
    private BigDecimal saldoRestante;

    public ParcelaDTO(Integer numeroParcela, LocalDate dataVencimento,
                      BigDecimal valorParcela, BigDecimal saldoRestante) {
        this.numeroParcela = numeroParcela;
        this.dataVencimento = dataVencimento;
        this.valorParcela = valorParcela;
        this.saldoRestante = saldoRestante;
    }
}
