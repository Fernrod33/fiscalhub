package com.fiscalhub.backend.service;

import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.JobProcessamento;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.StatusJob;
import com.fiscalhub.backend.entity.enums.TipoJob;
import com.fiscalhub.backend.repository.JobProcessamentoRepository;
import com.fiscalhub.backend.storage.ArmazenamentoArquivos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Fila de processamento de documentos: tabela job_processamento + executor @Async do próprio processo.
 * ponytail: sem fila externa; trocar por RabbitMQ/SQS quando houver mais de uma instância do backend.
 */
@Service
public class ProcessamentoDocumentoService {

    private static final Logger log = LoggerFactory.getLogger(ProcessamentoDocumentoService.class);

    /** Evento publicado na transação da importação; o despacho só ocorre depois do commit. */
    public record JobCriado(Long jobId) {
    }

    private final JobProcessamentoRepository jobRepository;
    private final ArmazenamentoArquivos armazenamento;
    private final ApplicationEventPublisher eventos;

    public ProcessamentoDocumentoService(JobProcessamentoRepository jobRepository, ArmazenamentoArquivos armazenamento,
                                         ApplicationEventPublisher eventos) {
        this.jobRepository = jobRepository;
        this.armazenamento = armazenamento;
        this.eventos = eventos;
    }

    @Transactional
    public JobProcessamento enfileirar(DocumentoFiscal documento) {
        JobProcessamento job = new JobProcessamento();
        job.setTipo(TipoJob.VALIDACAO_DOCUMENTO);
        job.setDocumento(documento);
        jobRepository.save(job);
        eventos.publishEvent(new JobCriado(job.getId()));
        return job;
    }

    /** Roda no executor @Async (ver DespachoJobs), em transação própria. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executar(Long jobId) {
        JobProcessamento job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != StatusJob.PENDENTE) {
            return;
        }
        job.setStatus(StatusJob.PROCESSANDO);
        job.setTentativas(job.getTentativas() + 1);
        job.setInicio(LocalDateTime.now());
        DocumentoFiscal documento = job.getDocumento();
        documento.setStatus(DocumentoStatus.PROCESSANDO);
        jobRepository.flush();
        try {
            validar(documento);
            documento.setStatus(DocumentoStatus.PROCESSADO);
            job.setStatus(StatusJob.CONCLUIDO);
        } catch (FalhaDeValidacao e) {
            documento.setStatus(DocumentoStatus.ERRO);
            job.setStatus(StatusJob.ERRO);
            job.setMensagem(e.getMessage());
        } catch (RuntimeException e) {
            // a mensagem vai para o /status do usuário: detalhe interno (storage, SDK) só no log
            log.error("Job {} falhou", jobId, e);
            documento.setStatus(DocumentoStatus.ERRO);
            job.setStatus(StatusJob.ERRO);
            job.setMensagem("Falha inesperada no processamento. Job " + jobId + ".");
        }
        job.setFim(LocalDateTime.now());
    }

    /** Por enquanto só confere que o arquivo está no storage; os parsers entram na Fase 1. */
    private void validar(DocumentoFiscal documento) {
        if (!armazenamento.existe(documento.getChaveArmazenamento())) {
            throw new FalhaDeValidacao("Arquivo não encontrado no armazenamento.");
        }
    }

    /** Problema do arquivo, com mensagem própria para o usuário. */
    static class FalhaDeValidacao extends RuntimeException {
        FalhaDeValidacao(String mensagem) {
            super(mensagem);
        }
    }
}
