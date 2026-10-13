package com.fiscalhub.backend.service;

import com.fiscalhub.backend.entity.enums.StatusJob;
import com.fiscalhub.backend.repository.JobProcessamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Despacha jobs para o pool de tarefas do Spring Boot (applicationTaskExecutor, o mesmo do @Async,
 * configurado em spring.task.execution.pool.*): após o commit da importação e, na subida, os pendentes.
 */
@Component
public class DespachoJobs {

    private static final Logger log = LoggerFactory.getLogger(DespachoJobs.class);

    private final ProcessamentoDocumentoService processamento;
    private final JobProcessamentoRepository jobRepository;
    private final TaskExecutor executor;

    public DespachoJobs(ProcessamentoDocumentoService processamento, JobProcessamentoRepository jobRepository,
                        @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.processamento = processamento;
        this.jobRepository = jobRepository;
        this.executor = executor;
    }

    @TransactionalEventListener
    public void aoCriarJob(ProcessamentoDocumentoService.JobCriado evento) {
        executor.execute(() -> processamento.executar(evento.jobId()));
    }

    /** Jobs presos em PROCESSANDO (queda da aplicação) voltam para PENDENTE e são reprocessados. */
    @EventListener(ApplicationReadyEvent.class)
    public void recuperarNaSubida() {
        int presos = jobRepository.devolverPresosParaFila();
        if (presos > 0) {
            log.info("{} job(s) presos em PROCESSANDO voltaram para PENDENTE", presos);
        }
        jobRepository.findByStatus(StatusJob.PENDENTE)
                .forEach(job -> executor.execute(() -> processamento.executar(job.getId())));
    }
}
