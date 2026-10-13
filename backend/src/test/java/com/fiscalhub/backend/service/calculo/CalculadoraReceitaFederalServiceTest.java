package com.fiscalhub.backend.service.calculo;

import com.fiscalhub.backend.dto.ParcelaDTO;
import com.fiscalhub.backend.dto.SimulacaoResultDTO;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraReceitaFederalServiceTest {

    /** Comportamento ATUAL do motor (inclusive o que diverge da lei). Se algum quebrar, o cálculo mudou. */
    @Nested
    class Caracterizacao {

        @Test
        void atrasoDe10Dias_multaDiariaESemJuros() {
            SimulacaoResultDTO r = CenarioParcelamento.federal().diasDeAtraso(10).calcularFederal();

            assertThat(r.getDiasEmAtraso()).isEqualTo(10);
            assertThat(r.getPercentualMultaMora()).isEqualByComparingTo("3.30");
            assertThat(r.getValorMultaMora()).isEqualByComparingTo("330.00");
            assertThat(r.getPercentualJurosMora()).isEqualByComparingTo("0");
            assertThat(r.getValorAtualizado()).isEqualByComparingTo("10330.00");
            assertThat(r.getNumeroParcelas()).isEqualTo(10);
            assertThat(r.getValorParcela()).isEqualByComparingTo("1033.00");
            assertThat(r.getCustoTotal()).isEqualByComparingTo("10330.00");
            assertThat(r.getAvisoParcelaMinima()).isNull();
        }

        @Test
        void atrasoDe45Dias_multaSaltaPara20EJurosDeUmMes() {
            SimulacaoResultDTO r = CenarioParcelamento.federal().diasDeAtraso(45).calcularFederal();

            assertThat(r.getPercentualMultaMora()).isEqualByComparingTo("20.00");
            assertThat(r.getValorMultaMora()).isEqualByComparingTo("2000.00");
            assertThat(r.getPercentualJurosMora()).isEqualByComparingTo("1.0000");
            assertThat(r.getValorJurosMora()).isEqualByComparingTo("100.00");
            assertThat(r.getValorAtualizado()).isEqualByComparingTo("12100.00");
            assertThat(r.getValorEntrada()).isEqualByComparingTo("0");
            assertThat(r.getValorParcela()).isEqualByComparingTo("1210.00");
            assertThat(r.getTabelaParcelas()).hasSize(10);
        }

        @Test
        void atrasoDe45DiasComEntradaPert_entradaDe5PorCento() {
            SimulacaoResultDTO r = CenarioParcelamento.federalPert().diasDeAtraso(45).comEntrada().calcularFederal();

            assertThat(r.getValorAtualizado()).isEqualByComparingTo("12100.00");
            assertThat(r.getValorEntrada()).isEqualByComparingTo("605.00");
            assertThat(r.getValorParcela()).isEqualByComparingTo("1149.50");
            assertThat(r.getCustoTotal()).isEqualByComparingTo("12100.00");
            assertThat(r.getTabelaParcelas()).hasSize(11);
            assertThat(r.getTabelaParcelas().get(0).getNumeroParcela()).isZero();
        }

        @Test
        void atrasoDe400Dias_selicCompostaDe13Meses() {
            SimulacaoResultDTO r = CenarioParcelamento.federal().diasDeAtraso(400).calcularFederal();

            assertThat(r.getDiasEmAtraso()).isEqualTo(400);
            assertThat(r.getPercentualMultaMora()).isEqualByComparingTo("20.00");
            // 1,01^13 - 1 (fev/2024 a fev/2025, composta)
            assertThat(r.getPercentualJurosMora()).isEqualByComparingTo("13.8093");
            assertThat(r.getValorJurosMora()).isEqualByComparingTo("1380.93");
            assertThat(r.getValorAtualizado()).isEqualByComparingTo("13380.93");
            assertThat(r.getValorParcela()).isEqualByComparingTo("1338.09");
            assertThat(r.getCustoTotal()).isEqualByComparingTo("13380.90");
        }

        @Test
        void parcelasAbaixoDoMinimo_reduzQuantidadeParaRespeitarR$500() {
            SimulacaoResultDTO r = CenarioParcelamento.federal().diasDeAtraso(45).parcelas(60).calcularFederal();

            assertThat(r.getNumeroParcelas()).isEqualTo(24);
            assertThat(r.getValorParcela()).isEqualByComparingTo("504.17");
            assertThat(r.getCustoTotal()).isEqualByComparingTo("12100.08");
            assertThat(r.getAvisoParcelaMinima()).contains("24").contains("500.00");
        }

        @Test
        void semSelicCadastrada_jurosZero() {
            SimulacaoResultDTO r = CenarioParcelamento.federalSemSelic().diasDeAtraso(45).calcularFederal();

            assertThat(r.getPercentualJurosMora()).isEqualByComparingTo("0");
            assertThat(r.getIndiceUtilizado()).isEqualTo("SELIC");
            assertThat(r.getValorAtualizado()).isEqualByComparingTo("12000.00");
        }
    }

    /** Resultado exigido pela norma. Desativados até a correção do motor na Fase 5. */
    @Nested
    class Normativo {

        @Test
        @Disabled("Fase 5 — divergência normativa: multa de mora é 0,33% ao dia limitada a 20% (art. 61 Lei 9.430/96); hoje salta para 20% no 31º dia")
        void multaDe45DiasE14Virgula85PorCento() {
            SimulacaoResultDTO r = CenarioParcelamento.federal().diasDeAtraso(45).calcularFederal();

            assertThat(r.getPercentualMultaMora()).isEqualByComparingTo("14.85");
        }

        @Test
        @Disabled("Fase 5 — divergência normativa: juros = soma simples da SELIC mensal do mês seguinte ao vencimento até o mês anterior ao pagamento + 1% no mês do pagamento (art. 61 §3º Lei 9.430/96); hoje é SELIC composta incluindo o mês do pagamento")
        void jurosSaoSomaSimplesDaSelicMais1PorCentoNoMesDoPagamento() {
            // vencimento jan/2024, pagamento fev/2025: SELIC de fev/2024 a jan/2025 (12 × 1,00%) + 1% = 13%
            SimulacaoResultDTO r = CenarioParcelamento.federal().diasDeAtraso(400).calcularFederal();

            assertThat(r.getPercentualJurosMora()).isEqualByComparingTo("13.00");
            assertThat(r.getValorJurosMora()).isEqualByComparingTo("1300.00");
        }

        @Test
        @Disabled("Fase 5 — divergência normativa: sem SELIC cadastrada para o período o cálculo deve falhar; hoje retorna juros 0")
        void semSelicCadastradaLancaExcecao() {
            assertThatThrownBy(() -> CenarioParcelamento.federalSemSelic().diasDeAtraso(45).calcularFederal())
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @Disabled("Fase 5 — divergência normativa: cada parcela é acrescida de SELIC acumulada desde a consolidação até o mês anterior ao pagamento + 1% no mês do pagamento (art. 13 Lei 10.522/02); hoje as parcelas são fixas")
        void parcelasSaoAcrescidasDeSelicAcumuladaMais1PorCento() {
            SimulacaoResultDTO r = CenarioParcelamento.federal().diasDeAtraso(45).calcularFederal();
            List<ParcelaDTO> tabela = r.getTabelaParcelas();
            BigDecimal base = r.getValorParcela();

            // 1ª parcela (mês seguinte à consolidação): só o 1%; 2ª: SELIC de 1 mês (1%) + 1%
            assertThat(tabela.get(0).getValorParcela()).isEqualByComparingTo(base.multiply(new BigDecimal("1.01")).setScale(2, RoundingMode.HALF_UP));
            assertThat(tabela.get(1).getValorParcela()).isEqualByComparingTo(base.multiply(new BigDecimal("1.02")).setScale(2, RoundingMode.HALF_UP));
        }
    }
}
