package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.enums.ModalidadeParcelamento;
import com.fiscalhub.backend.entity.enums.OrgaoReceita;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO de entrada para solicitar o cálculo de uma simulação de parcelamento.
 */
@Data
public class SimulacaoRequestDTO {

    @NotNull(message = "O cliente é obrigatório")
    private Long clienteId;

    @NotEmpty(message = "Informe ao menos um débito")
    private List<Long> debitoIds;

    @NotNull(message = "O órgão da receita é obrigatório")
    private OrgaoReceita orgao;

    @NotNull(message = "A modalidade de parcelamento é obrigatória")
    private ModalidadeParcelamento modalidade;

    @NotNull(message = "O número de parcelas é obrigatório")
    @Min(value = 1, message = "O número de parcelas deve ser ao menos 1")
    @Max(value = 60, message = "O número máximo de parcelas é 60")
    private Integer numeroParcelas;

    /**
     * Data de referência do cálculo. Se nulo, usa a data atual.
     */
    private LocalDate dataCalculo;

    /**
     * Se true, o sistema calcula e inclui uma entrada (quando aplicável à modalidade).
     */
    private Boolean incluirEntrada = false;

    /**
     * Percentual de redução de multa (para modalidades especiais).
     * Valor entre 0 e 100. Default 0.
     */
    @Min(0) @Max(100)
    private Integer percentualReducaoMulta = 0;

    /**
     * Percentual de redução de juros (para modalidades especiais).
     * Valor entre 0 e 100. Default 0.
     */
    @Min(0) @Max(100)
    private Integer percentualReducaoJuros = 0;
}
