package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.fiscalhub.backend.integracao.Espera.aguardar;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Upload na central (sem cliente), tipo detectado pelo conteúdo, inconsistências e reprocessamento. Sem @Transactional: há job. */
@TesteIntegracao
class PipelineImportacaoTest {

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired DocumentoFiscalRepository documentoRepository;

    private RequestPostProcessor consultor;
    private RequestPostProcessor assistente;

    @BeforeEach
    void setUp() {
        String sufixo = UUID.randomUUID().toString();
        var escritorio = dados.escritorio("Pipeline " + sufixo);
        Usuario u = dados.usuario(escritorio, "pipe-" + sufixo + "@test.com", Papel.CONSULTOR);
        consultor = dados.sessao(u);
        assistente = dados.sessao(dados.usuario(escritorio, "pipe-assist-" + sufixo + "@test.com", Papel.ASSISTENTE));
    }

    @Test
    void tipoInformadoDivergenteViraAlertaEValeOConteudo() throws Exception {
        long id = enviar("dados.pdf", "application/pdf", "%PDF-1.7 " + UUID.randomUUID(), "{\"tipo\":\"ECD\"}");
        aguardar(() -> documentoRepository.findById(id).orElseThrow().getStatus().finalizado());

        mvc.perform(get("/api/documentos/{d}", id).with(consultor))
                .andExpect(jsonPath("$.tipo").value("OUTRO"))
                .andExpect(jsonPath("$.tipoInformado").value("ECD"))
                .andExpect(jsonPath("$.clienteId").isEmpty())
                .andExpect(jsonPath("$.status").value("PROCESSADO_COM_ALERTAS"));
        mvc.perform(get("/api/documentos/{d}/inconsistencias", id).with(consultor))
                .andExpect(jsonPath("$[0].regra").value("TIPO_DIVERGENTE"))
                .andExpect(jsonPath("$[0].severidade").value("ALERTA"))
                .andExpect(jsonPath("$[1].regra").value("TIPO_SEM_LEITURA"));
    }

    @Test
    void reprocessarSoConsultorOuAdminERodaDeNovoSemDuplicarInconsistencias() throws Exception {
        long id = enviar("x.pdf", "application/pdf", "%PDF " + UUID.randomUUID(), null);
        aguardar(() -> documentoRepository.findById(id).orElseThrow().getStatus().finalizado());

        mvc.perform(post("/api/documentos/{d}/reprocessar", id).with(assistente)).andExpect(status().isForbidden());
        mvc.perform(post("/api/documentos/{d}/reprocessar", id).with(consultor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEBIDO"));
        aguardar(() -> documentoRepository.findById(id).orElseThrow().getStatus() == DocumentoStatus.PROCESSADO_COM_ALERTAS);

        mvc.perform(get("/api/documentos/{d}/status", id).with(consultor)).andExpect(jsonPath("$.tentativas").value(1));
        mvc.perform(get("/api/documentos/{d}/inconsistencias", id).with(consultor))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void confirmarClienteSoComDocumentoAguardando() throws Exception {
        long id = enviar("y.pdf", "application/pdf", "%PDF " + UUID.randomUUID(), null);
        aguardar(() -> documentoRepository.findById(id).orElseThrow().getStatus().finalizado());
        mvc.perform(post("/api/documentos/{d}/confirmar-cliente", id).with(consultor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O documento não está aguardando cliente."));
    }

    @Test
    void planilhaNaCentralSemClienteAguardaOClienteSemSugestao() throws Exception {
        byte[] xlsx = new org.springframework.core.io.ClassPathResource("amostras/balancete.xlsx").getContentAsByteArray();
        String resposta = mvc.perform(multipart("/api/documentos").file(new MockMultipartFile("arquivo", "balancete.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx)).with(consultor))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(resposta, "$.id")).longValue();
        aguardar(() -> documentoRepository.findById(id).orElseThrow().getStatus().finalizado());
        mvc.perform(get("/api/documentos/{d}", id).with(consultor))
                .andExpect(jsonPath("$.status").value("AGUARDANDO_CLIENTE"))
                .andExpect(jsonPath("$.tipo").value("BALANCETE"))
                .andExpect(jsonPath("$.cnpjArquivo").isEmpty());
    }

    private long enviar(String nome, String tipo, String conteudo, String metadados) throws Exception {
        var requisicao = multipart("/api/documentos")
                .file(new MockMultipartFile("arquivo", nome, tipo, conteudo.getBytes(StandardCharsets.ISO_8859_1)));
        if (metadados != null) {
            requisicao.file(new MockMultipartFile("documento", "", "application/json", metadados.getBytes(StandardCharsets.UTF_8)));
        }
        String resposta = mvc.perform(requisicao.with(consultor))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }
}
