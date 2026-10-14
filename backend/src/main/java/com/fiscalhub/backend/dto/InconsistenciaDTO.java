package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.importacao.Inconsistencia;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;

public record InconsistenciaDTO(Long id, Severidade severidade, String registro, Long linha, String regra, String mensagem) {

    public static InconsistenciaDTO de(Inconsistencia i) {
        return new InconsistenciaDTO(i.getId(), i.getSeveridade(), i.getRegistro(), i.getLinha(), i.getRegra(), i.getMensagem());
    }
}
