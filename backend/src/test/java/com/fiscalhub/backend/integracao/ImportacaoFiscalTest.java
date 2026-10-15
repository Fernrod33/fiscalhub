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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.fiscalhub.backend.integracao.Espera.aguardar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ECF, EFD-Contribuições e EFD ICMS/IPI sintéticas (scripts/sped/gerar_amostras_fiscais.py): upload, job, apurações no
 * modelo único com origem, detalhe, saldos fiscais, fórmulas, conferência ECD × ECF, retificação e isolamento.
 */
@TesteIntegracao
class ImportacaoFiscalTest {

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired DocumentoFiscalRepository documentoRepository;
    @Autowired JdbcTemplate jdbc;

    private Usuario consultor;
    private RequestPostProcessor sessao;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        String sufixo = UUID.randomUUID().toString();
        consultor = dados.usuario(dados.escritorio("Fiscal " + sufixo), "fiscal-" + sufixo + "@test.com", Papel.CONSULTOR);
        sessao = dados.sessao(consultor);
        cliente = dados.cliente(consultor, "11222333000181");
    }

    @Test
    void ecfLucroRealComEstimativaAjusteAnualELalurEPrejuizoCompensado() throws Exception {
        long id = enviar("ecf-real-2021.txt");
        aguardarStatus(id, DocumentoStatus.PROCESSADO);
        mvc.perform(get("/api/documentos/{d}/status", id).with(sessao)).andExpect(jsonPath("$.tipo").value("ECF"));

        String json = mvc.perform(get("/api/clientes/{c}/apuracoes", cliente.getId()).param("tributo", "IRPJ").with(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].periodicidade").value("MENSAL"))
                .andExpect(jsonPath("$[0].descricao").value("Estimativa mensal"))
                .andExpect(jsonPath("$[0].valorARecolher").value(45.00))
                .andExpect(jsonPath("$[1].periodicidade").value("ANUAL"))
                .andExpect(jsonPath("$[1].regime").value("LUCRO_REAL"))
                .andExpect(jsonPath("$[1].baseCalculo").value(3500.00))
                .andExpect(jsonPath("$[1].valorApurado").value(525.00))
                .andExpect(jsonPath("$[1].deducoes").value(45.00))
                .andExpect(jsonPath("$[1].valorARecolher").value(480.00))
                .andExpect(jsonPath("$[1].origem.baseCalculo.registro").value("N630"))
                .andExpect(jsonPath("$[1].arquivo").value("ecf-real-2021.txt"))
                .andReturn().getResponse().getContentAsString();
        long anual = ((Number) JsonPath.read(json, "$[1].id")).longValue();
        int linhaBase = JsonPath.read(json, "$[1].origem.baseCalculo.linha");
        assertThat(linhaDoArquivo("ecf-real-2021.txt", linhaBase)).startsWith("|N630|1|");

        // demonstração do lucro real: Parte A do e-Lalur (lucro, adição, compensação) e depois o cálculo
        mvc.perform(get("/api/clientes/{c}/apuracoes/{a}/detalhe", cliente.getId(), anual).with(sessao))
                .andExpect(jsonPath("$.linhas[0].grupo").value("LUCRO"))
                .andExpect(jsonPath("$.linhas[0].valor").value(4500.00))
                .andExpect(jsonPath("$.linhas[?(@.codigo == '6')].grupo").value("ADICAO"))
                .andExpect(jsonPath("$.linhas[?(@.codigo == '173')].grupo").value("COMPENSACAO"))
                .andExpect(jsonPath("$.linhas[?(@.codigo == '173')].valor").value(1500.00))
                .andExpect(jsonPath("$.linhas[?(@.registro == 'N630' && @.codigo == '26')].valor").value(480.00));
        mvc.perform(get("/api/clientes/{c}/apuracoes", cliente.getId()).param("tributo", "CSLL").with(sessao))
                .andExpect(jsonPath("$[1].valorARecolher").value(288.00));
        mvc.perform(get("/api/clientes/{c}/saldos-fiscais", cliente.getId()).with(sessao))
                .andExpect(jsonPath("$[*].tipo", containsInAnyOrder("BASE_NEGATIVA_CSLL", "PREJUIZO_FISCAL")))
                .andExpect(jsonPath("$[?(@.tipo == 'PREJUIZO_FISCAL')].saldoFinal").value(8500.00))
                .andExpect(jsonPath("$[?(@.tipo == 'PREJUIZO_FISCAL')].registro").value("M500"));
    }

    @Test
    void ecfLucroPresumidoTrimestralEConferenciaComAEcdSemReferencial() throws Exception {
        long ecd = enviar("ecd-2024-l9.txt"); // 2024: sem tabela referencial, confere pelo resultado líquido (I355)
        aguardarStatus(ecd, DocumentoStatus.PROCESSADO_COM_ALERTAS);
        long ecf = enviar("ecf-presumido-2024.txt");
        aguardarStatus(ecf, DocumentoStatus.PROCESSADO);
        mvc.perform(get("/api/clientes/{c}/apuracoes", cliente.getId()).param("tributo", "IRPJ").with(sessao))
                .andExpect(jsonPath("$[*].valorARecolher", containsInAnyOrder(1200.00, 3600.00)))
                .andExpect(jsonPath("$[0].regime").value("LUCRO_PRESUMIDO"))
                .andExpect(jsonPath("$[0].periodicidade").value("TRIMESTRAL"))
                .andExpect(jsonPath("$[0].descricao").value("1º trimestre"));
        mvc.perform(get("/api/clientes/{c}/apuracoes", cliente.getId()).param("tributo", "CSLL")
                        .param("de", "2024-04-01").param("ate", "2024-06-30").with(sessao))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].valorARecolher").value(3240.00));
    }

    @Test
    void conferenciaEcdEcfApontaADiferencaComAsDuasOrigensEmQualquerOrdem() throws Exception {
        long ecf = enviar("ecf-real-2021-receita-diverge.txt");
        aguardarStatus(ecf, DocumentoStatus.PROCESSADO);
        long ecd = enviar("ecd-minima-l9.txt"); // a ECD chega depois: o alerta vai para ela
        aguardarStatus(ecd, DocumentoStatus.PROCESSADO_COM_ALERTAS);
        assertThat(regras(ecd)).containsExactly("CONFERENCIA_ECD_ECF");
        mvc.perform(get("/api/clientes/{c}/apuracoes/alertas", cliente.getId()).with(sessao))
                .andExpect(jsonPath("$[0].regra").value("CONFERENCIA_ECD_ECF"))
                .andExpect(jsonPath("$[0].mensagem").value(containsString("Receita bruta de 2021: ECD -8000.00 (ecd-minima-l9.txt")))
                .andExpect(jsonPath("$[0].mensagem").value(containsString("ECF -8500.00 (ecf-real-2021-receita-diverge.txt, L300 linha(s)")));

        long igual = enviar("ecf-real-2021.txt"); // ECF que bate com a ECD: sem alerta de conferência; substitui a anterior
        aguardarStatus(igual, DocumentoStatus.PROCESSADO_COM_ALERTAS);
        assertThat(regras(igual)).containsExactly("RETIFICACAO");
    }

    @Test
    void baseDoLucroRealDiferenteDoElalurViraAlerta() throws Exception {
        long id = enviar("ecf-real-2021-base-diverge.txt");
        aguardarStatus(id, DocumentoStatus.PROCESSADO_COM_ALERTAS);
        assertThat(regras(id)).containsExactly("BASE_DIFERE_ELALUR");
    }

    @Test
    void retificadoraSubstituiASemDuplicarAsApuracoes() throws Exception {
        long original = enviar("ecf-real-2021.txt");
        aguardarStatus(original, DocumentoStatus.PROCESSADO);
        long retificadora = enviar("ecf-real-2021-retificadora.txt");
        aguardarStatus(retificadora, DocumentoStatus.PROCESSADO);
        assertThat(regras(retificadora)).containsExactly("RETIFICACAO");
        mvc.perform(get("/api/clientes/{c}/apuracoes", cliente.getId()).param("tributo", "IRPJ").with(sessao))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].arquivo", containsInAnyOrder("ecf-real-2021-retificadora.txt", "ecf-real-2021-retificadora.txt")));
    }

    @Test
    void efdContribuicoesNaoCumulativaComCreditosEControle() throws Exception {
        long id = enviar("efd-contribuicoes-nc-2025-01.txt");
        aguardarStatus(id, DocumentoStatus.PROCESSADO);
        String json = mvc.perform(get("/api/clientes/{c}/apuracoes", cliente.getId()).param("tributo", "PIS").with(sessao))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].regime").value("NAO_CUMULATIVO"))
                .andExpect(jsonPath("$[0].baseCalculo").value(100000.00))
                .andExpect(jsonPath("$[0].valorApurado").value(1650.00))
                .andExpect(jsonPath("$[0].deducoes").value(825.00))
                .andExpect(jsonPath("$[0].valorARecolher").value(825.00))
                .andExpect(jsonPath("$[0].origem.valorARecolher.registro").value("M200"))
                .andReturn().getResponse().getContentAsString();
        long pis = ((Number) JsonPath.read(json, "$[0].id")).longValue();
        mvc.perform(get("/api/clientes/{c}/apuracoes/{a}/detalhe", cliente.getId(), pis).with(sessao))
                .andExpect(jsonPath("$.linhas[?(@.grupo == 'CREDITO')].codigo").value("101"))
                .andExpect(jsonPath("$.linhas[?(@.grupo == 'CREDITO')].valor").value(825.00));
        mvc.perform(get("/api/clientes/{c}/apuracoes", cliente.getId()).param("tributo", "COFINS").with(sessao))
                .andExpect(jsonPath("$[0].valorARecolher").value(3800.00));
        mvc.perform(get("/api/clientes/{c}/saldos-fiscais", cliente.getId()).with(sessao))
                .andExpect(jsonPath("$[?(@.tipo == 'CREDITO_PIS')].saldoFinal").value(1000.00))
                .andExpect(jsonPath("$[?(@.tipo == 'CREDITO_COFINS')].saldoFinal").value(4000.00));
    }

    @Test
    void efdContribuicoesCumulativa() throws Exception {
        long id = enviar("efd-contribuicoes-cum-2025-01.txt");
        aguardarStatus(id, DocumentoStatus.PROCESSADO);
        mvc.perform(get("/api/clientes/{c}/apuracoes", cliente.getId()).with(sessao))
                .andExpect(jsonPath("$[?(@.tributo == 'PIS')].regime").value("CUMULATIVO"))
                .andExpect(jsonPath("$[?(@.tributo == 'PIS')].valorARecolher").value(650.00))
                .andExpect(jsonPath("$[?(@.tributo == 'COFINS')].valorARecolher").value(3000.00));
    }

    @Test
    void formulaDoM200QueNaoFechaEContagemDeRegistroPulado() throws Exception {
        long formula = enviar("efd-contribuicoes-formula-2025-01.txt");
        aguardarStatus(formula, DocumentoStatus.PROCESSADO_COM_ALERTAS);
        assertThat(regras(formula)).contains("FORMULA_M200");
        // C170 não é lido (bloco de documentos), mas a contagem do 9900 confere todas as linhas
        long contagem = enviar("efd-contribuicoes-contagem-errada.txt");
        aguardarStatus(contagem, DocumentoStatus.PROCESSADO_COM_ALERTAS);
        mvc.perform(get("/api/documentos/{d}/inconsistencias", contagem).with(sessao))
                .andExpect(jsonPath("$[0].regra").value("CONTAGEM_9900"))
                .andExpect(jsonPath("$[0].mensagem").value(containsString("3 registro(s) C170, mas o arquivo tem 1")));
    }

    @Test
    void efdIcmsIpiProprioStDifalFcpIpiComAjustesEObrigacoes() throws Exception {
        long id = enviar("efd-icms-ipi-2025-01.txt");
        aguardarStatus(id, DocumentoStatus.PROCESSADO);
        mvc.perform(get("/api/documentos/{d}/status", id).with(sessao)).andExpect(jsonPath("$.tipo").value("EFD_ICMS_IPI"));
        String json = mvc.perform(get("/api/clientes/{c}/apuracoes", cliente.getId()).with(sessao))
                .andExpect(jsonPath("$[*].tributo", containsInAnyOrder("ICMS", "ICMS_ST", "ICMS_DIFAL", "ICMS_FCP", "IPI")))
                .andExpect(jsonPath("$[?(@.tributo == 'ICMS')].uf").value("PR"))
                .andExpect(jsonPath("$[?(@.tributo == 'ICMS')].valorApurado").value(18500.00))
                .andExpect(jsonPath("$[?(@.tributo == 'ICMS')].deducoes").value(13500.00))
                .andExpect(jsonPath("$[?(@.tributo == 'ICMS')].valorARecolher").value(5000.00))
                .andExpect(jsonPath("$[?(@.tributo == 'ICMS_ST')].uf").value("SP"))
                .andExpect(jsonPath("$[?(@.tributo == 'ICMS_ST')].valorARecolher").value(3000.00))
                .andExpect(jsonPath("$[?(@.tributo == 'ICMS_DIFAL')].valorARecolher").value(400.00))
                .andExpect(jsonPath("$[?(@.tributo == 'ICMS_FCP')].valorARecolher").value(80.00))
                .andExpect(jsonPath("$[?(@.tributo == 'IPI')].esfera").value("FEDERAL"))
                .andExpect(jsonPath("$[?(@.tributo == 'IPI')].valorARecolher").value(600.00))
                .andReturn().getResponse().getContentAsString();
        List<Number> icms = JsonPath.read(json, "$[?(@.tributo == 'ICMS')].id");
        mvc.perform(get("/api/clientes/{c}/apuracoes/{a}/detalhe", cliente.getId(), icms.get(0).longValue()).with(sessao))
                .andExpect(jsonPath("$.linhas[?(@.grupo == 'AJUSTE')].codigo", containsInAnyOrder("PR000001", "PR020001", "PR040001")))
                .andExpect(jsonPath("$.obrigacoes[0].codigoReceita").value("1015"))
                .andExpect(jsonPath("$.obrigacoes[0].vencimento").value("2025-02-10"))
                .andExpect(jsonPath("$.obrigacoes[0].valor").value(5000.00))
                .andExpect(jsonPath("$.obrigacoes[0].registro").value("E116"));
        List<Number> fcp = JsonPath.read(json, "$[?(@.tributo == 'ICMS_FCP')].id");
        mvc.perform(get("/api/clientes/{c}/apuracoes/{a}/detalhe", cliente.getId(), fcp.get(0).longValue()).with(sessao))
                .andExpect(jsonPath("$.obrigacoes[0].codigoObrigacao").value("006"));
    }

    @Test
    void efdIcmsIpiSaldoCredorFormulaERetificacao() throws Exception {
        long credor = enviar("efd-icms-ipi-saldo-credor-2025-02.txt");
        aguardarStatus(credor, DocumentoStatus.PROCESSADO);
        mvc.perform(get("/api/clientes/{c}/saldos-fiscais", cliente.getId()).with(sessao))
                .andExpect(jsonPath("$[?(@.tipo == 'SALDO_CREDOR_ICMS')].saldoFinal").value(2000.00))
                .andExpect(jsonPath("$[?(@.tipo == 'SALDO_CREDOR_IPI')].saldoFinal").value(500.00));

        long formula = enviar("efd-icms-ipi-formula-2025-01.txt");
        aguardarStatus(formula, DocumentoStatus.PROCESSADO_COM_ALERTAS);
        assertThat(regras(formula)).containsExactly("FORMULA_E110");
        mvc.perform(get("/api/clientes/{c}/apuracoes/alertas", cliente.getId()).with(sessao))
                .andExpect(jsonPath("$[*].regra", hasItem("FORMULA_E110")));

        long retificadora = enviar("efd-icms-ipi-retificadora-2025-01.txt");
        aguardarStatus(retificadora, DocumentoStatus.PROCESSADO);
        assertThat(regras(retificadora)).containsExactly("RETIFICACAO");
        assertThat(jdbc.queryForObject("SELECT situacao FROM escrituracao_contabil WHERE documento_id = ?", String.class, formula))
                .isEqualTo("SUBSTITUIDA");
    }

    @Test
    void efdDeFilialVaiParaOClienteDaMesmaRaizDeCnpj() throws Exception {
        String filial = new String(new ClassPathResource("amostras/efd-icms-ipi-2025-01.txt").getContentAsByteArray(),
                StandardCharsets.ISO_8859_1).replace("|11222333000181|", "|11222333000262|");
        long id = enviarConteudo("efd-filial.txt", filial.getBytes(StandardCharsets.ISO_8859_1));
        aguardarStatus(id, DocumentoStatus.PROCESSADO);
        assertThat(jdbc.queryForObject("SELECT cnpj FROM escrituracao_contabil WHERE documento_id = ?", String.class, id))
                .isEqualTo("11222333000262");
    }

    @Test
    void registrosDeApuracaoDemaisSaoRecusadosSemEsgotarAMemoria() throws Exception {
        String efd = new String(new ClassPathResource("amostras/efd-icms-ipi-2025-01.txt").getContentAsByteArray(),
                StandardCharsets.ISO_8859_1);
        String ajuste = "|E111|PR000001|Ajuste repetido|1,00|\r\n";
        String inchada = efd.replace("|E116|", ajuste.repeat(200_001) + "|E116|");
        long id = enviarConteudo("efd-inchada.txt", inchada.getBytes(StandardCharsets.ISO_8859_1));
        aguardarStatus(id, DocumentoStatus.ERRO);
        assertThat(regras(id)).containsExactly("REGISTROS_DEMAIS");
    }

    @Test
    void outroEscritorioNaoVeApuracoesNemSaldos() throws Exception {
        long id = enviar("efd-icms-ipi-2025-01.txt");
        aguardarStatus(id, DocumentoStatus.PROCESSADO);
        long apuracao = jdbc.queryForObject("SELECT min(id) FROM apuracao_tributo WHERE documento_id = ?", Long.class, id);
        RequestPostProcessor intruso = dados.sessao(dados.usuario(dados.escritorio("Intruso " + UUID.randomUUID()),
                "intruso-" + UUID.randomUUID() + "@test.com", Papel.ADMIN));
        mvc.perform(get("/api/clientes/{c}/apuracoes", cliente.getId()).with(intruso)).andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes/{c}/apuracoes/{a}/detalhe", cliente.getId(), apuracao).with(intruso)).andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes/{c}/saldos-fiscais", cliente.getId()).with(intruso)).andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes/{c}/apuracoes/alertas", cliente.getId()).with(intruso)).andExpect(status().isNotFound());
        // apuração de outro cliente pelo cliente certo do intruso: também 404
        Cliente doIntruso = dados.cliente(dados.usuario(dados.escritorio("Outro " + UUID.randomUUID()),
                "outro-" + UUID.randomUUID() + "@test.com", Papel.ADMIN), "11444777000161");
        mvc.perform(get("/api/clientes/{c}/apuracoes/{a}/detalhe", doIntruso.getId(), apuracao).with(sessao))
                .andExpect(status().isNotFound());
    }

    private long enviar(String amostra) throws Exception {
        return enviarConteudo(amostra, new ClassPathResource("amostras/" + amostra).getContentAsByteArray());
    }

    private long enviarConteudo(String nome, byte[] conteudo) throws Exception {
        String resposta = mvc.perform(multipart("/api/clientes/{c}/documentos", cliente.getId())
                        .file(new MockMultipartFile("arquivo", nome, "text/plain", conteudo)).with(sessao))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private void aguardarStatus(long id, DocumentoStatus esperado) {
        aguardar(() -> documentoRepository.findById(id).orElseThrow().getStatus().finalizado());
        assertThat(documentoRepository.findById(id).orElseThrow().getStatus()).as("regras: %s", regras(id)).isEqualTo(esperado);
    }

    private List<String> regras(long id) {
        return jdbc.queryForList("SELECT regra FROM inconsistencia_importacao WHERE documento_id = ? ORDER BY id", String.class, id);
    }

    private static String linhaDoArquivo(String amostra, int numero) throws Exception {
        String texto = new String(new ClassPathResource("amostras/" + amostra).getContentAsByteArray(), StandardCharsets.ISO_8859_1);
        return texto.split("\r\n")[numero - 1];
    }
}
