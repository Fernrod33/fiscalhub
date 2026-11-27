package com.fiscalhub.backend.auditoria;

import java.time.LocalDateTime;
import java.util.Map;

/** Evento da trilha; usuarioNome vem do cadastro atual (null se o login falhou com e-mail desconhecido). */
public record AuditoriaEventoDTO(Long id, Long usuarioId, String usuarioNome, AcaoAuditoria acao, String recursoTipo,
                                 Long recursoId, LocalDateTime dataHora, String ip, ResultadoAuditoria resultado,
                                 Map<String, Object> detalhes) {

    public enum ResultadoAuditoria { SUCESSO, FALHA }

    public static AuditoriaEventoDTO de(AuditoriaEvento e, String usuarioNome) {
        Object resultado = e.getDetalhes() == null ? null : e.getDetalhes().get("resultado");
        return new AuditoriaEventoDTO(e.getId(), e.getUsuarioId(), usuarioNome, e.getAcao(), e.getRecursoTipo(),
                e.getRecursoId(), e.getDataHora(), e.getIp(),
                resultado == null ? null : ResultadoAuditoria.valueOf(resultado.toString()), e.getDetalhes());
    }
}
