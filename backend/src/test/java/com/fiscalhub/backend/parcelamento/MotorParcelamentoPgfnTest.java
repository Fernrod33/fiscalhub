package com.fiscalhub.backend.parcelamento;

import com.fiscalhub.backend.parcelamento.MotorParcelamento.ResultadoModalidade;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Inscrição em dívida ativa da União: ordinário com encargo legal e transação do Edital 6/2026 (reaberto pelo 10/2026). */
class MotorParcelamentoPgfnTest {

    private static final LocalDate BASE = LocalDate.of(2026, 10, 14);
    private static final LocalDate INSCRITA = LocalDate.of(2024, 2, 5);

    /** IPI: principal 38.750,00, multa 7.750,00, juros 6.980,12, encargo 10.696,02 (20%) = 64.176,14. */
    private static CenarioParcelamento ipi() {
        return CenarioParcelamento.pgfn().dataBase(BASE).parcelas(null).inscricao("38750.00", "7750.00", "6980.12", "10696.02", INSCRITA);
    }

    @Test
    void ordinarioConsolidaComEncargoLegalDe20PorCento() {
        ResultadoModalidade r = ipi().calcular("PGFN_ORDINARIO");

        assertThat(r.debitos().getFirst().encargo()).isEqualByComparingTo("10696.02");
        assertThat(r.totalConsolidado()).isEqualByComparingTo("64176.14");
        assertThat(r.parcelas()).isEqualTo(60);
        assertThat(r.valorParcela()).isEqualByComparingTo("1069.60");
        assertThat(r.cronograma().getFirst().vencimento()).isEqualTo(LocalDate.of(2026, 10, 30)); // último dia útil do mês do pedido
        assertThat(r.avisos()).noneMatch(a -> a.contains("garantia"));
    }

    @Test
    void ordinarioAcimaDeUmMilhaoExigeGarantia() {
        ResultadoModalidade r = CenarioParcelamento.pgfn().dataBase(BASE)
                .inscricao("900000.00", "180000.00", "100000.00", "236000.00", INSCRITA).calcular("PGFN_ORDINARIO");

        assertThat(r.avisos()).anyMatch(a -> a.startsWith("Exige garantia"));
    }

    @Test
    void transacaoClasseCTemDescontoMaximoDoEditalSemTocarNoPrincipal() {
        ResultadoModalidade r = ipi().capag("C", null).calcular("PGFN_TRANSACAO_EDITAL_6_2026");

        assertThat(r.versaoRegra()).isEqualTo("PGFN_TRANSACAO_EDITAL_6_2026@2026-10-01"); // a versão do Edital 10/2026
        assertThat(r.elegivel()).isTrue();
        assertThat(r.desconto().valor()).isEqualByComparingTo("25426.14"); // 100% dos acréscimos < 65% do total (41.714,49)
        assertThat(r.entrada()).isEqualByComparingTo("3850.57");           // 6% de 64.176,14
        assertThat(r.prestacoesEntrada()).isEqualTo(6);
        assertThat(r.parcelas()).isEqualTo(114);
        assertThat(r.valorParcela()).isEqualByComparingTo("306.14");        // (64.176,14 − 25.426,14 − 3.850,57) ÷ 114
        assertThat(r.fonteSecundaria()).isTrue();
    }

    @Test
    void transacaoComDescontoLimitadoA65PorCentoDoTotal() {
        // acréscimos de 90.000 sobre principal de 10.000: 65% de 100.000 = 65.000 < 90.000
        ResultadoModalidade r = CenarioParcelamento.pgfn().dataBase(BASE)
                .inscricao("10000.00", "40000.00", "33333.33", "16666.67", INSCRITA).capag("D", null).calcular("PGFN_TRANSACAO_EDITAL_6_2026");

        assertThat(r.desconto().valor()).isEqualByComparingTo("65000.00");
    }

    @Test
    void transacaoClasseASemDescontoEmAte60Meses() {
        ResultadoModalidade r = ipi().capag("A", null).calcular("PGFN_TRANSACAO_EDITAL_6_2026");

        assertThat(r.desconto().valor()).isEqualByComparingTo("0");
        assertThat(r.prestacoesEntrada() + r.parcelas()).isEqualTo(60);
    }

    @Test
    void transacaoSemCapagOficialFicaBloqueada() {
        ResultadoModalidade r = ipi().calcular("PGFN_TRANSACAO_EDITAL_6_2026");

        assertThat(r.elegivel()).isFalse();
        assertThat(r.bloqueio()).contains("CAPAG oficial não importada");
    }

    @Test
    void inscricaoDepoisDaDataDeCorteNaoEntra() {
        ResultadoModalidade r = CenarioParcelamento.pgfn().dataBase(BASE)
                .inscricao("38750.00", "7750.00", "6980.12", "10696.02", LocalDate.of(2026, 8, 1)).capag("C", null)
                .calcular("PGFN_TRANSACAO_EDITAL_6_2026");

        assertThat(r.elegivel()).isFalse();
        assertThat(r.elegibilidade().getFirst().motivos()).anyMatch(m -> m.contains("03/07/2026"));
    }

    @Test
    void estimadaSoEntraComoCenarioRotuladoDaRevisao() {
        List<ResultadoModalidade> todas = ipi().capag("A", "C").simular();

        ResultadoModalidade oficial = todas.stream().filter(m -> m.cenario().equals("OFICIAL") && m.chave().startsWith("PGFN_TRANSACAO")).findFirst().orElseThrow();
        ResultadoModalidade revisao = todas.stream().filter(m -> m.cenario().equals("SE_REVISAO_DEFERIDA")).findFirst().orElseThrow();
        assertThat(oficial.desconto().valor()).isEqualByComparingTo("0");
        assertThat(revisao.desconto().valor()).isEqualByComparingTo("25426.14");
        assertThat(revisao.cenarioRotulo()).startsWith(MotorParcelamento.ROTULO_REVISAO);
        assertThat(SimulacaoService.recomendar(todas)).noneMatch(r -> "SE_REVISAO_DEFERIDA".equals(r.cenario()));
    }

    @Test
    void transacaoAVistaSemCorrecao() {
        ResultadoModalidade r = ipi().capag("C", null).simular().stream()
                .filter(m -> m.cenario().equals("A_VISTA")).findFirst().orElseThrow();

        assertThat(r.cronograma()).hasSize(1);
        assertThat(r.custoProjetado()).isEqualByComparingTo("38750.00");
    }
}
