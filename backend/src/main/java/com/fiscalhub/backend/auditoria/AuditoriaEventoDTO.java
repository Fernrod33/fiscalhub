package com.fiscalhub.backend.auditoria;

import java.time.LocalDateTime;
import java.util.Map;

public record AuditoriaEventoDTO(Long id, Long usuarioId, AcaoAuditoria acao, String recursoTipo, Long recursoId,
                                 LocalDateTime dataHora, String ip, Map<String, Object> detalhes) {

    public static AuditoriaEventoDTO fromEntity(AuditoriaEvento e) {
        return new AuditoriaEventoDTO(e.getId(), e.getUsuarioId(), e.getAcao(), e.getRecursoTipo(), e.getRecursoId(),
                e.getDataHora(), e.getIp(), e.getDetalhes());
    }
}
