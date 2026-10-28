package com.fiscalhub.backend.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.pdf.DocumentoPdf;
import com.fiscalhub.backend.pdf.LeitorPdf;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.HexFormat;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    /** Exercício completo de 2025 (coerente com a ECD 2025) e EFD ICMS/IPI de 01 a 07/2026 (faturamento de 12 meses do PR). */
    static final List<String> ARQUIVOS_FASE_4 = java.util.stream.Stream.concat(
            java.util.stream.IntStream.rangeClosed(1, 12).boxed().flatMap(m -> java.util.stream.Stream.of(
                    String.format("efd-icms-ipi/EFD_ICMS_IPI_%02d2025_11222333000181.txt", m),
                    String.format("efd-contribuicoes/EFD_CONTRIBUICOES_%02d2025_11222333000181.txt", m))),
            java.util.stream.IntStream.rangeClosed(1, 7).mapToObj(m -> String.format("efd-icms-ipi/EFD_ICMS_IPI_%02d2026_11222333000181.txt", m)))
            .toList();

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
        for (String arquivo : java.util.stream.Stream.concat(ARQUIVOS.stream(), ARQUIVOS_FASE_4.stream()).toList()) {
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
        JsonNode c = encontrar(conteudo("/api/clientes/{c}/capacidade-oficial"), x -> "PGFN".equals(x.get("fonte").asText()));
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
            // 2025: a massa traz as EFD (apurado) e as GR-PR do ICMS, mas não a DCTFWeb nem os DARF do ano
            boolean federal2025 = i.get("periodo").asText().startsWith("2025") && "FEDERAL".equals(i.get("esfera").asText());
            if (federal2025) {
                // IRPJ e CSLL de 2025 têm apurado zero (prejuízo): conciliados
                assertThat(i.get("status").asText()).as(i.get("id").asText()).isIn("NAO_DECLARADO", "CONCILIADO");
            } else if (!"CONCILIADO".equals(i.get("status").asText())) {
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

    // ---------------------------------------------------------------- relatórios (Fase 3) com a massa

    /** Os três relatórios montados com a massa: valores contra valores_esperados.json e a conciliação; PDF, Excel e emissão. */
    @Nested
    class Relatorios {

        @Test
        void tributosTrazADivergenciaDaCofinsComOrigemEOsMesmosValoresDaConciliacao() throws Exception {
            JsonNode r = emitir("{\"tipo\":\"TRIBUTOS\",\"de\":\"2024-11\",\"ate\":\"2026-08\"}");
            JsonNode m = snapshot(r);
            JsonNode div = linha(secao(m, "Divergências"), "COFINS", "2026-08");
            assertThat(new BigDecimal(div.get(3).asText())).isEqualByComparingTo("5079.00");
            assertThat(div.get(7).asText()).contains("EFD_CONTRIBUICOES_082026_11222333000181.txt").contains("DARF_DCTFWeb_082026_PIS_COFINS_IPI.pdf, p. 1");
            JsonNode ipi = linha(secao(m, "Divergências"), "IPI", "2024-11");
            assertThat(new BigDecimal(ipi.get(4).asText())).isEqualByComparingTo(esperados.at("/situacao_fiscal/multa_ipi").asText());
            assertThat(new BigDecimal(ipi.get(5).asText())).isEqualByComparingTo(esperados.at("/situacao_fiscal/juros_ipi").asText());
            JsonNode cofins = linha(secao(m, "Tributos por competência"), "Federal", "COFINS", "2026-08");
            assertThat(new BigDecimal(cofins.get(3).asText())).isEqualByComparingTo(esperados.at("/efd_contribuicoes/COFINS/devido").asText());
            JsonNode pis = linha(secao(m, "Tributos por competência"), "Federal", "PIS", "2026-08");
            assertThat(new BigDecimal(pis.get(3).asText())).isEqualByComparingTo(esperados.at("/efd_contribuicoes/PIS/devido").asText());
            // totais = soma da conciliação do mesmo período (nada recalculado)
            BigDecimal apurado = BigDecimal.ZERO;
            int divergentes = 0;
            for (JsonNode i : conteudo("/api/clientes/{c}/conciliacao?de=2024-11&ate=2026-08").get("itens")) {
                apurado = apurado.add(i.hasNonNull("apurado") ? new BigDecimal(i.at("/apurado/valor").asText()) : BigDecimal.ZERO);
                divergentes += java.util.Set.of("CONCILIADO", "PARCELADO", "COMPENSADO_OU_SUSPENSO").contains(i.get("status").asText()) ? 0 : 1;
            }
            assertThat(new BigDecimal(m.at("/sumario/0/valor").asText())).isEqualByComparingTo(apurado);
            // as três plantadas + os federais de 2025 sem DCTFWeb/DARF na massa (não declarados)
            assertThat(m.at("/sumario/3/valor").asText()).isEqualTo(String.valueOf(divergentes));
            assertThat(m.get("fontes")).isNotEmpty();
            assertThat(m.get("notas").toString()).contains("Lei 9.430/96, art. 61");
        }

        @Test
        void pdfAbreComOsNumerosChaveEDoisDownloadsSaoOMesmoArquivo() throws Exception {
            JsonNode r = emitir("{\"tipo\":\"TRIBUTOS\",\"de\":\"2024-11\",\"ate\":\"2026-08\"}");
            String url = "/api/clientes/{c}/relatorios/" + r.get("id").asLong() + "/pdf";
            byte[] pdf = baixar(url);
            assertThat(sha256(baixar(url))).isEqualTo(sha256(pdf)).isEqualTo(r.get("sha256Pdf").asText());
            DocumentoPdf lido = LeitorPdf.ler(pdf);
            String texto = String.join("\n", lido.linhas().stream().map(DocumentoPdf.Linha::texto).toList());
            int paginas = lido.paginas().size();
            assertThat(paginas).isGreaterThanOrEqualTo(3); // capa, corpo e anexo de fontes
            assertThat(texto).contains("Página 2 de " + paginas, "Página " + paginas + " de " + paginas)
                    .contains("Relatório nº " + r.get("numero").asLong()).contains("11.222.333/0001-81").contains("5.079,00")
                    .contains("6.526,08").contains("828,12").contains("Notas metodológicas").contains("Anexo");
        }

        @Test
        void excelTemNumerosComoNumeroCabecalhoCongeladoEAbaFontes() throws Exception {
            JsonNode r = emitir("{\"tipo\":\"TRIBUTOS\",\"de\":\"2026-08\",\"ate\":\"2026-08\"}");
            byte[] xlsx = baixar("/api/clientes/{c}/relatorios/" + r.get("id").asLong() + "/xlsx");
            assertThat(sha256(xlsx)).isEqualTo(r.get("sha256Xlsx").asText());
            try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
                Sheet tabela = wb.getSheet("Tributos por competência");
                assertThat(tabela.getPaneInformation().isFreezePane()).isTrue();
                assertThat(tabela.getRow(0).getCell(3).getStringCellValue()).isEqualTo("Apurado");
                boolean achou = false;
                for (int i = 1; i <= tabela.getLastRowNum(); i++) {
                    var row = tabela.getRow(i);
                    if ("COFINS".equals(row.getCell(1).getStringCellValue())) {
                        assertThat(row.getCell(6).getCellType()).isEqualTo(CellType.NUMERIC);
                        assertThat(row.getCell(6).getNumericCellValue()).isEqualTo(5079.00);
                        assertThat(row.getCell(6).getCellStyle().getDataFormatString()).contains("R$");
                        achou = true;
                    }
                }
                assertThat(achou).isTrue();
                Sheet fontes = wb.getSheet("Fontes");
                assertThat(fontes.getLastRowNum()).isPositive();
                assertThat(fontes.getRow(1).getCell(4).getStringCellValue()).hasSize(64);
            }
        }

        @Test
        void reemitirGeraOutroNumeroEORegistroEhImutavel() throws Exception {
            String pedido = "{\"tipo\":\"SITUACAO_FISCAL\"}";
            JsonNode a = emitir(pedido);
            JsonNode b = emitir(pedido);
            assertThat(b.get("numero").asLong()).isEqualTo(a.get("numero").asLong() + 1);
            assertThat(b.get("id").asLong()).isNotEqualTo(a.get("id").asLong());
            assertThat(sha256(baixar("/api/clientes/{c}/relatorios/" + a.get("id").asLong() + "/pdf"))).isEqualTo(a.get("sha256Pdf").asText());
            assertThatThrownBy(() -> jdbc.update("UPDATE relatorio_emitido SET tipo = 'TRIBUTOS' WHERE id = ?", a.get("id").asLong()))
                    .hasMessageContaining("imutável");
            JsonNode lista = conteudo("/api/clientes/{c}/relatorios");
            assertThat(lista.findValuesAsText("numero")).contains(a.get("numero").asText(), b.get("numero").asText());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_evento WHERE acao = 'RELATORIO_BAIXAR' AND recurso_id = ?",
                    Long.class, a.get("id").asLong())).isPositive();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_evento WHERE acao = 'RELATORIO_EMITIR' AND recurso_id = ?",
                    Long.class, b.get("id").asLong())).isEqualTo(1);
        }

        @Test
        void situacaoFiscalDestacaOQueImpedeCertidaoEBateComOsDebitos() throws Exception {
            JsonNode m = snapshot(emitir("{\"tipo\":\"SITUACAO_FISCAL\"}"));
            BigDecimal total = BigDecimal.ZERO;
            for (JsonNode d : conteudo("/api/clientes/{c}/debitos")) {
                total = d.hasNonNull("valorTotal") ? total.add(new BigDecimal(d.get("valorTotal").asText())) : total;
            }
            assertThat(new BigDecimal(m.at("/sumario/0/valor").asText())).isEqualByComparingTo(total);
            JsonNode impede = secao(m, "Itens que impedem a certidão");
            assertThat(impede.get("destaque").asBoolean()).isTrue();
            assertThat(impede.at("/tabela/linhas")).isNotEmpty();
            assertThat(secao(m, "Parcelamentos e transações").at("/tabela/linhas").toString()).contains("2021.009871-4", "57 de 64");
            assertThat(m.at("/sumario/4/valor").asText()).isNotEqualTo("não disponível"); // CAPAG da massa
        }

        @Test
        void carga2025BateComOGeradorEInsumoFaltanteFicaNaoDisponivel() throws Exception {
            JsonNode carga = conteudo("/api/clientes/{c}/carga-tributaria");
            JsonNode e2025 = encontrar(carga, e -> e.get("exercicio").asInt() == 2025);
            assertThat(new BigDecimal(e2025.get("receitaBruta").asText())).isEqualByComparingTo(esperados.at("/carga_tributaria_2025/receita_bruta").asText());
            assertThat(new BigDecimal(e2025.get("tributos").asText())).isEqualByComparingTo(esperados.at("/carga_tributaria_2025/tributos").asText());
            assertThat(new BigDecimal(e2025.get("carga").asText())).isEqualByComparingTo(esperados.at("/carga_tributaria_2025/carga").asText());
            assertThat(e2025.get("irpjCsllNaoAplicavel").asBoolean()).isTrue(); // prejuízo antes do IR
            JsonNode e2024 = encontrar(carga, e -> e.get("exercicio").asInt() == 2024);
            assertThat(e2024.get("carga").isNull()).isTrue(); // sem as EFD de 2024: não estima
            assertThat(e2024.get("faltando").toString()).contains("EFD-Contribuições: importe o exercício de 2024");
            JsonNode e2026 = encontrar(carga, e -> e.get("exercicio").asInt() == 2026);
            assertThat(e2026.get("receitaBruta").isNull()).isTrue();
            assertThat(e2026.get("carga").isNull()).isTrue();
            assertThat(e2026.get("faltando").toString()).contains("receita bruta de 2026: importe a ECD");
            JsonNode m = snapshot(emitir("{\"tipo\":\"CARGA_TRIBUTARIA\"}"));
            assertThat(secao(m, "Insumos que faltam").at("/tabela/linhas").toString()).contains("não disponível — receita bruta de 2026");
            assertThat(linha(secao(m, "Carga efetiva por exercício"), "2026").get(3).asText()).isEqualTo("não disponível");
        }

        private JsonNode emitir(String corpo) throws Exception {
            return json.readTree(mvc.perform(post("/api/clientes/{c}/relatorios", metalurgica.getId()).with(sessao)
                    .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        }

        private JsonNode snapshot(JsonNode emitido) throws Exception {
            return json.readTree(jdbc.queryForObject("SELECT snapshot FROM relatorio_emitido WHERE id = ?", String.class, emitido.get("id").asLong()));
        }

        private byte[] baixar(String url) throws Exception {
            var inicio = mvc.perform(get(url, metalurgica.getId()).with(sessao)).andExpect(status().isOk()).andReturn();
            return mvc.perform(asyncDispatch(inicio)).andReturn().getResponse().getContentAsByteArray(); // StreamingResponseBody
        }

        private static JsonNode secao(JsonNode m, String titulo) {
            return encontrar(m.get("secoes"), s -> titulo.equals(s.get("titulo").asText()));
        }

        /** Linha da tabela da seção cujas primeiras células são as dadas. */
        private static JsonNode linha(JsonNode secao, String... inicio) {
            return encontrar(secao.at("/tabela/linhas"), l -> {
                for (int i = 0; i < inicio.length; i++) {
                    if (!inicio[i].equals(l.get(i).asText())) {
                        return false;
                    }
                }
                return true;
            });
        }

        private static String sha256(byte[] b) throws Exception {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        }
    }

    // ---------------------------------------------------------------- capacidade de pagamento (Fase 4) com a massa

    /** Valores calculados de forma independente pelo gerador (_gerador/valores_esperados.json). */
    @Nested
    class Capacidade {

        @Test
        void dfcDiretaDe2024E2025PorCategoriaBateComOGeradorEComOCaixa() throws Exception {
            for (String ano : List.of("2024", "2025")) {
                JsonNode dfc = conteudo("/api/clientes/{c}/dfc?exercicio=" + ano);
                JsonNode esperado = esperados.at("/dfc_direta/" + ano);
                esperado.get("categorias").fields().forEachRemaining(e -> {
                    JsonNode linha = encontrar(dfc.get("linhas"), l -> e.getKey().equals(l.get("categoria").asText()));
                    assertThat(new BigDecimal(linha.get("total").asText())).as(ano + " " + e.getKey()).isEqualByComparingTo(e.getValue().asText());
                    assertThat(linha.get("origens")).isNotEmpty();
                });
                assertThat(dfc.get("linhas")).hasSize(esperado.get("categorias").size());
                assertThat(new BigDecimal(dfc.get("variacaoDfc").asText())).isEqualByComparingTo(esperado.get("variacao_caixa").asText());
                assertThat(new BigDecimal(dfc.get("diferenca").asText())).isZero(); // conferência com o I155
                assertThat(dfc.get("alerta").isNull()).isTrue();
            }
        }

        @Test
        void classificacaoDoPrBateComOGeradorEComORti() throws Exception {
            JsonNode pr = metodologia(conteudo("/api/clientes/{c}/capacidade"), "PR");
            JsonNode esperado = esperados.at("/classificacao_pr");
            assertThat(pr.get("classe").asText()).isEqualTo(esperado.get("classe").asText());
            assertThat(new BigDecimal(pr.get("valor").asText())).isEqualByComparingTo(esperado.get("soma").asText());
            List<String> nomes = List.of("Situação cadastral", "Tempo de inatividade", "Índice de endividamento", "Indicativo de pagamento",
                    "Classificação das CDAs");
            List<String> chaves = List.of("situacao", "inatividade", "endividamento", "pagamento", "cdas");
            for (int i = 0; i < nomes.size(); i++) {
                String nome = nomes.get(i);
                assertThat(encontrar(pr.get("criterios"), c -> nome.equals(c.get("nome").asText())).get("pontos").asText())
                        .as(nome).isEqualTo(esperado.at("/pontos/" + chaves.get(i)).asText());
            }
            esperado.get("cdas").fields().forEachRemaining(e -> {
                JsonNode item = encontrar(pr.get("itens"), x -> e.getKey().equals(x.get("id").asText()));
                assertThat(item.get("classe").asText()).as(e.getKey()).isEqualTo(e.getValue().get("classe").asText());
                assertThat(item.get("indice").asText()).as(e.getKey()).isEqualTo(e.getValue().get("indice").asText());
                assertThat(item.get("classeOficial").asText()).as("RTI " + e.getKey()).isEqualTo(e.getValue().get("classe").asText());
            });
            assertThat(pr.at("/parametros/oficial").asText()).isEqualTo(esperado.get("classe").asText());
            assertThat(pr.get("alertas")).isEmpty();
        }

        @Test
        void simularQuitarACdaAjuizadaMudaAClasseSemAlterarOImportado() throws Exception {
            String antes = jdbc.queryForObject("SELECT count(*)::text FROM debito WHERE cliente_id = ?", String.class, metalurgica.getId());
            JsonNode sim = json.readTree(mvc.perform(post("/api/clientes/{c}/capacidade/pr/simulacao", metalurgica.getId()).with(sessao)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"quitar\":[\"2023.0045678-2\"]}")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            JsonNode esperado = esperados.at("/classificacao_pr/simulacao_quitar_2023_0045678_2");
            assertThat(sim.get("classe").asText()).isEqualTo(esperado.get("classe").asText());
            assertThat(new BigDecimal(sim.get("valor").asText())).isEqualByComparingTo(esperado.get("soma").asText());
            assertThat(sim.get("rotuloTexto").asText()).startsWith("simulação");
            assertThat(jdbc.queryForObject("SELECT count(*)::text FROM debito WHERE cliente_id = ?", String.class, metalurgica.getId())).isEqualTo(antes);
            assertThat(metodologia(conteudo("/api/clientes/{c}/capacidade"), "PR").get("classe").asText()).isEqualTo("B");
        }

        @Test
        void capagOficialCEstimadaComRotuloEIrrecuperaveisDoGerador() throws Exception {
            JsonNode c = conteudo("/api/clientes/{c}/capacidade");
            assertThat(c.at("/capagOficial/classe").asText()).isEqualTo(esperados.at("/capag_oficial/classe").asText());
            assertThat(c.at("/capagOficial/rotulo").asText()).isEqualTo("OFICIAL");
            JsonNode est = metodologia(c, "CAPAG_ESTIMADA");
            assertThat(est.get("rotulo").asText()).isEqualTo("ESTIMATIVA");
            assertThat(est.get("classe").asText()).isIn("A", "B", "C", "D");
            assertThat(est.at("/parametros/faixaA").asText()).isEqualTo("2");
            assertThat(est.get("criterios").toString()).contains("GRF", "PLR", "Fluxo de caixa (DFC direta)", "parâmetro do escritório");
            List<String> irrecuperaveis = new ArrayList<>();
            est.get("itens").forEach(i -> {
                if ("IRRECUPERAVEL".equals(i.get("classe").asText())) {
                    irrecuperaveis.add(i.get("id").asText());
                }
            });
            assertThat(irrecuperaveis).hasSize(esperados.get("irrecuperaveis_pgfn").size());
            assertThat(est.get("itens")).hasSize(4).allSatisfy(i -> assertThat(i.get("classe").asText()).isEqualTo("RECUPERAVEL"));
            JsonNode ger = metodologia(c, "GERENCIAL");
            assertThat(ger.get("rotuloTexto").asText()).isEqualTo("análise do consultor — não é critério oficial");
            JsonNode resumo = conteudo("/api/clientes/{c}/capacidade/resumo");
            assertThat(resumo.get("capagOficial").asText()).isEqualTo("C");
            assertThat(resumo.get("classePr").asText()).isEqualTo("B");
        }

        private JsonNode metodologia(JsonNode capacidade, String chave) {
            return encontrar(capacidade.get("metodologias"), m -> chave.equals(m.get("metodologia").asText()));
        }
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
