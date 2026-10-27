package com.fiscalhub.backend.situacao.adaptadores;

import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.entity.enums.SituacaoDebito;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.importacao.FalhaImportacao;
import com.fiscalhub.backend.pdf.AdaptadorDocumentoPdf;
import com.fiscalhub.backend.pdf.DocumentoPdf;
import com.fiscalhub.backend.pdf.LeitorPdf;
import com.fiscalhub.backend.situacao.Extracao;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cada adaptador ficticio-2026-10 contra o PDF da massa fictícia: reconhecimento exclusivo e os valores lidos. */
class AdaptadoresTest {

    static final Path MASSA = Path.of("src/test/resources/massa-ficticia");
    static final List<AdaptadorDocumentoPdf> TODOS = List.of(new AdaptadorRelatorioSituacaoFiscal(), new AdaptadorRegularize(),
            new AdaptadorCapag(), new AdaptadorContaCorrentePr(), new AdaptadorRti(), new AdaptadorGrPr(), new AdaptadorRelacaoGrPr(),
            new AdaptadorDctfPdf(), new AdaptadorDctfWeb(), new AdaptadorDarf(), new AdaptadorRelatorioPagamentos());

    @Test
    void relatorioDeSituacaoFiscal() throws IOException {
        Extracao e = extrair("situacao-fiscal/Relatorio_Situacao_Fiscal_11222333000181_20261014.pdf", AdaptadorRelatorioSituacaoFiscal.class);
        assertThat(e.cnpj).isEqualTo("11222333000181");
        assertThat(e.fim).isEqualTo(LocalDate.of(2026, 10, 14));
        Extracao.Debito cofins = e.debitos.stream().filter(d -> d.tributo() == TipoTributo.COFINS && d.orgao() == OrgaoCredor.RFB).findFirst().orElseThrow();
        assertThat(cofins.competencia()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(cofins.principal()).isEqualByComparingTo("5079.00");
        assertThat(cofins.total()).isEqualByComparingTo("5448.24");
        assertThat(cofins.origem()).isEqualTo(new Extracao.Origem(1, 14,
                "5856-01 - COFINS | 08/2026 | 25/09/2026 | 33.079,00 | 5.079,00 | 318,45 | 50,79 | 5.448,24 | DEVEDOR"));
        assertThat(e.debitos).filteredOn(d -> "90 3 24 002201-55".equals(d.numero())).singleElement()
                .satisfies(d -> assertThat(d.situacao()).isEqualTo(SituacaoDebito.INSCRITO_DIVIDA_ATIVA));
        assertThat(e.debitos).filteredOn(d -> d.situacao() == SituacaoDebito.NEGOCIADO).hasSize(3);
        assertThat(e.debitos).filteredOn(d -> d.situacao() == SituacaoDebito.SUSPENSO).hasSize(1);
        assertThat(e.pendencias).singleElement().satisfies(p -> assertThat(p.periodo()).isEqualTo(YearMonth.of(2026, 6)));
    }

    @Test
    void regularizeComInscricoesNegociacaoEAjuizamento() throws IOException {
        Extracao e = extrair("pgfn/Extrato_Inscricoes_Regularize_11222333000181.pdf", AdaptadorRegularize.class);
        assertThat(e.debitos).hasSize(4);
        assertThat(e.debitos.stream().map(Extracao.Debito::total).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("512791.83");
        Extracao.Debito irpj = e.debitos.getFirst();
        assertThat(irpj.numero()).isEqualTo("90 2 23 004512-38"); // quebrada em duas linhas na célula
        assertThat(irpj.processo()).isEqualTo("5012345-67.2023.4.04.7005");
        assertThat(irpj.ajuizado()).isTrue();
        assertThat(irpj.negociacao()).contains("2021.009871-4");
        assertThat(e.debitos.getLast().numero()).isEqualTo("15.678.912-3 (DEBCAD)");
        assertThat(e.debitos.getLast().tributo()).isEqualTo(TipoTributo.INSS);
        assertThat(e.parcelamentos).singleElement().satisfies(p -> {
            assertThat(p.parcelasPagas()).isEqualTo(57);
            assertThat(p.parcelasTotal()).isEqualTo(64);
            assertThat(p.saldo()).isEqualByComparingTo("52000.00");
            assertThat(p.debitos()).hasSize(3);
        });
    }

    @Test
    void capagConsistenteEARegraDaPortaria() throws IOException {
        Extracao e = extrair("pgfn/CAPAG_11222333000181.pdf", AdaptadorCapag.class);
        assertThat(e.capacidades).singleElement().satisfies(c -> {
            assertThat(c.classe()).isEqualTo("C");
            assertThat(c.classeConsistente()).isTrue();
        });
        assertThat(e.alertas).isEmpty();
        assertThat(AdaptadorCapag.classePelaPortaria(new BigDecimal("200"), new BigDecimal("100"))).isEqualTo("A");
        assertThat(AdaptadorCapag.classePelaPortaria(new BigDecimal("199.99"), new BigDecimal("100"))).isEqualTo("B");
        assertThat(AdaptadorCapag.classePelaPortaria(new BigDecimal("100"), new BigDecimal("100"))).isEqualTo("B");
        assertThat(AdaptadorCapag.classePelaPortaria(new BigDecimal("50"), new BigDecimal("100"))).isEqualTo("C");
        assertThat(AdaptadorCapag.classePelaPortaria(new BigDecimal("49.99"), new BigDecimal("100"))).isEqualTo("D");
        assertThat(AdaptadorCapag.classePelaPortaria(new BigDecimal("1"), BigDecimal.ZERO)).isNull();
    }

    @Test
    void contaCorrenteDividaAtivaEParcelamentoDoPr() throws IOException {
        Extracao e = extrair("parana/Conta_Corrente_e_Divida_Ativa_ICMS_9012345663.pdf", AdaptadorContaCorrentePr.class);
        assertThat(e.inscricaoEstadual).isEqualTo("9012345663");
        assertThat(e.declarados).hasSize(12);
        assertThat(e.debitos).extracting(Extracao.Debito::orgao, Extracao.Debito::tributo, Extracao.Debito::situacao)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(OrgaoCredor.SEFA_PR, TipoTributo.ICMS_ST, SituacaoDebito.EM_ABERTO),
                        org.assertj.core.groups.Tuple.tuple(OrgaoCredor.PGE_PR, TipoTributo.ICMS, SituacaoDebito.PARCELADO));
        assertThat(e.parcelamentos).singleElement().satisfies(p -> {
            assertThat(p.parcelasPagas()).isEqualTo(12);
            assertThat(p.parcelasTotal()).isEqualTo(36);
            assertThat(p.situacao()).isEqualTo("EM_DIA");
        });
    }

    @Test
    void rtiDaSaldosCredoresMensaisESemCapacidade() throws IOException {
        Extracao e = extrair("parana/RTI_9012345663.pdf", AdaptadorRti.class);
        assertThat(e.saldos).hasSize(6);
        assertThat(e.saldos.getLast().saldoFinal()).isEqualByComparingTo("365250.00");
        assertThat(e.capacidades).isEmpty();
        assertThat(e.alertas).extracting(Extracao.Alerta::regra).containsExactly("RTI_SEM_CAPACIDADE");
    }

    @Test
    void grPrIndividualERelacao() throws IOException {
        Extracao parcela = extrair("parana/GR-PR_Parcelamento_parcela_12-36.pdf", AdaptadorGrPr.class);
        assertThat(parcela.pagamentos).singleElement().satisfies(p -> {
            assertThat(p.parcelamento()).isEqualTo("PR-2025-0045871");
            assertThat(p.periodo()).isNull(); // "12/36" é a parcela, não o período
        });
        Extracao relacao = extrair("parana/Pagamentos_ICMS_GR-PR_9012345663.pdf", AdaptadorRelacaoGrPr.class);
        assertThat(relacao.cnpj).isNull();
        assertThat(relacao.inscricaoEstadual).isEqualTo("9012345663");
        assertThat(relacao.pagamentos).hasSize(11);
        assertThat(relacao.pagamentos).filteredOn(p -> p.parcelamento() != null).hasSize(6);
    }

    @Test
    void dctfEmPdfEEmDecSaoAMesmaDeclaracao() throws IOException {
        Extracao pdf = extrair("dctf/DCTF_Mensal_112024_11222333000181.pdf", AdaptadorDctfPdf.class);
        Extracao dec = new Extracao();
        LeitorDctfDec.ler(Files.readAllBytes(MASSA.resolve("dctf/DCTF_Mensal_112024_11222333000181.dec")), dec);
        assertThat(pdf.declarados).hasSize(4);
        assertThat(dec.declarados).extracting(Extracao.Declarado::codigoReceita, Extracao.Declarado::valor, Extracao.Declarado::pago)
                .isEqualTo(pdf.declarados.stream().map(d -> org.assertj.core.groups.Tuple.tuple(d.codigoReceita(), d.valor(), d.pago())).toList());
        assertThat(dec.inicio).isEqualTo(LocalDate.of(2024, 11, 1));
        assertThat(dec.declarados.getFirst().origem().pagina()).isNull();
        assertThat(dec.declarados.getFirst().origem().linha()).isEqualTo(3);
        assertThat(dec.alertas).isEmpty();
    }

    @Test
    void decComContagemErradaECabecalhoDesconhecido() throws IOException {
        String dec = Files.readString(MASSA.resolve("dctf/DCTF_Mensal_112024_11222333000181.dec"), StandardCharsets.ISO_8859_1);
        Extracao e = new Extracao();
        LeitorDctfDec.ler(dec.replaceFirst("R99\\d{6}", "R99000099").getBytes(StandardCharsets.ISO_8859_1), e);
        assertThat(e.alertas).extracting(Extracao.Alerta::regra).containsExactly("CONTAGEM_DEC");
        assertThatThrownBy(() -> LeitorDctfDec.ler("DCTFM2024 outro layout".getBytes(StandardCharsets.ISO_8859_1), new Extracao()))
                .isInstanceOf(FalhaImportacao.class).hasMessageContaining("layout");
    }

    @Test
    void dctfWebEDarfs() throws IOException {
        Extracao dctfweb = extrair("dctfweb/DCTFWeb_082026_Declaracao_Completa_11222333000181.pdf", AdaptadorDctfWeb.class);
        assertThat(dctfweb.declarados.stream().map(Extracao.Declarado::valor).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("148806.02");
        Extracao darf = extrair("pagamentos/DARF_Sicalc_IPI_112024_pago_em_atraso.pdf", AdaptadorDarf.class);
        assertThat(darf.pagamentos).singleElement().satisfies(p -> {
            assertThat(p.numero()).isEqualTo("07.18.25041.0099887-6");
            assertThat(p.vencimento()).isEqualTo(LocalDate.of(2024, 12, 24));
            assertThat(p.dataPagamento()).isEqualTo(LocalDate.of(2025, 2, 10));
            assertThat(p.multa()).isEqualByComparingTo(BigDecimal.ZERO);
        });
        Extracao relatorio = extrair("pagamentos/Relatorio_Consolidado_Pagamentos_11222333000181.pdf", AdaptadorRelatorioPagamentos.class);
        assertThat(relatorio.pagamentos).hasSize(17);
        assertThat(relatorio.pagamentos).filteredOn(p -> "2021.009871-4".equals(p.parcelamento())).hasSize(9);
        assertThat(relatorio.pagamentos.get(3).numero()).isEqualTo("07.18.25041.0099887-6"); // número cortado na célula, junto
        assertThat(relatorio.pagamentos.get(3).dataPagamento()).isEqualTo(LocalDate.of(2025, 2, 10));
    }

    @Test
    void cadaPdfDaMassaEhReconhecidoPorUmAdaptadorSoEPdfSemAFaixaPorNenhum() throws IOException {
        try (var arquivos = Files.walk(MASSA)) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".pdf")).toList()) {
                DocumentoPdf pdf = LeitorPdf.ler(Files.readAllBytes(arquivo));
                assertThat(TODOS.stream().filter(a -> a.reconhece(pdf))).as(arquivo.toString()).hasSize(1);
            }
        }
        DocumentoPdf real = new DocumentoPdf(List.of(new DocumentoPdf.Pagina(1, List.of(new DocumentoPdf.Linha(1, 1, 10, 9,
                List.of(new DocumentoPdf.Celula(10, "INFORMAÇÕES DE APOIO PARA EMISSÃO DE CERTIDÃO")))))));
        assertThat(TODOS).noneMatch(a -> a.reconhece(real)); // título certo, mas não é o layout fictício: nunca chute
    }

    static Extracao extrair(String arquivo, Class<? extends AdaptadorDocumentoPdf> tipo) throws IOException {
        DocumentoPdf pdf = LeitorPdf.ler(Files.readAllBytes(MASSA.resolve(arquivo)));
        AdaptadorDocumentoPdf adaptador = TODOS.stream().filter(tipo::isInstance).findFirst().orElseThrow();
        assertThat(adaptador.reconhece(pdf)).isTrue();
        Extracao e = new Extracao();
        adaptador.extrair(pdf, e);
        return e;
    }
}
