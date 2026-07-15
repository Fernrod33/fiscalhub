package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.enums.ModalidadeParcelamento;
import com.fiscalhub.backend.entity.enums.OrgaoReceita;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO de resposta completo com todos os resultados do cálculo da simulação.
 */
@Data
public class SimulacaoResultDTO {

    /** ID da simulação persistida no banco (null quando apenas simulando sem salvar) */
    private Long simulacaoId;

    // --- Identificação ---
    private Long declaracaoId;
    private OrgaoReceita orgao;
    private ModalidadeParcelamento modalidade;
    private TipoTributo tributo;
    private String empresaRazaoSocial;
    private String empresaCnpj;

    // --- Débito base ---
    private BigDecimal valorOriginal;
    private LocalDate dataVencimentoOriginal;
    private LocalDate dataCalculo;
    private Integer diasEmAtraso;

    // --- Penalidades ---
    private BigDecimal percentualMultaMora;
    private BigDecimal valorMultaMora;
    private BigDecimal percentualMultaOficio;
    private BigDecimal valorMultaOficio;
    private BigDecimal percentualJurosMora;
    private BigDecimal valorJurosMora;
    private String indiceUtilizado;

    // --- Reduções aplicadas (modalidades especiais) ---
    private BigDecimal valorReducaoMulta;
    private BigDecimal valorReducaoJuros;

    // --- Totais ---
    private BigDecimal valorAtualizado;
    private BigDecimal valorEntrada;
    private Integer numeroParcelas;
    private BigDecimal valorParcela;
    private BigDecimal custoTotal;

    /** Economia = soma das reduções de multa + juros obtidas */
    private BigDecimal economiaObtida;

    // --- Aviso sobre valor mínimo de parcela ---
    private String avisoParcelaMinima;

    // --- Detalhamento ---
    private List<EtapaCalculoDTO> memoriaCalculo;
    private List<ParcelaDTO> tabelaParcelas;
}
