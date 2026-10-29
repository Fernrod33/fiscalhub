package com.fiscalhub.backend.parcelamento;

import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.parcelamento.MotorParcelamento.DebitoSimulado;
import com.fiscalhub.backend.parcelamento.MotorParcelamento.Parcela;
import com.fiscalhub.backend.parcelamento.MotorParcelamento.ResultadoModalidade;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MotorParcelamentoFederalTest {

    /** Resultado exigido pela norma (os 6 testes da Fase 2b, reativados sem mudar os valores esperados). */
    @Nested
    class Normativo {

        @Test
        void multaDe45DiasE14Virgula85PorCento() {
            ResultadoModalidade r = CenarioParcelamento.federal().diasDeAtraso(45).calcularFederal();

            assertThat(r.debitos().getFirst().percentualMulta()).isEqualByComparingTo("14.85");
        }

        @Test
        void jurosSaoSomaSimplesDaSelicMais1PorCentoNoMesDoPagamento() {
            // vencimento jan/2024, pagamento fev/2025: SELIC de fev/2024 a jan/2025 (12 × 1,00%) + 1% = 13%
            ResultadoModalidade r = CenarioParcelamento.federal().diasDeAtraso(400).calcularFederal();

            assertThat(r.debitos().getFirst().percentualJuros()).isEqualByComparingTo("13.00");
            assertThat(r.debitos().getFirst().juros()).isEqualByComparingTo("1300.00");
        }

        @Test
        void semSelicCadastradaLancaExcecao() {
            assertThatThrownBy(() -> CenarioParcelamento.federalSemSelic().diasDeAtraso(45).calcularFederal())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Índice SELIC ausente para 2024-02.");
        }

        @Test
        void selicFaltandoNoMeioDoPeriodoLancaExcecao() {
            CenarioParcelamento cenario = CenarioParcelamento.federalSemSelic()
                    .selic(YearMonth.of(2024, 2), YearMonth.of(2024, 6), "1.00")
                    .selic(YearMonth.of(2024, 8), YearMonth.of(2025, 2), "1.00")
                    .diasDeAtraso(400);

            assertThatThrownBy(cenario::calcularFederal).hasMessage("Índice SELIC ausente para 2024-07.");
        }

        @Test
        void parcelasSaoAcrescidasDeSelicAcumuladaMais1PorCento() {
            ResultadoModalidade r = CenarioParcelamento.federal().diasDeAtraso(45).calcularFederal();
            List<Parcela> tabela = r.cronograma();
            BigDecimal base = r.valorParcela();

            // 1ª parcela (mês seguinte à consolidação): só o 1%; 2ª: SELIC de 1 mês (1%) + 1%
            assertThat(tabela.get(0).valor()).isEqualByComparingTo(base.multiply(new BigDecimal("1.01")).setScale(2, RoundingMode.HALF_UP));
            assertThat(tabela.get(1).valor()).isEqualByComparingTo(base.multiply(new BigDecimal("1.02")).setScale(2, RoundingMode.HALF_UP));
        }
    }

    /** Casos-limite das regras (valores tirados do texto da norma, em regras.json). */
    @Nested
    class Limites {

        @Test
        void consolidaCadaDebitoComOProprioVencimento() {
            // 6.000 vencido em 10/01/2024 (45 dias: 14,85%) + 4.000 vencido em 10/02/2024 (14 dias: 4,62%), data-base 24/02/2024
            ResultadoModalidade r = CenarioParcelamento.federal().valor("6000.00")
                    .maisUmDebito("4000.00", LocalDate.of(2024, 2, 10)).diasDeAtraso(45).calcularFederal();

            assertThat(r.debitos()).extracting(c -> c.percentualMulta().toPlainString()).containsExactly("14.85", "4.62");
            assertThat(r.debitos().get(0).juros()).isEqualByComparingTo("60.00"); // venceu em jan: 1% no mês do pagamento
            assertThat(r.debitos().get(1).juros()).isEqualByComparingTo("0.00");  // venceu no mês da consolidação
            assertThat(r.totalConsolidado()).isEqualByComparingTo("11135.80"); // 6.951,00 + 4.184,80
        }

        @Test
        void parcelaMinimaDeR$500ReduzOPrazoPedido() {
            ResultadoModalidade r = CenarioParcelamento.federal().diasDeAtraso(45).parcelas(60).calcularFederal();

            // 11.585,00 ÷ 500 = 23,17 → 23 parcelas
            assertThat(r.parcelas()).isEqualTo(23);
            assertThat(r.valorParcela()).isEqualByComparingTo("503.70");
            assertThat(r.avisos()).anyMatch(a -> a.contains("23") && a.contains("500,00"));
        }

        @Test
        void semPrazoPedidoUsaOMaximoQueAParcelaMinimaPermite() {
            ResultadoModalidade r = CenarioParcelamento.federal().valor("100000.00").diasDeAtraso(45).parcelas(null).calcularFederal();

            assertThat(r.parcelas()).isEqualTo(60);
            assertThat(r.cronograma()).hasSize(60);
            BigDecimal soma = r.cronograma().stream().map(Parcela::nominal).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(soma).isEqualByComparingTo(r.totalConsolidado()); // a última parcela leva o arredondamento
        }

        @Test
        void mesFuturoSemSelicUsaAProjecaoRotulada() {
            // data-base 15/12/2025: SELIC carregada até 2025-12; 2026 em diante é projeção (média das 12 últimas = 1%)
            ResultadoModalidade r = CenarioParcelamento.federal().dataBase(LocalDate.of(2025, 12, 15)).calcularFederal();

            assertThat(r.cronograma().get(0).projetada()).isFalse(); // jan/2026: só o 1%
            assertThat(r.cronograma().get(1).projetada()).isTrue();  // fev/2026: SELIC de jan/2026 projetada
            assertThat(r.selicProjetada()).isEqualByComparingTo("1.0000");
            assertThat(r.avisos()).anyMatch(a -> a.contains(MotorParcelamento.ROTULO_PROJECAO));
            assertThat(r.custoProjetado()).isGreaterThan(r.custoNominal());
        }

        @Test
        void selicProjetadaDoEscritorioSubstituiAMedia() {
            ResultadoModalidade r = CenarioParcelamento.federal().dataBase(LocalDate.of(2025, 12, 15)).selicProjetada("2.00").calcularFederal();

            assertThat(r.selicProjetada()).isEqualByComparingTo("2.00");
            assertThat(r.selicProjetadaOrigem()).isEqualTo("parâmetro do escritório");
            assertThat(r.cronograma().get(1).percentualCorrecao()).isEqualByComparingTo("3.00");
        }

        @Test
        void irrfVedadoNoOrdinarioMasNaoNoSimplificado() {
            CenarioParcelamento c = CenarioParcelamento.federal().diasDeAtraso(45).debito(CenarioParcelamentoTributo.de(TipoTributo.IRRF));

            assertThat(c.calcular("RFB_ORDINARIO").elegivel()).isFalse();
            assertThat(c.calcular("RFB_ORDINARIO").elegibilidade().getFirst().motivos()).anyMatch(m -> m.contains("IRRF"));
            assertThat(c.calcular("RFB_SIMPLIFICADO").elegivel()).isTrue();
        }

        @Test
        void debitoParceladoSoEntraNoReparcelamentoCom10Ou20PorCento() {
            CenarioParcelamento c = CenarioParcelamento.federal().diasDeAtraso(45).reparcelamento()
                    .debito(d -> new DebitoSimulado(d.id(), d.orgao(), d.tributo(), d.descricao(), d.vencimento(), d.principal(), null, null,
                            null, null, null, null, null, "Parcelamento 123", null, false, false, null));
            List<ResultadoModalidade> todas = c.simular();
            ResultadoModalidade ordinario = todas.stream().filter(m -> m.chave().equals("RFB_ORDINARIO") && m.cenario().equals("OFICIAL")).findFirst().orElseThrow();
            ResultadoModalidade repar = todas.stream().filter(m -> m.chave().equals("RFB_ORDINARIO") && m.cenario().equals("REPARCELAMENTO")).findFirst().orElseThrow();

            assertThat(ordinario.elegivel()).isFalse();
            assertThat(repar.elegivel()).isTrue();
            assertThat(repar.entrada()).isEqualByComparingTo("1158.50"); // 10% de 11.585,00
            assertThat(repar.cronograma().getFirst().tipo()).isEqualTo("ENTRADA");
        }

        @Test
        void programasEncerradosNaoAceitamAdesaoEDizemPorque() {
            List<ResultadoModalidade> todas = CenarioParcelamento.federal().diasDeAtraso(45).simular();

            assertThat(todas).filteredOn(m -> m.chave().equals("PERT_RFB") || m.chave().equals("REFIS"))
                    .hasSize(2).allMatch(m -> !m.elegivel() && m.bloqueio().contains("encerrada"));
            assertThat(todas).anyMatch(m -> m.chave().equals("A_VISTA_RFB") && m.custoProjetado().compareTo(new BigDecimal("11585.00")) == 0);
        }

        @Test
        void seloComparaAMaiorParcelaComAParcelaSustentavel() {
            ResultadoModalidade cabe = CenarioParcelamento.federal().diasDeAtraso(45).parcelaSustentavel("2000.00").calcularFederal();
            ResultadoModalidade naoCabe = CenarioParcelamento.federal().diasDeAtraso(45).parcelaSustentavel("1000.00").calcularFederal();

            assertThat(cabe.selo().cabe()).isTrue();
            assertThat(naoCabe.selo().cabe()).isFalse();
            assertThat(naoCabe.selo().texto()).startsWith("Compromete ");
        }
    }

    /** Ajuste de tributo do débito do cenário. */
    static final class CenarioParcelamentoTributo {
        static java.util.function.UnaryOperator<DebitoSimulado> de(TipoTributo t) {
            return d -> new DebitoSimulado(d.id(), d.orgao(), t, d.descricao(), d.vencimento(), d.principal(), null, null, null, null,
                    null, null, null, null, null, false, false, null);
        }
    }
}
