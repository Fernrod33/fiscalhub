package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.Escritorio;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.repository.ClienteRepository;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.fiscalhub.backend.integracao.Espera.aguardar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ECD sintéticas de src/test/resources/amostras: leitura, validações, cliente pelo arquivo e retificação. */
@TesteIntegracao
class ImportacaoEcdTest {

    private static final String CNPJ = "11222333000181";

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired DocumentoFiscalRepository documentoRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired JdbcTemplate jdbc;

    private Escritorio escritorio;
    private Usuario consultor;
    private RequestPostProcessor sessao;

    @BeforeEach
    void setUp() {
        String sufixo = UUID.randomUUID().toString();
        escritorio = dados.escritorio("ECD " + sufixo);
        consultor = dados.usuario(escritorio, "ecd-" + sufixo + "@test.com", Papel.CONSULTOR);
        sessao = dados.sessao(consultor);
    }

    @Test
    void semClienteComOCnpjAguardaEConfirmarCadastraOSugeridoEProcessa() throws Exception {
        long id = enviar("/api/documentos", "ecd-minima-l9.txt");
        aguardarStatus(id, DocumentoStatus.AGUARDANDO_CLIENTE);
        mvc.perform(get("/api/documentos/{d}", id).with(sessao))
                .andExpect(jsonPath("$.cnpjArquivo").value(CNPJ))
                .andExpect(jsonPath("$.nomeEmpresaArquivo").value("EMPRESA EXEMPLO LTDA"))
                .andExpect(jsonPath("$.clienteId").isEmpty());
        assertThat(contar("escrituracao_contabil", id)).isZero(); // aguardar desfaz o que foi gravado

        mvc.perform(post("/api/documentos/{d}/confirmar-cliente", id).with(sessao)).andExpect(status().isOk());
        aguardarStatus(id, DocumentoStatus.PROCESSADO);

        Cliente criado = clienteRepository.findByEscritorioIdAndCnpj(escritorio.getId(), CNPJ).orElseThrow();
        assertThat(criado.getRazaoSocial()).isEqualTo("EMPRESA EXEMPLO LTDA");
        mvc.perform(get("/api/documentos/{d}", id).with(sessao))
                .andExpect(jsonPath("$.clienteId").value(criado.getId().intValue()))
                .andExpect(jsonPath("$.tipo").value("ECD"))
                .andExpect(jsonPath("$.periodoInicio").value("2024-01-01"))
                .andExpect(jsonPath("$.periodoFim").value("2024-12-31"));
        mvc.perform(get("/api/documentos/{d}/inconsistencias", id).with(sessao)).andExpect(jsonPath("$").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ecd-minima-l9.txt", "ecd-minima-l8.txt", "ecd-minima-l7.txt"})
    void cadaLeiauteCobertoGravaPlanoSaldosEDemonstracoesPublicadas(String amostra) throws Exception {
        Cliente cliente = dados.cliente(consultor, CNPJ);
        long id = enviar("/api/clientes/" + cliente.getId() + "/documentos", amostra);
        aguardarStatus(id, DocumentoStatus.PROCESSADO);

        Map<String, Object> esc = jdbc.queryForMap("SELECT * FROM escrituracao_contabil WHERE documento_id = ?", id);
        assertThat(esc.get("plano_referencial")).isEqualTo("1");
        assertThat(esc.get("situacao")).isEqualTo("VIGENTE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conta_contabil WHERE escrituracao_id = ?", Long.class, esc.get("id"))).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conta_contabil WHERE escrituracao_id = ? AND codigo_referencial IS NOT NULL",
                Long.class, esc.get("id"))).isEqualTo(7);
        // convenção de sinal: devedor +, credor −; a origem de cada valor é registro + linha do arquivo
        Map<String, Object> caixa = jdbc.queryForMap("""
                SELECT s.saldo_final, s.registro, s.linha FROM saldo_conta s JOIN conta_contabil c ON c.id = s.conta_id
                WHERE s.escrituracao_id = ? AND c.codigo = '1.1.01' AND s.origem = 'I155'""", esc.get("id"));
        assertThat((BigDecimal) caixa.get("saldo_final")).isEqualByComparingTo("4000");
        assertThat(caixa.get("registro")).isEqualTo("I155");
        assertThat(caixa.get("linha")).isEqualTo(26L);
        assertThat(jdbc.queryForObject("""
                SELECT s.saldo_final FROM saldo_conta s JOIN conta_contabil c ON c.id = s.conta_id
                WHERE s.escrituracao_id = ? AND c.codigo = '4.1' AND s.origem = 'I355'""", BigDecimal.class, esc.get("id")))
                .isEqualByComparingTo("-8000");

        List<Map<String, Object>> publicadas = jdbc.queryForList("""
                SELECT d.tipo, l.codigo, l.valor_final, l.registro FROM demonstracao d JOIN linha_demonstracao l ON l.demonstracao_id = d.id
                WHERE d.escrituracao_id = ? AND d.origem = 'PUBLICADA' AND l.codigo IN ('1', '3') ORDER BY d.tipo""", esc.get("id"));
        assertThat(publicadas).extracting(m -> m.get("tipo") + ":" + ((BigDecimal) m.get("valor_final")).toPlainString() + ":" + m.get("registro"))
                .containsExactly("BP:6000.00:J100", "DRE:-4500.00:J150");
    }

    @Test
    void uploadNoClienteComOutroCnpjEhErroClaro() throws Exception {
        Cliente outro = dados.cliente(consultor, "11444777000161");
        long id = enviar("/api/clientes/" + outro.getId() + "/documentos", "ecd-minima-l9.txt");
        aguardarStatus(id, DocumentoStatus.ERRO);
        mvc.perform(get("/api/documentos/{d}/inconsistencias", id).with(sessao))
                .andExpect(jsonPath("$[0].regra").value("CNPJ_DIVERGENTE"))
                .andExpect(jsonPath("$[0].severidade").value("ERRO"));
        assertThat(contar("escrituracao_contabil", id)).isZero();
    }

    @Test
    void substitutaMarcaAAnteriorComoSubstituidaEPreservaOHistorico() throws Exception {
        Cliente cliente = dados.cliente(consultor, CNPJ);
        long original = enviar("/api/clientes/" + cliente.getId() + "/documentos", "ecd-minima-l9.txt");
        aguardarStatus(original, DocumentoStatus.PROCESSADO);
        long substituta = enviar("/api/clientes/" + cliente.getId() + "/documentos", "ecd-retificadora-l9.txt");
        aguardarStatus(substituta, DocumentoStatus.PROCESSADO);

        assertThat(situacao(original)).isEqualTo("SUBSTITUIDA");
        assertThat(situacao(substituta)).isEqualTo("VIGENTE");
        mvc.perform(get("/api/documentos/{d}/inconsistencias", substituta).with(sessao))
                .andExpect(jsonPath("$[0].regra").value("RETIFICACAO"))
                .andExpect(jsonPath("$[0].severidade").value("INFO"));
    }

    @Test
    void balancoQueNaoFechaGeraErrosComRegistroELinha() throws Exception {
        long id = enviarNoCliente("ecd-balanco-nao-fecha-l9.txt");
        aguardarStatus(id, DocumentoStatus.PROCESSADO_COM_ALERTAS);
        assertThat(regras(id)).contains("DEBITO_DIFERENTE_CREDITO", "BALANCO_NAO_FECHA_SALDOS", "BALANCO_NAO_FECHA_J100");
        mvc.perform(get("/api/documentos/{d}/inconsistencias", id).with(sessao))
                .andExpect(jsonPath("$[?(@.regra == 'DEBITO_DIFERENTE_CREDITO')].linha").value(25))
                .andExpect(jsonPath("$[?(@.regra == 'BALANCO_NAO_FECHA_J100')].registro").value("J100"));
    }

    @Test
    void contaSemI051EhAlertaComALista() throws Exception {
        long id = enviarNoCliente("ecd-sem-i051-l9.txt");
        aguardarStatus(id, DocumentoStatus.PROCESSADO_COM_ALERTAS);
        mvc.perform(get("/api/documentos/{d}/inconsistencias", id).with(sessao))
                .andExpect(jsonPath("$[0].regra").value("CONTA_SEM_I051"))
                .andExpect(jsonPath("$[0].severidade").value("ALERTA"))
                .andExpect(jsonPath("$[0].mensagem").value(org.hamcrest.Matchers.containsString("1.1.02")));
    }

    @Test
    void contagem9900ErradaApontaALinhaDoBloco9() throws Exception {
        long id = enviarNoCliente("ecd-contagem-errada-l9.txt");
        aguardarStatus(id, DocumentoStatus.PROCESSADO_COM_ALERTAS);
        mvc.perform(get("/api/documentos/{d}/inconsistencias", id).with(sessao))
                .andExpect(jsonPath("$[0].regra").value("CONTAGEM_9900"))
                .andExpect(jsonPath("$[0].registro").value("9900"))
                .andExpect(jsonPath("$[0].mensagem").value(org.hamcrest.Matchers.containsString("8 registro(s) I155")));
    }

    @Test
    void reprocessarApagaOQueGerouERefazSemDuplicar() throws Exception {
        long id = enviarNoCliente("ecd-minima-l9.txt");
        aguardarStatus(id, DocumentoStatus.PROCESSADO);
        mvc.perform(post("/api/documentos/{d}/reprocessar", id).with(sessao)).andExpect(status().isOk());
        aguardar(() -> jdbc.queryForObject("SELECT count(*) FROM job_processamento WHERE documento_id = ? AND status = 'CONCLUIDO'",
                Long.class, id) == 2);
        assertThat(contar("escrituracao_contabil", id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM saldo_conta s JOIN escrituracao_contabil e ON e.id = s.escrituracao_id
                WHERE e.documento_id = ?""", Long.class, id)).isEqualTo(9); // 7 I155 + 2 I355
    }

    private long enviarNoCliente(String amostra) throws Exception {
        Cliente cliente = dados.cliente(consultor, CNPJ);
        return enviar("/api/clientes/" + cliente.getId() + "/documentos", amostra);
    }

    private long enviar(String url, String amostra) throws Exception {
        byte[] conteudo = new ClassPathResource("amostras/" + amostra).getContentAsByteArray();
        String resposta = mvc.perform(multipart(url).file(new MockMultipartFile("arquivo", amostra, "text/plain", conteudo)).with(sessao))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private void aguardarStatus(long id, DocumentoStatus esperado) {
        aguardar(() -> documentoRepository.findById(id).orElseThrow().getStatus().finalizado());
        assertThat(documentoRepository.findById(id).orElseThrow().getStatus()).as("regras: %s", regras(id)).isEqualTo(esperado);
    }

    private List<String> regras(long id) {
        return jdbc.queryForList("SELECT regra FROM inconsistencia_importacao WHERE documento_id = ? ORDER BY id", String.class, id);
    }

    private long contar(String tabela, long documentoId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabela + " WHERE documento_id = ?", Long.class, documentoId);
    }

    private String situacao(long documentoId) {
        return jdbc.queryForObject("SELECT situacao FROM escrituracao_contabil WHERE documento_id = ?", String.class, documentoId);
    }
}
