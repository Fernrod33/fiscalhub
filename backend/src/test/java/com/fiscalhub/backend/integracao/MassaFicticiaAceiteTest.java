package com.fiscalhub.backend.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.fiscalhub.backend.integracao.Espera.aguardar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aceite de ponta a ponta com a massa fictícia versionada (src/test/resources/massa-ficticia): importa todos os
 * arquivos da Metalúrgica Pinheiral (e a ECF da Vale do Iguaçu) pela central de importação, como o usuário, e confere
 * os valores de _gerador/valores_esperados.json e os problemas plantados do gabarito (README da massa).
 */
@TesteIntegracao
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MassaFicticiaAceiteTest {

    static final Path MASSA = Path.of("src", "test", "resources", "massa-ficticia");
    /** Ordem: a conta corrente do PR (CNPJ + CAD/ICMS) antes da relação de GR-PR, que só traz o CAD/ICMS. */
    static final List<String> ARQUIVOS = List.of(
            "ecd/ECD_2022_11222333000181_ORIGINAL.txt", "ecd/ECD_2023_11222333000181_ORIGINAL.txt",
            "ecd/ECD_2024_11222333000181_ORIGINAL.txt", "ecd/ECD_2024_11222333000181_SUBSTITUTA.txt",
            "ecd/ECD_2025_11222333000181_ORIGINAL.txt", "ecf/ECF_2025_LUCRO_REAL_11222333000181.txt",
            "ecf/ECF_2025_LUCRO_PRESUMIDO_44555666000181.txt",
            "efd-contribuicoes/EFD_CONTRIBUICOES_082026_11222333000181.txt", "efd-icms-ipi/EFD_ICMS_IPI_082026_11222333000181.txt",
            "situacao-fiscal/Relatorio_Situacao_Fiscal_11222333000181_20261014.pdf",
            "pgfn/Extrato_Inscricoes_Regularize_11222333000181.pdf", "pgfn/CAPAG_11222333000181.pdf",
            "dctf/DCTF_Mensal_112024_11222333000181.pdf", "dctf/DCTF_Mensal_112024_11222333000181.dec",
            "dctfweb/DCTFWeb_082026_Declaracao_Completa_11222333000181.pdf",
            "pagamentos/DARF_DCTFWeb_082026_PIS_COFINS_IPI.pdf", "pagamentos/DARF_DCTFWeb_082026_Previdenciario_IRRF.pdf",
            "pagamentos/DARF_Sicalc_IPI_112024_pago_em_atraso.pdf", "pagamentos/Relatorio_Consolidado_Pagamentos_11222333000181.pdf",
            "parana/Conta_Corrente_e_Divida_Ativa_ICMS_9012345663.pdf", "parana/RTI_9012345663.pdf",
            "parana/Pagamentos_ICMS_GR-PR_9012345663.pdf", "parana/GR-PR_ICMS-ST_072026.pdf",
            "parana/GR-PR_Parcelamento_parcela_12-36.pdf");

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired DocumentoFiscalRepository documentoRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private RequestPostProcessor sessao;
    private Cliente metalurgica;
    private JsonNode esperados;
    private final Map<String, Long> documentos = new LinkedHashMap<>();

    @BeforeAll
    void importarAMassa() throws Exception {
        String sufixo = UUID.randomUUID().toString();
        Usuario consultor = dados.usuario(dados.escritorio("Massa " + sufixo), "massa-" + sufixo + "@test.com", Papel.CONSULTOR);
        sessao = dados.sessao(consultor);
        metalurgica = dados.cliente(consultor, "11222333000181");
        dados.cliente(consultor, "44555666000181");
        // SELIC mensal de jan/2025 (BCB, série 4390): o único mês somado nos juros do IPI 11/2024 pago em 10/02/2025
        dados.selic(YearMonth.of(2025, 1), YearMonth.of(2025, 1), "1.01");
        esperados = json.readTree(MASSA.resolve("_gerador/valores_esperados.json").toFile());
        for (String arquivo : ARQUIVOS) {
            documentos.put(arquivo, importar(MASSA.resolve(arquivo)));
        }
    }

    @Test
    void todosOsArquivosSaoLidosSemErro() {
        documentos.forEach((arquivo, id) -> {
            assertThat(documentoRepository.findById(id).orElseThrow().getStatus()).as(arquivo)
                    .isIn(DocumentoStatus.PROCESSADO, DocumentoStatus.PROCESSADO_COM_ALERTAS);
            assertThat(jdbc.queryForList("SELECT regra FROM inconsistencia_importacao WHERE documento_id = ? AND severidade = 'ERRO'",
                    String.class, id)).as(arquivo).isEmpty();
        });
        assertThat(jdbc.queryForObject("SELECT versao_leiaute FROM escrituracao_contabil WHERE documento_id = ?", String.class,
                documentos.get("pgfn/CAPAG_11222333000181.pdf"))).isEqualTo("ficticio-2026-10");
    }

    // ---------------------------------------------------------------- valores_esperados.json

    @Test
    void resultadoDasEcdsPorAno() throws Exception {
        JsonNode dre = conteudo("/api/clientes/{c}/demonstracoes?tipo=DRE&origem=REFERENCIAL");
        for (var ano : List.of("2022", "2023", "2024", "2025")) {
            BigDecimal esperado = new BigDecimal(esperados.at("/ecd_resultado_por_ano/" + ano).asText());
            BigDecimal lido = new BigDecimal(dre.at("/linhas/0/valores/" + ano + "/valor").asText());
            assertThat(lido.negate()).as("resultado de %s (DRE referencial, linha 3; credor = lucro)", ano).isEqualByComparingTo(esperado);
        }
    }

    @Test
    void prejuizoFiscalDaEcf2025ESaldo() throws Exception {
        JsonNode saldos = conteudo("/api/clientes/{c}/saldos-fiscais");
        JsonNode prejuizo = encontrar(saldos, s -> "PREJUIZO_FISCAL".equals(s.get("tipo").asText()) && s.get("dataFim").asText().startsWith("2025"));
        assertThat(new BigDecimal(prejuizo.get("saldoInicial").asText()))
                .isEqualByComparingTo(esperados.at("/prejuizo_fiscal_saldo_inicial_2025").asText());
        assertThat(new BigDecimal(prejuizo.get("saldoFinal").asText()))
                .isEqualByComparingTo(new BigDecimal(esperados.at("/prejuizo_fiscal_saldo_inicial_2025").asText())
                        .add(new BigDecimal(esperados.at("/ecf_lucro_real_2025/pf").asText())));
    }

    @Test
    void apuradoDe082026() throws Exception {
        JsonNode apuracoes = conteudo("/api/clientes/{c}/apuracoes?de=2026-08-01&ate=2026-08-31");
        assertThat(aRecolher(apuracoes, "ICMS")).isEqualByComparingTo(esperados.at("/efd_icms/a_recolher").asText());
        assertThat(new BigDecimal(encontrar(apuracoes, a -> "ICMS".equals(a.get("tributo").asText())).get("saldoCredor").asText()))
                .isEqualByComparingTo(esperados.at("/efd_icms/transp").asText());
        assertThat(aRecolher(apuracoes, "ICMS_ST")).isEqualByComparingTo(esperados.at("/efd_icms/st").asText());
        assertThat(aRecolher(apuracoes, "IPI")).isEqualByComparingTo(esperados.at("/efd_icms/ipi_rec").asText());
        assertThat(aRecolher(apuracoes, "PIS")).isEqualByComparingTo(esperados.at("/efd_contribuicoes/PIS/devido").asText());
        assertThat(aRecolher(apuracoes, "COFINS")).isEqualByComparingTo(esperados.at("/efd_contribuicoes/COFINS/devido").asText());
    }

    @Test
    void totalDaDctfWebEPassivoPgfn() throws Exception {
        BigDecimal dctfweb = BigDecimal.ZERO;
        for (JsonNode d : conteudo("/api/clientes/{c}/declaracoes")) {
            if ("DCTFWEB".equals(d.get("declaracao").asText()) && d.get("periodo").asText().startsWith("2026-08")) {
                dctfweb = dctfweb.add(new BigDecimal(d.get("valor").asText()));
            }
        }
        assertThat(dctfweb).isEqualByComparingTo(esperados.at("/dctfweb_082026_total").asText());
        BigDecimal passivo = BigDecimal.ZERO;
        for (JsonNode d : conteudo("/api/clientes/{c}/debitos?orgao=PGFN")) {
            passivo = passivo.add(new BigDecimal(d.get("valorTotal").asText()));
        }
        assertThat(passivo).isEqualByComparingTo(esperados.at("/pgfn_passivo_total").asText());
    }

    // ---------------------------------------------------------------- gabarito (README da massa)

    @Test
    void item1CofinsPagaAMenorComODebitoNaSituacaoFiscal() throws Exception {
        JsonNode item = conteudo("/api/clientes/{c}/conciliacao/COFINS-2026-08");
        assertThat(item.get("status").asText()).isEqualTo("PAGO_A_MENOR");
        assertThat(new BigDecimal(item.get("diferenca").asText())).isEqualByComparingTo("5079.00");
        assertThat(new BigDecimal(item.at("/apurado/valor").asText())).isEqualByComparingTo("33079.00");
        assertThat(new BigDecimal(item.at("/declarado/valor").asText())).isEqualByComparingTo("33079.00");
        assertThat(new BigDecimal(item.at("/pago/valor").asText())).isEqualByComparingTo("28000.00");
        assertThat(item.at("/debitos/0/orgao").asText()).isEqualTo("RFB");
        assertThat(new BigDecimal(item.at("/debitos/0/valorTotal").asText())).isEqualByComparingTo("5448.24");
        // cada valor aponta o arquivo e a página/registro e linha
        assertThat(item.at("/pago/origens/0/arquivo").asText()).isEqualTo("DARF_DCTFWeb_082026_PIS_COFINS_IPI.pdf");
        assertThat(item.at("/pago/origens/0/pagina").asInt()).isEqualTo(1);
        assertThat(item.at("/pago/origens/0/linha").asInt()).isEqualTo(13);
        assertThat(item.at("/apurado/origens/0/registro").asText()).startsWith("M6");
    }

    @Test
    void item2IpiPagoEmAtrasoSemAcrescimos() throws Exception {
        JsonNode item = conteudo("/api/clientes/{c}/conciliacao/IPI-2024-11");
        assertThat(item.get("status").asText()).isEqualTo("PAGO_EM_ATRASO_SEM_ACRESCIMOS");
        assertThat(item.at("/acrescimos/diasAtraso").asInt()).isEqualTo(48); // 24/12/2024 -> 10/02/2025
        assertThat(item.at("/debitos/0/orgao").asText()).isEqualTo("RFB");
        BigDecimal multa = new BigDecimal(item.at("/acrescimos/multa").asText());
        BigDecimal juros = new BigDecimal(item.at("/acrescimos/juros").asText());
        // art. 61 da Lei 9.430/96, igual ao relatório de situação fiscal da massa: 0,33% × 48 dias = 15,84% (6.526,08);
        // SELIC jan/2025 (1,01%) + 1% no mês do pagamento = 2,01% (828,12)
        assertThat(multa).isEqualByComparingTo(esperados.at("/situacao_fiscal/multa_ipi").asText());
        assertThat(juros).isEqualByComparingTo(esperados.at("/situacao_fiscal/juros_ipi").asText());
    }

    @Test
    void item3IcmsStNaoRecolhidoMesmoComSaldoCredor() throws Exception {
        JsonNode item = conteudo("/api/clientes/{c}/conciliacao/ICMS_ST-PR-2026-08");
        assertThat(item.get("status").asText()).isEqualTo("NAO_PAGO");
        assertThat(new BigDecimal(item.get("diferenca").asText())).isEqualByComparingTo("44850.00");
        assertThat(item.at("/apurado/origens/0/arquivo").asText()).isEqualTo("EFD_ICMS_IPI_082026_11222333000181.txt");
        assertThat(item.at("/debitos/0/orgao").asText()).isEqualTo("SEFA_PR");
        assertThat(item.at("/debitos/0/situacao").asText()).isEqualTo("EM_ABERTO");
        assertThat(conteudo("/api/clientes/{c}/conciliacao/ICMS-PR-2026-08").get("status").asText()).isEqualTo("CONCILIADO");
        assertThat(conteudo("/api/clientes/{c}/conciliacao/ICMS_ST-PR-2026-07").get("status").asText()).isEqualTo("CONCILIADO");
    }

    @Test
    void item4SaldosCredoresMensaisDisponiveis() throws Exception {
        List<BigDecimal> rti = new ArrayList<>();
        for (JsonNode s : conteudo("/api/clientes/{c}/saldos-fiscais")) {
            if ("SALDO_CREDOR_ICMS".equals(s.get("tipo").asText()) && s.get("arquivo").asText().startsWith("RTI")) {
                rti.add(new BigDecimal(s.get("saldoFinal").asText()));
            }
        }
        assertThat(rti).usingElementComparator(BigDecimal::compareTo).containsExactly(new BigDecimal("402115.40"),
                new BigDecimal("431980.12"), new BigDecimal("455320.77"), new BigDecimal("462011.30"),
                new BigDecimal("480000.00"), new BigDecimal("365250.00"));
    }

    @Test
    void item5OmissaoDaEfdContribuicoes() throws Exception {
        JsonNode p = encontrar(conteudo("/api/clientes/{c}/pendencias"), x -> "OMISSAO_DECLARACAO".equals(x.get("tipo").asText()));
        assertThat(p.get("periodo").asText()).isEqualTo("2026-06-01");
        assertThat(p.get("descricao").asText()).contains("EFD-Contribuições");
        assertThat(p.at("/origem/arquivo").asText()).startsWith("Relatorio_Situacao_Fiscal");
    }

    @Test
    void item6InscricaoNaoNegociadaImpedeCertidaoEDeduplicada() throws Exception {
        JsonNode d = encontrar(conteudo("/api/clientes/{c}/debitos"), x -> "90 3 24 002201-55".equals(x.get("numeroInscricao").asText()));
        assertThat(d.get("situacao").asText()).isEqualTo("INSCRITO_DIVIDA_ATIVA");
        assertThat(d.get("impedeCertidao").asBoolean()).isTrue();
        assertThat(d.get("negociacao").isNull()).isTrue();
        assertThat(d.get("tributo").asText()).isEqualTo("IPI");
        assertThat(new BigDecimal(d.get("valorTotal").asText())).isEqualByComparingTo("64176.14");
        assertThat(d.get("origens")).hasSize(2); // relatório de situação fiscal e extrato do Regularize: um débito
        assertThat(d.get("divergente").asBoolean()).isFalse();
        long impeditivos = 0;
        for (JsonNode x : conteudo("/api/clientes/{c}/debitos?orgao=PGFN")) {
            impeditivos += x.get("impedeCertidao").asBoolean() ? 1 : 0;
        }
        assertThat(impeditivos).isEqualTo(1); // as outras três inscrições estão na transação
    }

    @Test
    void item7CapagClasseCConsistenteSemAlerta() throws Exception {
        JsonNode c = conteudo("/api/clientes/{c}/capacidade-oficial").get(0);
        assertThat(c.get("classe").asText()).isEqualTo("C");
        assertThat(new BigDecimal(c.get("razao").asText())).isEqualByComparingTo("0.78");
        assertThat(c.get("classeConsistente").asBoolean()).isTrue();
        assertThat(jdbc.queryForList("SELECT regra FROM inconsistencia_importacao WHERE documento_id = ?", String.class,
                documentos.get("pgfn/CAPAG_11222333000181.pdf"))).doesNotContain("CAPAG_INCONSISTENTE");
    }

    @Test
    void item8Ecd2024SubstitutaPrevalece() {
        assertThat(jdbc.queryForObject("SELECT situacao FROM escrituracao_contabil WHERE documento_id = ?", String.class,
                documentos.get("ecd/ECD_2024_11222333000181_SUBSTITUTA.txt"))).isEqualTo("VIGENTE");
        assertThat(jdbc.queryForObject("SELECT situacao FROM escrituracao_contabil WHERE documento_id = ?", String.class,
                documentos.get("ecd/ECD_2024_11222333000181_ORIGINAL.txt"))).isEqualTo("SUBSTITUIDA");
    }

    @Test
    void item9AdicaoDaMultaDeOficioNoElalur() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM detalhe_apuracao d JOIN apuracao_tributo a ON a.id = d.apuracao_id
                WHERE a.documento_id = ? AND d.grupo = 'ADICAO' AND d.valor = 18450.00""", Long.class,
                documentos.get("ecf/ECF_2025_LUCRO_REAL_11222333000181.txt"))).isPositive();
    }

    @Test
    void item10DividaAtivaEstadualParceladaEmDia() throws Exception {
        JsonNode p = encontrar(conteudo("/api/clientes/{c}/parcelamentos"), x -> "PR-2025-0045871".equals(x.get("numero").asText()));
        assertThat(p.get("parcelasPagas").asInt()).isEqualTo(12);
        assertThat(p.get("parcelasTotal").asInt()).isEqualTo(36);
        assertThat(p.get("situacao").asText()).isEqualTo("EM_DIA");
        JsonNode cda = encontrar(conteudo("/api/clientes/{c}/debitos?orgao=PGE_PR"), x -> "2024.0012345-8".equals(x.get("numeroInscricao").asText()));
        assertThat(cda.get("situacao").asText()).isEqualTo("PARCELADO");
        assertThat(cda.get("impedeCertidao").asBoolean()).isFalse();
    }

    // ---------------------------------------------------------------- deduplicação e conferências entre fontes

    @Test
    void conciliacaoTemSoAsTresDiferencasPlantadasESemAlertas() throws Exception {
        JsonNode c = conteudo("/api/clientes/{c}/conciliacao");
        List<String> diferencas = new ArrayList<>();
        c.get("itens").forEach(i -> {
            if (!"CONCILIADO".equals(i.get("status").asText())) {
                diferencas.add(i.get("id").asText() + " " + i.get("status").asText());
            }
        });
        assertThat(diferencas).containsExactlyInAnyOrder("COFINS-2026-08 PAGO_A_MENOR", "ICMS_ST-PR-2026-08 NAO_PAGO",
                "IPI-2024-11 PAGO_EM_ATRASO_SEM_ACRESCIMOS");
        assertThat(c.get("alertas")).isEmpty(); // toda diferença tem o débito e todo débito em aberto tem a diferença
        assertThat(conteudo("/api/clientes/{c}/conciliacao?status=PAGO_A_MENOR&de=2026-08&ate=2026-08").get("itens")).hasSize(1);
    }

    @Test
    void mesmoPagamentoEmDuasFontesEhUmSoEADctfEmDoisFormatosConfere() throws Exception {
        List<JsonNode> darf = new ArrayList<>();
        for (JsonNode p : conteudo("/api/clientes/{c}/pagamentos")) {
            if ("07.18.26263.7654321-0".equals(p.get("numero").asText())) {
                darf.add(p);
            }
        }
        assertThat(darf).hasSize(3); // PIS, COFINS e IPI do DARF; a linha do relatório consolidado é a segunda origem
        assertThat(darf).allSatisfy(p -> assertThat(p.get("origens")).hasSize(2));
        assertThat(jdbc.queryForList("SELECT regra FROM inconsistencia_importacao WHERE documento_id = ?", String.class,
                documentos.get("dctf/DCTF_Mensal_112024_11222333000181.dec"))).contains("DECLARACAO_EM_DOIS_FORMATOS");
        JsonNode parcelas = conteudo("/api/clientes/{c}/conciliacao");
        assertThat(parcelas.at("/itens").findValuesAsText("id")).doesNotContain("PARCELAMENTO");
    }

    // ---------------------------------------------------------------- apoio

    private long importar(Path arquivo) throws Exception {
        String nome = arquivo.getFileName().toString();
        String tipo = nome.endsWith(".pdf") ? "application/pdf" : nome.endsWith(".dec") ? "application/octet-stream" : "text/plain";
        String resposta = mvc.perform(multipart("/api/documentos").file(new MockMultipartFile("arquivo", nome, tipo,
                        Files.readAllBytes(arquivo))).with(sessao))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(resposta, "$.id")).longValue();
        aguardar(() -> documentoRepository.findById(id).orElseThrow().getStatus().finalizado()
                && jdbc.queryForObject("SELECT count(*) FROM job_processamento WHERE documento_id = ? AND status IN ('PENDENTE', 'PROCESSANDO')",
                Long.class, id) == 0, 120_000);
        return id;
    }

    private JsonNode conteudo(String url) throws Exception {
        return json.readTree(mvc.perform(get(url, metalurgica.getId()).with(sessao)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static JsonNode encontrar(JsonNode lista, java.util.function.Predicate<JsonNode> condicao) {
        for (JsonNode n : lista) {
            if (condicao.test(n)) {
                return n;
            }
        }
        throw new AssertionError("Nenhum item atende à condição em " + lista);
    }

    private static BigDecimal aRecolher(JsonNode apuracoes, String tributo) {
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode a : apuracoes) {
            if (tributo.equals(a.get("tributo").asText())) {
                total = total.add(new BigDecimal(a.get("valorARecolher").asText()));
            }
        }
        return total;
    }
}
