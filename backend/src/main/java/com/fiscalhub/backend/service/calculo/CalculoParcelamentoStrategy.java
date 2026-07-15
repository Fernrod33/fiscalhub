package com.fiscalhub.backend.service.calculo;

import com.fiscalhub.backend.dto.SimulacaoRequestDTO;
import com.fiscalhub.backend.dto.SimulacaoResultDTO;
import com.fiscalhub.backend.entity.Declaracao;
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
     * @param declaracao declaração fiscal base
     * @param request    parâmetros da simulação
     * @return resultado detalhado com memória de cálculo e tabela de parcelas
     */
    SimulacaoResultDTO calcular(Declaracao declaracao, SimulacaoRequestDTO request);
}
