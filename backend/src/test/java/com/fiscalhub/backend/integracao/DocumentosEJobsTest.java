package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.JobProcessamento;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.entity.enums.StatusJob;
import com.fiscalhub.backend.entity.enums.TipoJob;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.fiscalhub.backend.repository.JobProcessamentoRepository;
import com.fiscalhub.backend.service.DespachoJobs;
import com.fiscalhub.backend.storage.ArmazenamentoArquivos;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Upload por stream com SHA-256 contra duplicados, download por stream, job em segundo plano com status
 * consultável e recuperação de jobs presos. Sem @Transactional: o job só é despachado depois do commit.
 */
@TesteIntegracao
class DocumentosEJobsTest {

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired DocumentoFiscalRepository documentoRepository;
    @Autowired JobProcessamentoRepository jobRepository;
    @Autowired ArmazenamentoArquivos armazenamento;
    @Autowired DespachoJobs despachoJobs;

    private String token;
    private Cliente cliente;
    private final String conteudo = "|0000|SPED|" + UUID.randomUUID() + "|";

    @BeforeEach
    void setUp() {
        String sufixo = UUID.randomUUID().toString();
        Usuario consultor = dados.usuario(dados.escritorio("Docs " + sufixo), "docs-" + sufixo + "@test.com", Papel.CONSULTOR);
        token = dados.token(consultor);
        cliente = dados.cliente(consultor, "11222333000181");
    }

    @Test
    void importaProcessaEmSegundoPlanoEBaixaPorStream() throws Exception {
        long id = importar("sped.txt", "text/plain");
        DocumentoFiscal salvo = documentoRepository.findById(id).orElseThrow();
        assertThat(salvo.getSha256()).hasSize(64);
        assertThat(armazenamento.existe(salvo.getChaveArmazenamento())).isTrue();

        aguardar(() -> documentoRepository.findById(id).orElseThrow().getStatus() == DocumentoStatus.PROCESSADO);
        mvc.perform(get("/api/clientes/{c}/documentos/{d}/status", cliente.getId(), id).header(AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSADO"))
                .andExpect(jsonPath("$.job").value("CONCLUIDO"))
                .andExpect(jsonPath("$.tentativas").value(1));

        MvcResult download = mvc.perform(get("/api/clientes/{c}/documentos/{d}/arquivo", cliente.getId(), id)
                        .header(AUTHORIZATION, token))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(asyncDispatch(download))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("sped.txt")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(conteudo.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void mesmoArquivoDuasVezesNoClienteE409ApontandoOExistente() throws Exception {
        long id = importar("sped.txt", "text/plain");

        mvc.perform(upload("copia.txt", "text/plain").header(AUTHORIZATION, token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.recursoExistenteId").value(id));
        assertThat(documentoRepository.findByClienteIdOrderByDataImportacaoDesc(cliente.getId())).hasSize(1);
    }

    @Test
    void extensaoOuTipoForaDaListaE400() throws Exception {
        mvc.perform(upload("nota.xml", "application/xml").header(AUTHORIZATION, token)).andExpect(status().isBadRequest());
        mvc.perform(upload("sped.txt", "application/x-msdownload").header(AUTHORIZATION, token)).andExpect(status().isBadRequest());
    }

    @Test
    void excluirRemoveRegistroEArquivo() throws Exception {
        long id = importar("sped.txt", "text/plain");
        String chave = documentoRepository.findById(id).orElseThrow().getChaveArmazenamento();

        mvc.perform(delete("/api/clientes/{c}/documentos/{d}", cliente.getId(), id).header(AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        assertThat(documentoRepository.findById(id)).isEmpty();
        assertThat(armazenamento.existe(chave)).isFalse();
    }

    @Test
    void jobPresoEmProcessandoVoltaParaFilaNaSubida() {
        DocumentoFiscal documento = dados.documento(cliente);
        armazenamento.salvar(documento.getChaveArmazenamento(), new ByteArrayInputStream(new byte[]{1}), 1, "application/pdf");
        JobProcessamento preso = new JobProcessamento();
        preso.setTipo(TipoJob.VALIDACAO_DOCUMENTO);
        preso.setDocumento(documento);
        preso.setStatus(StatusJob.PROCESSANDO);
        jobRepository.save(preso);

        despachoJobs.recuperarNaSubida();

        aguardar(() -> jobRepository.findById(preso.getId()).orElseThrow().getStatus() == StatusJob.CONCLUIDO);
    }

    private long importar(String nome, String tipo) throws Exception {
        String resposta = mvc.perform(upload(nome, tipo).header(AUTHORIZATION, token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEBIDO"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private MockMultipartHttpServletRequestBuilder upload(String nome, String tipo) {
        return multipart("/api/clientes/{c}/documentos", cliente.getId())
                .file(new MockMultipartFile("arquivo", nome, tipo, conteudo.getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("documento", "", "application/json",
                        "{\"tipo\":\"EFD_CONTRIBUICOES\",\"tributos\":[\"PIS\"],\"periodoInicio\":\"2024-01-01\",\"periodoFim\":\"2024-01-31\"}"
                                .getBytes(StandardCharsets.UTF_8)));
    }

    /** Espera simples pelo processamento assíncrono (até 10 s). */
    private static void aguardar(BooleanSupplier condicao) {
        long limite = System.currentTimeMillis() + 10_000;
        while (!condicao.getAsBoolean()) {
            if (System.currentTimeMillis() > limite) {
                throw new AssertionError("Condição não atingida em 10 s");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }
}
