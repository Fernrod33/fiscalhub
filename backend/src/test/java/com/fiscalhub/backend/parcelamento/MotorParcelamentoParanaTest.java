package com.fiscalhub.backend.service.calculo;

import com.fiscalhub.backend.dto.EtapaCalculoDTO;
import com.fiscalhub.backend.dto.SimulacaoResultDTO;
import com.fiscalhub.backend.entity.enums.TipoIndice;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CalculadoraReceitaEstadualPRServiceTest {

    /** Comportamento ATUAL do motor (inclusive o que diverge da lei). Se algum quebrar, o cálculo mudou. */
    @Nested
    class Caracterizacao {

        @Test
        void atrasoDe10Dias_semAtualizacaoMultaDiariaESemJuros() {
            SimulacaoResultDTO r = CenarioParcelamento.estadualPR().diasDeAtraso(10).calcularPR();

            assertThat(r.getPercentualMultaMora()).isEqualByComparingTo("3.30");
            assertThat(r.getValorMultaMora()).isEqualByComparingTo("330.00");
            assertThat(r.getPercentualJurosMora()).isEqualByComparingTo("0");
            assertThat(r.getValorAtualizado()).isEqualByComparingTo("10330.00");
            assertThat(r.getValorParcela()).isEqualByComparingTo("1033.00");
            assertThat(r.getIndiceUtilizado()).isEqualTo("UFPR");
            assertThat(r.getAvisoParcelaMinima()).isNull();
        }

        @Test
        void atrasoDe45Dias_atualizaPelaUfprMulta20EJuros1PorCento() {
            SimulacaoResultDTO r = CenarioParcelamento.estadualPR().diasDeAtraso(45).calcularPR();

            // UFPR 101/100 → 10.100,00; multa e juros sobre o valor atualizado
            assertThat(r.getPercentualMultaMora()).isEqualByComparingTo("20.00");
            assertThat(r.getValorMultaMora()).isEqualByComparingTo("2020.00");
            assertThat(r.getPercentualJurosMora()).isEqualByComparingTo("1.00");
            assertThat(r.getValorJurosMora()).isEqualByComparingTo("101.00");
            assertThat(r.getValorAtualizado()).isEqualByComparingTo("12221.00");
            assertThat(r.getValorParcela()).isEqualByComparingTo("1222.10");
        }

        @Test
        void atrasoDe45DiasComEntrada_entradaDe10PorCento() {
            SimulacaoResultDTO r = CenarioParcelamento.estadualPR().diasDeAtraso(45).comEntrada().calcularPR();

            assertThat(r.getValorEntrada()).isEqualByComparingTo("1222.10");
            assertThat(r.getValorParcela()).isEqualByComparingTo("1099.89");
            assertThat(r.getCustoTotal()).isEqualByComparingTo("12221.00");
            assertThat(r.getTabelaParcelas()).hasSize(11);
        }

        @Test
        void atrasoDe400Dias_jurosFixosDe1PorCentoAoMes() {
            SimulacaoResultDTO r = CenarioParcelamento.estadualPR().diasDeAtraso(400).calcularPR();

            // UFPR 110/100 → 11.000,00; multa 20% = 2.200,00; juros 13 meses × 1% = 1.430,00
            assertThat(r.getValorMultaMora()).isEqualByComparingTo("2200.00");
            assertThat(r.getPercentualJurosMora()).isEqualByComparingTo("13.00");
            assertThat(r.getValorJurosMora()).isEqualByComparingTo("1430.00");
            assertThat(r.getValorAtualizado()).isEqualByComparingTo("14630.00");
            assertThat(r.getValorParcela()).isEqualByComparingTo("1463.00");
        }

        @Test
        void parcelasAbaixoDoMinimo_reduzQuantidadeParaRespeitarR$200() {
            SimulacaoResultDTO r = CenarioParcelamento.estadualPR().valor("5000.00").diasDeAtraso(45).parcelas(60).calcularPR();

            assertThat(r.getValorAtualizado()).isEqualByComparingTo("6110.50");
            assertThat(r.getNumeroParcelas()).isEqualTo(30);
            assertThat(r.getValorParcela()).isEqualByComparingTo("203.69");
            assertThat(r.getCustoTotal()).isEqualByComparingTo("6110.70");
            assertThat(r.getAvisoParcelaMinima()).contains("30").contains("200.00");
        }

        @Test
        void semUfprDoMesDoCalculoLancaExcecao() {
            // UFPR cadastrada só para jan/2024, fev/2024 e fev/2025; o cálculo cai em abr/2024
            assertThatThrownBy(() -> CenarioParcelamento.estadualPR().diasDeAtraso(100).calcularPR())
                    .hasMessage("Índice UFPR ausente para 2024-04.");
        }
    }

    /** Resultado exigido pela norma. Desativados até a correção do motor na Fase 5. */
    @Nested
    class Normativo {

        @Test
        @Disabled("Fase 5 — divergência normativa: não há atualização monetária pela UFPR (Lei PR 15.610/2007); hoje o débito é corrigido pela UFPR antes de multa e juros")
        void naoAplicaAtualizacaoMonetariaPelaUfpr() {
            CenarioParcelamento cenario = CenarioParcelamento.estadualPR().diasDeAtraso(400);
            SimulacaoResultDTO r = cenario.calcularPR();

            assertThat(r.getIndiceUtilizado()).isNotEqualTo("UFPR");
            assertThat(r.getMemoriaCalculo()).extracting(EtapaCalculoDTO::getDescricao).noneMatch(d -> d.contains("UFPR"));
            verify(cenario.repositorio, never()).findByTipoAndAnoAndMes(eq(TipoIndice.UFPR), anyInt(), anyInt());
        }

        @Test
        @Disabled("Fase 5 — divergência normativa: juros = soma da SELIC mensal + 1% no mês do pagamento (art. 38 Lei 11.580/96); hoje é 1% ao mês fixo")
        void jurosSaoSomaDaSelicMais1PorCentoNoMesDoPagamento() {
            // SELIC 0,80% a.m.: fev/2024 a jan/2025 (12 × 0,80%) + 1% = 10,60%
            SimulacaoResultDTO r = CenarioParcelamento.estadualPR()
                    .selic(YearMonth.of(2024, 1), YearMonth.of(2025, 12), "0.80")
                    .diasDeAtraso(400).calcularPR();

            assertThat(r.getPercentualJurosMora()).isEqualByComparingTo("10.60");
        }

        @Test
        @Disabled("Fase 5 — divergência normativa: multa de mora é 0,33% ao dia até o 30º dia e 10% a partir do 31º, até a inscrição em dívida ativa (art. 55 §1º I c/c art. 40 Lei 11.580/96); hoje é 20%")
        void multaApos30DiasE10PorCento() {
            SimulacaoResultDTO r = CenarioParcelamento.estadualPR().diasDeAtraso(45).calcularPR();

            assertThat(r.getPercentualMultaMora()).isEqualByComparingTo("10.00");
        }
    }
}
