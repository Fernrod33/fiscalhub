package com.fiscalhub.backend.importacao;

import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.JobProcessamento;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.entity.enums.EtapaProcessamento;
import com.fiscalhub.backend.entity.enums.StatusJob;
import com.fiscalhub.backend.entity.enums.TipoJob;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import com.fiscalhub.backend.repository.ClienteRepository;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.fiscalhub.backend.repository.JobProcessamentoRepository;
import com.fiscalhub.backend.storage.ArmazenamentoArquivos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fila de processamento de documentos (tabela job_processamento + executor do próprio processo) e despacho para o
 * {@link ProcessadorDocumento} do tipo detectado no conteúdo. Cada execução usa transações separadas:
 * <ol>
 *   <li>início: marca o job, detecta o tipo e registra a divergência com o tipo informado;</li>
 *   <li>processamento: tudo o que o processador grava; exceção desfaz o documento inteiro;</li>
 *   <li>conclusão (ou falha): status final, inconsistências e fim do job.</li>
 * </ol>
 * Etapa e progresso vão por {@link RegistroProgresso}, em transação própria, para a tela acompanhar.
 * ponytail: sem fila externa; trocar por RabbitMQ/SQS quando houver mais de uma instância do backend.
 */
@Service
public class ProcessamentoDocumentoService {

    private static final Logger log = LoggerFactory.getLogger(ProcessamentoDocumentoService.class);

    /** Evento publicado na transação da importação; o despacho só ocorre depois do commit. */
    public record JobCriado(Long jobId) {
    }

    private final JobProcessamentoRepository jobRepository;
    private final DocumentoFiscalRepository documentoRepository;
    private final InconsistenciaRepository inconsistenciaRepository;
    private final ClienteRepository clienteRepository;
    private final ArmazenamentoArquivos armazenamento;
    private final RegistroProgresso registroProgresso;
    private final List<ProcessadorDocumento> processadores;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transacao;

    public ProcessamentoDocumentoService(JobProcessamentoRepository jobRepository, DocumentoFiscalRepository documentoRepository,
                                         InconsistenciaRepository inconsistenciaRepository, ClienteRepository clienteRepository,
                                         ArmazenamentoArquivos armazenamento, RegistroProgresso registroProgresso,
                                         List<ProcessadorDocumento> processadores, ApplicationEventPublisher eventos,
                                         PlatformTransactionManager transacoes) {
        this.jobRepository = jobRepository;
        this.documentoRepository = documentoRepository;
        this.inconsistenciaRepository = inconsistenciaRepository;
        this.clienteRepository = clienteRepository;
        this.armazenamento = armazenamento;
        this.registroProgresso = registroProgresso;
        this.processadores = processadores; // na ordem do Spring (@Order): o ProcessadorSemLeitura fica por último
        this.eventos = eventos;
        this.transacao = new TransactionTemplate(transacoes);
    }

    /** Novo job para o documento (importação, reprocessamento ou cliente confirmado). Roda na transação de quem chama. */
    @Transactional
    public JobProcessamento enfileirar(DocumentoFiscal documento) {
        JobProcessamento job = new JobProcessamento();
        job.setTipo(TipoJob.VALIDACAO_DOCUMENTO);
        job.setDocumento(documento);
        jobRepository.save(job);
        documento.setStatus(DocumentoStatus.RECEBIDO);
        eventos.publishEvent(new JobCriado(job.getId()));
        return job;
    }

    /** Reprocessar, excluir ou remapear um documento com job em andamento é recusado. */
    public void exigirParado(Long documentoId) {
        if (jobRepository.existsByDocumentoIdAndStatusIn(documentoId, List.of(StatusJob.PENDENTE, StatusJob.PROCESSANDO))) {
            throw new BusinessRuleException("O documento ainda está em processamento. Aguarde terminar.");
        }
    }

    /** Roda no executor (ver DespachoJobs), fora de transação: cada fase abre a sua. */
    public void executar(Long jobId) {
        Long documentoId;
        try {
            documentoId = transacao.execute(t -> iniciar(jobId));
        } catch (RuntimeException e) {
            // sem isso o job ficaria PENDENTE para sempre e o erro só existiria na thread
            log.error("Job {} falhou ao iniciar", jobId, e);
            transacao.executeWithoutResult(t -> jobRepository.findById(jobId).ifPresent(job -> {
                job.setStatus(StatusJob.ERRO);
                job.setMensagem("Falha inesperada ao iniciar o processamento. Job " + jobId + ".");
                job.setFim(LocalDateTime.now());
                job.getDocumento().setStatus(DocumentoStatus.ERRO);
            }));
            return;
        }
        if (documentoId == null) {
            return;
        }
        ContextoProcessamento contexto = new ContextoProcessamento(jobId, armazenamento, clienteRepository, registroProgresso);
        try {
            transacao.executeWithoutResult(t -> processar(documentoId, contexto));
            DocumentoStatus status = contexto.temAlertaOuErro() ? DocumentoStatus.PROCESSADO_COM_ALERTAS : DocumentoStatus.PROCESSADO;
            transacao.executeWithoutResult(t -> concluir(jobId, documentoId, status, contexto, null));
        } catch (AguardandoUsuario pendencia) {
            transacao.executeWithoutResult(t -> concluir(jobId, documentoId, pendencia.status(), contexto, pendencia));
        } catch (FalhaImportacao falha) {
            contexto.registrar(Severidade.ERRO, falha.registro(), falha.linha(), falha.regra(), falha.getMessage());
            transacao.executeWithoutResult(t -> falhar(jobId, documentoId, falha.getMessage(), contexto));
        } catch (RuntimeException e) {
            // a mensagem vai para o /status do usuário: detalhe interno (storage, SDK, SQL) só no log
            log.error("Job {} falhou", jobId, e);
            String mensagem = "Falha inesperada no processamento. Job " + jobId + ".";
            contexto.registrar(Severidade.ERRO, null, null, "FALHA_INTERNA", mensagem);
            transacao.executeWithoutResult(t -> falhar(jobId, documentoId, mensagem, contexto));
        }
    }

    private Long iniciar(Long jobId) {
        JobProcessamento job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != StatusJob.PENDENTE) {
            return null;
        }
        job.setStatus(StatusJob.PROCESSANDO);
        job.setTentativas(job.getTentativas() + 1);
        job.setInicio(LocalDateTime.now());
        job.setFim(null);
        job.setEtapa(EtapaProcessamento.RECEBIDO);
        job.setProgresso(0);
        DocumentoFiscal documento = job.getDocumento();
        documento.setStatus(DocumentoStatus.PROCESSANDO);
        documento.setTipo(detectarTipo(documento));
        return documento.getId();
    }

    /** Sem o arquivo, o tipo fica como está e o processamento falha com a mensagem certa. */
    private DocumentoTipo detectarTipo(DocumentoFiscal documento) {
        if (!armazenamento.existe(documento.getChaveArmazenamento())) {
            return documento.getTipo();
        }
        try (InputStream in = armazenamento.abrir(documento.getChaveArmazenamento())) {
            return DetectorTipoDocumento.detectar(in.readNBytes(DetectorTipoDocumento.BYTES_INICIO), documento.getExtensao());
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o início do arquivo", e);
        }
    }

    private void processar(Long documentoId, ContextoProcessamento contexto) {
        DocumentoFiscal documento = documentoRepository.findById(documentoId).orElseThrow();
        if (!armazenamento.existe(documento.getChaveArmazenamento())) {
            throw new FalhaImportacao("ARQUIVO_AUSENTE", "Arquivo não encontrado no armazenamento.");
        }
        contexto.usar(documento);
        DocumentoTipo informado = documento.getTipoInformado();
        if (informado != null && informado != DocumentoTipo.OUTRO && informado != documento.getTipo()) {
            contexto.registrar(Severidade.ALERTA, null, null, "TIPO_DIVERGENTE", "Informado como " + informado
                    + ", mas o conteúdo é " + documento.getTipo() + ". Vale o conteúdo.");
        }
        processadores.stream().filter(p -> p.suporta(documento.getTipo())).findFirst().orElseThrow()
                .processar(documento, contexto);
    }

    private void concluir(Long jobId, Long documentoId, DocumentoStatus status, ContextoProcessamento contexto,
                          AguardandoUsuario pendencia) {
        DocumentoFiscal documento = documentoRepository.findById(documentoId).orElseThrow();
        documento.setStatus(status);
        if (pendencia != null) {
            documento.setCnpjArquivo(pendencia.cnpj());
            documento.setNomeEmpresaArquivo(pendencia.nome());
        }
        gravarInconsistencias(documento, contexto);
        JobProcessamento job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(StatusJob.CONCLUIDO);
        job.setEtapa(EtapaProcessamento.CONCLUIDO);
        job.setProgresso(100);
        job.setMensagem(pendencia == null ? null : pendencia.getMessage());
        job.setFim(LocalDateTime.now());
    }

    /** A etapa fica onde parou: a tela mostra em que ponto o arquivo falhou. */
    private void falhar(Long jobId, Long documentoId, String mensagem, ContextoProcessamento contexto) {
        DocumentoFiscal documento = documentoRepository.findById(documentoId).orElseThrow();
        documento.setStatus(DocumentoStatus.ERRO);
        gravarInconsistencias(documento, contexto);
        JobProcessamento job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(StatusJob.ERRO);
        job.setMensagem(mensagem);
        job.setFim(LocalDateTime.now());
    }

    private void gravarInconsistencias(DocumentoFiscal documento, ContextoProcessamento contexto) {
        inconsistenciaRepository.apagarDoDocumento(documento.getId());
        List<Inconsistencia> novas = contexto.inconsistencias();
        novas.forEach(i -> i.setDocumento(documento));
        inconsistenciaRepository.saveAll(novas);
    }
}
