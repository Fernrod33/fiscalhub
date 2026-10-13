package com.fiscalhub.backend.service.calculo;

import com.fiscalhub.backend.dto.SimulacaoResultDTO;
import com.fiscalhub.backend.entity.enums.ModalidadeParcelamento;

/**
 * Estratégia de cálculo de parcelamento fiscal.
 * Cada implementação encapsula as regras de um órgão/modalidade específica.
 */
public interface CalculoParcelamentoStrategy {

    /**
     * Indica se esta estratégia suporta a modalidade informada.
     */
    boolean suporta(ModalidadeParcelamento modalidade);

    /**
     * Executa o cálculo completo da simulação de parcelamento.
     *
     * @param entrada débitos consolidados do cliente e parâmetros da simulação
     * @return resultado detalhado com memória de cálculo e tabela de parcelas
     */
    SimulacaoResultDTO calcular(EntradaCalculo entrada);
}
