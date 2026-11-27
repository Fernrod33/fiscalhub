package com.fiscalhub.backend.auditoria;

import java.util.Map;

/** Exceção que diz à auditoria o motivo da falha (ex.: bloqueio por tentativas). "usuarioId" identifica quem falhou. */
public interface DetalheAuditoria {

    Map<String, Object> detalhesAuditoria();
}
