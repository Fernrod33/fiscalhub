package com.fiscalhub.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Representa uma etapa da memória de cálculo da simulação.
 */
@Data
public class EtapaCalculoDTO {

    private Integer ordem;
    private String descricao;
    private String formula;
    private BigDecimal valor;
    private String unidade; // ex: "%", "R$", "dias"

    public EtapaCalculoDTO(Integer ordem, String descricao, String formula, BigDecimal valor, String unidade) {
        this.ordem = ordem;
        this.descricao = descricao;
        this.formula = formula;
        this.valor = valor;
        this.unidade = unidade;
    }
}
