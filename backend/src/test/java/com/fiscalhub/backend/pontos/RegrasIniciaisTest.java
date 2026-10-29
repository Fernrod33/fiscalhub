package com.fiscalhub.backend.pontos;

import com.fiscalhub.backend.capacidade.CapacidadeService;
import com.fiscalhub.backend.fiscal.ApuracaoService;
import com.fiscalhub.backend.fiscal.ApuracaoService.SaldoFiscal;
import com.fiscalhub.backend.fiscal.ConferenciaEcdEcf;
import com.fiscalhub.backend.fiscal.ConferenciaEcdEcf.Comparacao;
import com.fiscalhub.backend.pontos.RegraPontoAtencao.*;
import com.fiscalhub.backend.situacao.SituacaoFiscalService;
import com.fiscalhub.backend.situacao.SituacaoFiscalService.ParcelamentoFiscal;
import com.fiscalhub.backend.situacao.SituacaoFiscalService.PendenciaFiscal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Cada regra: dispara, não dispara, limiar exato e dados insuficientes ("não avaliada"). Serviços simulados. */
class RegrasIniciaisTest {

    private final SituacaoFiscalService situacao = mock(SituacaoFiscalService.class);
    private final ApuracaoService apuracoes = mock(ApuracaoService.class);
    private final CapacidadeService capacidade = mock(CapacidadeService.class);
    private final ConferenciaEcdEcf conferencia = mock(ConferenciaEcdEcf.class);
    private final ContextoPontos ctx = new ContextoPontos(1L, null,
            new ContextoPontos.Servicos(null, situacao, apuracoes, capacidade, null, null, null, conferencia));

    private static Definicao def(String codigo, Severidade sev, Map<String, Object> p) {
        return new Definicao(codigo, codigo + "@t", codigo, Categoria.OPORTUNIDADE, sev, "f", "r", p, true);
    }

    private static SaldoFiscal saldo(String tipo, YearMonth mes, String valor) {
        return new SaldoFiscal(mes.getMonthValue(), tipo, "PR", null, tipo, mes.atDay(1), mes.atEndOfMonth(), BigDecimal.ZERO,
                new BigDecimal(valor), 9, "efd.txt", "E110", 10);
    }

    private void saldos(SaldoFiscal... s) {
        when(apuracoes.saldos(anyLong(), any())).thenReturn(List.of(s));
        when(apuracoes.apuracoes(anyLong(), any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void r05SaldoCredorNoLimiarDeMesesSeguidos() {
        Definicao d = def("R05", Severidade.MEDIA, Map.of("mesesConsecutivos", 3, "multiploDebitoMedio", 2));
        RegraPontoAtencao r = new RegrasIniciais.R05SaldoCredorAcumulado();
        saldos(saldo("SALDO_CREDOR_ICMS", YearMonth.of(2026, 6), "10"), saldo("SALDO_CREDOR_ICMS", YearMonth.of(2026, 7), "20"),
                saldo("SALDO_CREDOR_ICMS", YearMonth.of(2026, 8), "30"));
        assertThat(r.avaliar(ctx, d).achados()).hasSize(1); // exatamente 3 meses: dispara
        saldos(saldo("SALDO_CREDOR_ICMS", YearMonth.of(2026, 7), "20"), saldo("SALDO_CREDOR_ICMS", YearMonth.of(2026, 8), "30"));
        assertThat(r.avaliar(new ContextoPontos(1L, null, ctx.servicos()), d).achados()).isEmpty(); // 2 meses: não
        saldos(saldo("SALDO_CREDOR_ICMS", YearMonth.of(2026, 5), "10"), saldo("SALDO_CREDOR_ICMS", YearMonth.of(2026, 7), "20"),
                saldo("SALDO_CREDOR_ICMS", YearMonth.of(2026, 8), "30"));
        assertThat(r.avaliar(new ContextoPontos(1L, null, ctx.servicos()), d).achados()).as("meses não seguidos").isEmpty();
        saldos();
        assertThat(r.avaliar(new ContextoPontos(1L, null, ctx.servicos()), d).naoAvaliada()).contains("EFD ICMS/IPI");
    }

    @Test
    void r13CreditosNoLimiarDeMeses() {
        Definicao d = def("R13", Severidade.MEDIA, Map.of("meses", 3));
        RegraPontoAtencao r = new RegrasIniciais.R13CreditosPisCofins();
        saldos(saldo("CREDITO_PIS", YearMonth.of(2026, 6), "1"), saldo("CREDITO_PIS", YearMonth.of(2026, 7), "1"), saldo("CREDITO_PIS", YearMonth.of(2026, 8), "1"));
        assertThat(r.avaliar(ctx, d).achados()).extracting(Achado::chave).containsExactly("CREDITO_PIS");
        saldos(saldo("CREDITO_PIS", YearMonth.of(2026, 6), "1"), saldo("CREDITO_PIS", YearMonth.of(2026, 7), "0"), saldo("CREDITO_PIS", YearMonth.of(2026, 8), "1"));
        assertThat(r.avaliar(new ContextoPontos(1L, null, ctx.servicos()), d).achados()).isEmpty();
    }

    @Test
    void r15LimiarDaDivergenciaEcdEcf() {
        Definicao d = def("R15", Severidade.MEDIA, Map.of("limiar", 1.00));
        RegraPontoAtencao r = new RegrasIniciais.R15EcdEcf();
        when(conferencia.comparacoes(1L)).thenReturn(List.of(
                new Comparacao(2025, "Receita bruta", new BigDecimal("100.00"), 1, "ecd", "o", new BigDecimal("99.00"), 2, "ecf", "o", new BigDecimal("1.00")),
                new Comparacao(2025, "LAIR", new BigDecimal("100.00"), 1, "ecd", "o", new BigDecimal("98.99"), 2, "ecf", "o", new BigDecimal("1.01"))));
        assertThat(r.avaliar(ctx, d).achados()).extracting(Achado::chave).containsExactly("2025|LAIR"); // 1,00 não passa do limiar; 1,01 passa
        when(conferencia.comparacoes(1L)).thenReturn(List.of());
        assertThat(r.avaliar(ctx, d).naoAvaliada()).contains("ECD e a ECF");
    }

    @Test
    void r06SeveridadeCriticaSoQuandoImpedeACertidao() {
        Definicao d = def("R06", Severidade.ALTA, Map.of("severidadeSeImpedeCertidao", "CRITICA"));
        when(situacao.pendencias(anyLong(), any())).thenReturn(List.of(
                new PendenciaFiscal(1, "OMISSAO_DECLARACAO", "Omissão de GFIP", LocalDate.of(2026, 5, 1), "x", null, null, true, "PC 1.751"),
                new PendenciaFiscal(2, "OMISSAO_DECLARACAO", "Omissão de EFD-Contribuições", LocalDate.of(2026, 6, 1), "x", null, null, false, "PC 1.751"),
                new PendenciaFiscal(3, "PROCESSO_SUSPENSO", "Processo", null, "x", null, null, false, "")));
        List<Achado> a = new RegrasIniciais.R06Omissao().avaliar(ctx, d).achados();
        assertThat(a).extracting(Achado::severidade).containsExactly(Severidade.CRITICA, null); // nulo = a padrão (ALTA)
    }

    @Test
    void r10ParcelasNoLimiteDaSustentavelNaoDisparam() {
        Definicao d = def("R10", Severidade.ALTA, Map.of());
        RegraPontoAtencao r = new RegrasIniciais.R10ParcelamentoEmRisco();
        when(situacao.parcelamentos(anyLong(), any())).thenReturn(new ArrayList<>(List.of(
                new ParcelamentoFiscal(1, null, "P1", "Parcelamento", 1, 10, new BigDecimal("600"), BigDecimal.TEN, "EM_DIA", List.of(), null, null),
                new ParcelamentoFiscal(2, null, "P2", "Transação", 1, 10, new BigDecimal("400"), BigDecimal.TEN, "EM_DIA", List.of(), null, null))));
        when(capacidade.resumo(anyLong(), any())).thenReturn(new CapacidadeService.ResumoCapacidade("C", "D", true, "B", "B", new BigDecimal("1000")));
        assertThat(r.avaliar(ctx, d).achados()).isEmpty(); // 1.000 = sustentável: não passa
        when(capacidade.resumo(anyLong(), any())).thenReturn(new CapacidadeService.ResumoCapacidade("C", "D", true, "B", "B", new BigDecimal("999.99")));
        assertThat(r.avaliar(new ContextoPontos(1L, null, ctx.servicos()), d).achados()).extracting(Achado::chave).containsExactly("sustentavel");
        when(capacidade.resumo(anyLong(), any())).thenReturn(new CapacidadeService.ResumoCapacidade("C", "D", true, "B", "B", null));
        assertThat(r.avaliar(new ContextoPontos(1L, null, ctx.servicos()), d).naoAvaliada()).contains("parcela sustentável");
    }

    @Test
    void r12SaldoZeradoNaoDispara() {
        Definicao d = def("R12", Severidade.INFO, Map.of("percentualTrava", 30));
        saldos(saldo("PREJUIZO_FISCAL", YearMonth.of(2025, 12), "-5000"), saldo("BASE_NEGATIVA_CSLL", YearMonth.of(2025, 12), "0"));
        assertThat(new RegrasIniciais.R12PrejuizoFiscal().avaliar(ctx, d).achados()).extracting(Achado::chave).containsExactly("PREJUIZO_FISCAL");
    }
}
