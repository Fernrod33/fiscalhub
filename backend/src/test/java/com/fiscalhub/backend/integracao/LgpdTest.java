package com.fiscalhub.backend.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.lgpd.LgpdService;
import com.fiscalhub.backend.operacao.MascaraLog;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.fiscalhub.backend.repository.UsuarioRepository;
import com.fiscalhub.backend.storage.ArmazenamentoArquivos;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.fiscalhub.backend.integracao.Espera.aguardar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tarefa 4 da Fase 7: aceite dos termos, portabilidade, exclusão com prazo de arrependimento e logs sem dado pessoal. */
@TesteIntegracao
@ExtendWith(OutputCaptureExtension.class)
class LgpdTest {

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired LgpdService lgpd;
    @Autowired UsuarioRepository usuarios;
    @Autowired DocumentoFiscalRepository documentos;
    @Autowired ArmazenamentoArquivos armazenamento;

    private Usuario admin;
    private RequestPostProcessor sessao;
    private Cliente cliente;
    private long documentoId;

    @BeforeEach
    void setUp() throws Exception {
        admin = dados.usuario(dados.escritorio("LGPD " + System.nanoTime()), "lgpd-" + System.nanoTime() + "@test.com", Papel.ADMIN);
        sessao = dados.sessao(admin);
        cliente = dados.cliente(admin, "11222333000181");
        byte[] ecd = new ClassPathResource("amostras/ecd-2024-l9.txt").getContentAsByteArray();
        String resposta = mvc.perform(multipart("/api/clientes/{c}/documentos", cliente.getId())
                        .file(new MockMultipartFile("arquivo", "ecd-2024.txt", "text/plain", ecd)).with(sessao))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        documentoId = ((Number) JsonPath.read(resposta, "$.id")).longValue();
        aguardar(() -> documentos.findById(documentoId).orElseThrow().getStatus().finalizado());
    }

    @Test
    void semOAceiteDosTermosSoDaParaLerOProprioCadastroEAceitar() throws Exception {
        Usuario novo = dados.usuario(admin.getEscritorio(), "termos-" + System.nanoTime() + "@test.com", Papel.CONSULTOR);
        novo.setTermosVersao(null);
        usuarios.saveAndFlush(novo);
        RequestPostProcessor s = dados.sessao(novo);
        mvc.perform(get("/api/clientes").with(s)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problemas/termos-pendentes"));
        mvc.perform(get("/api/usuarios/me").with(s)).andExpect(jsonPath("$.termosPendentes").value(true));
        mvc.perform(post("/api/usuarios/me/termos").with(s)).andExpect(status().isOk()).andExpect(jsonPath("$.termosPendentes").value(false));
        mvc.perform(get("/api/clientes").with(s)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_evento WHERE acao = 'TERMOS_ACEITAR' AND usuario_id = ?",
                Long.class, novo.getId())).isOne();
    }

    @Test
    void exportacaoTrazOArquivoOriginalEOsDadosDerivadosEmJson() throws Exception {
        var inicio = mvc.perform(get("/api/clientes/{c}/exportacao", cliente.getId()).with(sessao)).andExpect(status().isOk()).andReturn();
        byte[] zip = mvc.perform(asyncDispatch(inicio)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        Map<String, byte[]> entradas = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                entradas.put(e.getName(), in.readAllBytes());
            }
        }
        assertThat(entradas).containsKeys("LEIAME.txt", "dados/cliente.json", "dados/documento_fiscal.json",
                "dados/escrituracao_contabil.json", "dados/conta_contabil.json", "dados/saldo_conta.json");
        assertThat(entradas.get("documentos/" + documentoId + "-ecd-2024.txt"))
                .isEqualTo(new ClassPathResource("amostras/ecd-2024-l9.txt").getContentAsByteArray());
        JsonNode clienteJson = json.readTree(entradas.get("dados/cliente.json"));
        assertThat(clienteJson).hasSize(1);
        assertThat(clienteJson.get(0).get("cnpj").asText()).isEqualTo("11222333000181");
        assertThat(json.readTree(entradas.get("dados/saldo_conta.json")).size()).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_evento WHERE acao = 'CLIENTE_EXPORTAR' AND recurso_id = ?",
                Long.class, cliente.getId())).isOne();
    }

    @Test
    void exclusaoPedeOCnpjEsperaOPrazoPodeSerCanceladaEDepoisApagaTudo() throws Exception {
        String chave = documentos.findById(documentoId).orElseThrow().getChaveArmazenamento();
        mvc.perform(post("/api/clientes/{c}/exclusao", cliente.getId()).with(sessao).contentType(APPLICATION_JSON)
                .content("{\"confirmacao\":\"99999999000199\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/clientes/{c}/exclusao", cliente.getId()).with(sessao).contentType(APPLICATION_JSON)
                .content("{\"confirmacao\":\"11.222.333/0001-81\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.exclusaoPrevistaPara").isNotEmpty());
        lgpd.concluirVencidas(); // ainda no prazo de arrependimento: nada sai
        assertThat(documentos.findById(documentoId)).isPresent();
        mvc.perform(delete("/api/clientes/{c}/exclusao", cliente.getId()).with(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.exclusaoPrevistaPara").isEmpty());

        mvc.perform(post("/api/clientes/{c}/exclusao", cliente.getId()).with(sessao).contentType(APPLICATION_JSON)
                .content("{\"confirmacao\":\"11222333000181\"}")).andExpect(status().isOk());
        jdbc.update("UPDATE cliente SET exclusao_prevista_para = now() - interval '1 minute' WHERE id = ?", cliente.getId());
        lgpd.concluirVencidas();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM cliente WHERE id = ?", Long.class, cliente.getId())).isZero();
        assertThat(documentos.findById(documentoId)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM escrituracao_contabil WHERE cliente_id = ?", Long.class, cliente.getId())).isZero();
        assertThat(armazenamento.existe(chave)).isFalse();
        String detalhes = jdbc.queryForObject("SELECT detalhes::text FROM auditoria_evento WHERE acao = 'CLIENTE_EXCLUIR' AND recurso_id = ?",
                String.class, cliente.getId());
        assertThat(detalhes).contains("CONCLUIDA", "saldo_conta").doesNotContain("11222333000181", cliente.getRazaoSocial());
        // a auditoria do pedido continua, só com ids
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_evento WHERE acao = 'CLIENTE_EXCLUSAO_SOLICITAR' AND recurso_id = ?",
                Long.class, cliente.getId())).isEqualTo(2);
    }

    @Test
    void logsSaemSemCnpjCpfNemValores(CapturedOutput saida) {
        var log = LoggerFactory.getLogger("teste.lgpd");
        log.warn("Documento do CNPJ 11.222.333/0001-81 (sócio 123.456.789-09) com diferença de R$ 1.234,56 e 98765.43",
                new IllegalStateException("CNPJ 11222333000181 sem cliente; total 45.000,00"));
        assertThat(saida.getOut()).contains("[cnpj …81]", "[cpf …09]", "[valor]")
                .doesNotContain("11.222.333/0001-81", "11222333000181", "123.456.789-09", "1.234,56", "98765.43", "45.000,00");
    }

    @Test
    void jsonDoPerfilProdTambemSaiMascarado() {
        JsonWriter<String> escritor = JsonWriter.of(m -> {
            m.add("message", s -> s);
            new MascaraLog.Json().customize((JsonWriter.Members) m);
        });
        assertThat(escritor.writeToString("cliente 11222333000181 deve R$ 10,00")).doesNotContain("11222333000181", "10,00");
    }
}
