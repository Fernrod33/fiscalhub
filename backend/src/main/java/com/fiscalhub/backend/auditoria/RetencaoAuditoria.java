package com.fiscalhub.backend.auditoria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Retenção da trilha de auditoria (fiscalhub.auditoria.retencao-anos, padrão 5: decadência e prescrição, CTN arts. 173
 * e 174; prazo final a validar com o jurídico). O expurgo passa pela função do banco auditoria_expurgar (o DELETE comum
 * continua bloqueado pelo trigger) e fica registrado na própria trilha.
 */
@Component
public class RetencaoAuditoria {

    private static final Logger log = LoggerFactory.getLogger(RetencaoAuditoria.class);

    private final JdbcTemplate jdbc;
    private final AuditoriaEventoRepository auditoria;
    private final int anos;

    public RetencaoAuditoria(JdbcTemplate jdbc, AuditoriaEventoRepository auditoria,
                             @Value("${fiscalhub.auditoria.retencao-anos:5}") int anos) {
        if (anos < 1) {
            throw new IllegalStateException("fiscalhub.auditoria.retencao-anos deve ser >= 1, veio " + anos);
        }
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.anos = anos;
    }

    /** Todo dia: remove os eventos anteriores a (hoje - prazo), à meia-noite. Devolve quantos saíram. */
    @Scheduled(cron = "${fiscalhub.auditoria.retencao-cron:0 45 3 * * *}")
    @Transactional
    public int expurgar() {
        LocalDateTime limite = LocalDate.now().minusYears(anos).atStartOfDay();
        int n = jdbc.queryForObject("SELECT auditoria_expurgar(?)", Integer.class, limite);
        if (n > 0) {
            AuditoriaEvento e = new AuditoriaEvento();
            e.setAcao(AcaoAuditoria.AUDITORIA_EXPURGAR);
            e.setRecursoTipo(AcaoAuditoria.AUDITORIA_EXPURGAR.recurso());
            e.setDetalhes(Map.of("resultado", "SUCESSO", "registros", n, "anterioresA", limite.toString(), "retencaoAnos", anos));
            auditoria.save(e);
            log.info("Auditoria: {} evento(s) anteriores a {} expurgados (retenção de {} anos)", n, limite, anos);
        }
        return n;
    }
}
