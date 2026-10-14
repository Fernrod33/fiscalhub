package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.fiscalhub.backend.integracao.Espera.aguardar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Balancete em planilha: assistente de colunas e período, mapeamento salvo por cliente e de-para referencial. */
@TesteIntegracao
class ImportacaoPlanilhaTest {

    private static final String COLUNAS_XLSX = """
            {"codigo":0,"descricao":1,"saldoInicial":2,"debitos":4,"creditos":5,"saldoFinal":6,"dcInicial":3,"dcFinal":7}""";

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired DocumentoFiscalRepository documentoRepository;
    @Autowired JdbcTemplate jdbc;

    private RequestPostProcessor sessao;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        String sufixo = UUID.randomUUID().toString();
        Usuario u = dados.usuario(dados.escritorio("Planilha " + sufixo), "plan-" + sufixo + "@test.com", Papel.CONSULTOR);
        sessao = dados.sessao(u);
        cliente = dados.cliente(u, "11222333000181");
    }

    @Test
    void assistenteDeColunasPeriodoEDeParaSemDigitarValores() throws Exception {
        long id = enviar("balancete.xlsx", null);
        aguardarStatus(id, DocumentoStatus.AGUARDANDO_MAPEAMENTO);

        mvc.perform(get("/api/documentos/{d}/planilha", id).with(sessao))
                .andExpect(jsonPath("$.abas[0]").value("Balancete"))
                .andExpect(jsonPath("$.linhas[2][0]").value("Conta"))
                .andExpect(jsonPath("$.sugestao.reconhecido").value(false))
                .andExpect(jsonPath("$.sugestao.linhaCabecalho").value(3))
                .andExpect(jsonPath("$.sugestao.colunas.saldoFinal").value(6))
                .andExpect(jsonPath("$.sugestao.colunas.dcFinal").value(7));

        mapear(id, "Balancete", 3, COLUNAS_XLSX);
        aguardarStatus(id, DocumentoStatus.PROCESSADO_COM_ALERTAS);
        assertThat(regras(id)).containsExactly("CONTA_SEM_DEPARA");
        // origem de cada valor: aba e linha da planilha; convenção devedor +, credor −
        assertThat(saldo(id, "1.1.01")).isEqualByComparingTo("4000");
        assertThat(saldo(id, "4.1")).isEqualByComparingTo("-8000");
        assertThat(jdbc.queryForObject("""
                SELECT s.registro || ':' || s.linha FROM saldo_conta s JOIN conta_contabil c ON c.id = s.conta_id
                JOIN escrituracao_contabil e ON e.id = s.escrituracao_id WHERE e.documento_id = ? AND c.codigo = '1.1.01'""",
                String.class, id)).isEqualTo("Aba Balancete:6");

        mvc.perform(get("/api/documentos/{d}/planilha/depara", id).with(sessao))
                .andExpect(jsonPath("$.pendentes").value(7))
                .andExpect(jsonPath("$.contas[*].codigo").value(org.hamcrest.Matchers.contains(
                        "1.1.01", "1.1.02", "2.1", "3.1", "3.2", "4.1", "4.2")));
        mvc.perform(put("/api/documentos/{d}/planilha/depara", id).with(sessao).contentType(APPLICATION_JSON).content("""
                        [{"codigoConta":"1.1.01","codigoReferencial":"1.01.01.01.01"},{"codigoConta":"1.1.02","codigoReferencial":"1.01.01.02.01"},
                         {"codigoConta":"2.1","codigoReferencial":"2.01.01.03.01"},{"codigoConta":"3.1","codigoReferencial":"2.03.01.01.01"},
                         {"codigoConta":"3.2","codigoReferencial":"2.03.04.01.01"},{"codigoConta":"4.1","codigoReferencial":"3.01.01.01.01.05"},
                         {"codigoConta":"4.2","codigoReferencial":"3.01.01.03.01.02"}]"""))
                .andExpect(status().isAccepted());
        aguardarStatus(id, DocumentoStatus.PROCESSADO); // o de-para reprocessa com as associações salvas

        // mesma montagem referencial da ECD
        mvc.perform(get("/api/clientes/{c}/demonstracoes", cliente.getId()).param("tipo", "DRE").with(sessao))
                .andExpect(jsonPath("$.exercicios[0].origemDados").value("PLANILHA"))
                .andExpect(jsonPath("$.linhas[0].valores['2024'].valor").value(-4500.00));
        mvc.perform(get("/api/clientes/{c}/demonstracoes", cliente.getId()).param("tipo", "BP").with(sessao))
                .andExpect(jsonPath("$.linhas[0].valores['2024'].valor").value(6000.00));
    }

    @Test
    void mapeamentoSalvoEhReconhecidoNoProximoUploadDoMesmoFormato() throws Exception {
        long primeiro = enviar("balancete.csv", null);
        aguardarStatus(primeiro, DocumentoStatus.AGUARDANDO_MAPEAMENTO);
        mapear(primeiro, null, 1, "{\"codigo\":0,\"descricao\":1,\"saldoInicial\":2,\"debitos\":3,\"creditos\":4,\"saldoFinal\":5}");
        aguardarStatus(primeiro, DocumentoStatus.PROCESSADO_COM_ALERTAS);
        assertThat(saldo(primeiro, "2.1")).isEqualByComparingTo("-1000"); // valores com sinal, sem coluna D/C

        long segundo = enviar("balancete.csv", "\r\n"); // outro arquivo, mesmo cabeçalho
        aguardarStatus(segundo, DocumentoStatus.AGUARDANDO_MAPEAMENTO); // falta só o período
        mvc.perform(get("/api/documentos/{d}/planilha", segundo).with(sessao))
                .andExpect(jsonPath("$.sugestao.reconhecido").value(true))
                .andExpect(jsonPath("$.sugestao.colunas.saldoFinal").value(5));
    }

    @Test
    void ecdDoClienteSugereOReferencialPeloMesmoCodigoDeConta() throws Exception {
        long ecd = enviar("ecd-minima-l9.txt", null);
        aguardarStatus(ecd, DocumentoStatus.PROCESSADO);
        long id = enviar("balancete-latin1.csv", null);
        aguardarStatus(id, DocumentoStatus.AGUARDANDO_MAPEAMENTO);
        mvc.perform(get("/api/documentos/{d}/planilha", id).with(sessao))
                .andExpect(jsonPath("$.linhas[2][1]").value("Descrição da conta")) // Latin-1 lido certo
                .andExpect(jsonPath("$.sugestao.linhaCabecalho").value(3));

        mapear(id, null, 3, "{\"codigo\":0,\"descricao\":1,\"saldoInicial\":2,\"debitos\":3,\"creditos\":4,\"saldoFinal\":5}");
        aguardarStatus(id, DocumentoStatus.PROCESSADO);
        assertThat(saldo(id, "2.1")).isEqualByComparingTo("-1000"); // "1.000,00 C"
        mvc.perform(get("/api/documentos/{d}/planilha/depara", id).with(sessao))
                .andExpect(jsonPath("$.pendentes").value(0))
                .andExpect(jsonPath("$.contas[0].sugestaoEcd").value("1.01.01.01.01"))
                .andExpect(jsonPath("$.contas[0].descricaoReferencial").value("Caixa Matriz"));
    }

    @Test
    void formulaValePeloValorEmCache() throws Exception {
        long id = enviar("balancete-formulas.xlsx", null);
        aguardarStatus(id, DocumentoStatus.AGUARDANDO_MAPEAMENTO);
        mapear(id, null, 1, "{\"codigo\":0,\"descricao\":1,\"saldoInicial\":2,\"debitos\":3,\"creditos\":4,\"saldoFinal\":5}");
        aguardarStatus(id, DocumentoStatus.PROCESSADO_COM_ALERTAS);
        assertThat(saldo(id, "1.1.01")).isEqualByComparingTo("4000");
        assertThat(regras(id)).containsExactly("CONTA_SEM_DEPARA");
    }

    @Test
    void balanceteQueNaoFechaEhErroComALinha() throws Exception {
        String csv = "Conta;Descrição;Saldo final\r\n1.1;Caixa;100,00 D\r\n2.1;Fornecedores;90,00 C\r\n";
        long id = enviarConteudo("desbalanceado.csv", csv.getBytes(StandardCharsets.UTF_8));
        aguardarStatus(id, DocumentoStatus.AGUARDANDO_MAPEAMENTO);
        mapear(id, null, 1, "{\"codigo\":0,\"descricao\":1,\"saldoFinal\":2}");
        aguardarStatus(id, DocumentoStatus.PROCESSADO_COM_ALERTAS);
        assertThat(regras(id)).contains("BALANCETE_NAO_FECHA");
    }

    @Test
    void buscaNoPlanoReferencialSoAnaliticas() throws Exception {
        mvc.perform(get("/api/referencial/contas").param("plano", "1").param("exercicio", "2024").param("busca", "caixa").with(sessao))
                .andExpect(jsonPath("$[?(@.codigo == '1.01.01.01.01')].descricao").value("Caixa Matriz"))
                .andExpect(jsonPath("$[?(@.codigo == '1.01.01.01')]").isEmpty()); // sintética fica de fora
    }

    private void mapear(long id, String aba, int linhaCabecalho, String colunas) throws Exception {
        mvc.perform(post("/api/documentos/{d}/planilha/mapeamento", id).with(sessao).contentType(APPLICATION_JSON).content("""
                        {"aba":%s,"linhaCabecalho":%d,"colunas":%s,"periodoInicio":"2024-01-01","periodoFim":"2024-12-31",
                         "planoReferencial":"1"}""".formatted(aba == null ? "null" : "\"" + aba + "\"", linhaCabecalho, colunas)))
                .andExpect(status().isAccepted());
    }

    private long enviar(String amostra, String acrescimo) throws Exception {
        byte[] conteudo = new ClassPathResource("amostras/" + amostra).getContentAsByteArray();
        if (acrescimo != null) {
            conteudo = (new String(conteudo, StandardCharsets.ISO_8859_1) + acrescimo).getBytes(StandardCharsets.ISO_8859_1);
        }
        return enviarConteudo(amostra, conteudo);
    }

    private long enviarConteudo(String nome, byte[] conteudo) throws Exception {
        String tipo = nome.endsWith(".xlsx") ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : nome.endsWith(".csv") ? "text/csv" : "text/plain";
        String resposta = mvc.perform(multipart("/api/clientes/{c}/documentos", cliente.getId())
                        .file(new MockMultipartFile("arquivo", nome, tipo, conteudo)).with(sessao))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private void aguardarStatus(long id, DocumentoStatus esperado) {
        aguardar(() -> {
            var doc = documentoRepository.findById(id).orElseThrow();
            return doc.getStatus().finalizado() && jdbc.queryForObject(
                    "SELECT count(*) FROM job_processamento WHERE documento_id = ? AND status IN ('PENDENTE', 'PROCESSANDO')", Long.class, id) == 0;
        });
        assertThat(documentoRepository.findById(id).orElseThrow().getStatus()).as("regras: %s", regras(id)).isEqualTo(esperado);
    }

    private List<String> regras(long id) {
        return jdbc.queryForList("SELECT regra FROM inconsistencia_importacao WHERE documento_id = ? ORDER BY id", String.class, id);
    }

    private BigDecimal saldo(long documentoId, String conta) {
        return jdbc.queryForObject("""
                SELECT s.saldo_final FROM saldo_conta s JOIN conta_contabil c ON c.id = s.conta_id
                JOIN escrituracao_contabil e ON e.id = s.escrituracao_id WHERE e.documento_id = ? AND c.codigo = ?""",
                BigDecimal.class, documentoId, conta);
    }
}
