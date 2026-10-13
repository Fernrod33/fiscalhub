package com.fiscalhub.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

/** Dispara a importação da SELIC na subida e diariamente. Falha de rede não derruba a aplicação. */
@Component
@ConditionalOnProperty(name = "fiscalhub.selic.agendamento", havingValue = "true", matchIfMissing = true)
public class AgendamentoSelic {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoSelic.class);

    private final ImportacaoSelicService importacao;

    public AgendamentoSelic(ImportacaoSelicService importacao) {
        this.importacao = importacao;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "${fiscalhub.selic.cron:0 0 6 * * *}")
    public void importar() {
        try {
            int gravados = importacao.importarAte(YearMonth.now().minusMonths(1));
            log.info("SELIC (BCB/SGS 4390): {} mês(es) gravado(s) ou corrigido(s)", gravados);
        } catch (RuntimeException e) {
            log.warn("Importação da SELIC falhou; nova tentativa no próximo agendamento: {}", e.getMessage());
        }
    }
}
