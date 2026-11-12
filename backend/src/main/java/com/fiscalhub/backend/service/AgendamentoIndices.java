package com.fiscalhub.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

/** Importa a SELIC (BCB) e a UPF/PR (SEFA-PR) na subida e diariamente. Falha de rede não derruba a aplicação. */
@Component
@ConditionalOnProperty(name = "fiscalhub.indices.agendamento", havingValue = "true", matchIfMissing = true)
public class AgendamentoIndices {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoIndices.class);

    private final ImportacaoSelicService selic;
    private final ImportacaoUpfPrService upf;

    public AgendamentoIndices(ImportacaoSelicService selic, ImportacaoUpfPrService upf) {
        this.selic = selic;
        this.upf = upf;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "${fiscalhub.indices.cron:0 0 6 * * *}")
    public void importar() {
        try {
            log.info("SELIC (BCB/SGS 4390): {} mês(es) gravado(s) ou corrigido(s)", selic.importarAte(YearMonth.now().minusMonths(1)));
        } catch (RuntimeException e) {
            log.warn("Importação da SELIC falhou; nova tentativa no próximo agendamento: {}", e.getMessage());
        }
        try {
            // a UPF/PR do mês é publicada no próprio mês
            log.info("UPF/PR (SEFA-PR): {} mês(es) gravado(s) ou corrigido(s)", upf.importarAte(YearMonth.now()));
        } catch (RuntimeException e) {
            log.warn("Importação da UPF/PR falhou; nova tentativa no próximo agendamento: {}", e.getMessage());
        }
    }
}
