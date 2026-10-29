package com.fiscalhub.backend.parcelamento;

import com.fiscalhub.backend.entity.enums.TipoIndice;
import com.fiscalhub.backend.parcelamento.MotorParcelamento.Etapa;
import com.fiscalhub.backend.parcelamento.MotorParcelamento.ResultadoModalidade;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MotorParcelamentoParanaTest {

    /** Resultado exigido pela norma (reativados sem mudar os valores esperados). */
    @Nested
    class Normativo {

        @Test
        void naoAplicaAtualizacaoMonetariaPelaUfpr() {
            CenarioParcelamento cenario = CenarioParcelamento.estadualPR().diasDeAtraso(400);
            ResultadoModalidade r = cenario.calcularPR();

            assertThat(r.indice()).isNotEqualTo("UFPR");
            assertThat(r.memoria()).extracting(Etapa::descricao).noneMatch(d -> d.contains("UFPR"));
            verify(cenario.repositorio, never()).findByTipoAndAnoAndMes(eq(TipoIndice.UFPR), anyInt(), anyInt());
        }

        @Test
        void jurosSaoSomaDaSelicMais1PorCentoNoMesDoPagamento() {
            // SELIC 0,80% a.m.: fev/2024 a jan/2025 (12 × 0,80%) + 1% = 10,60%
            ResultadoModalidade r = CenarioParcelamento.estadualPR()
                    .selic(YearMonth.of(2024, 1), YearMonth.of(2025, 12), "0.80")
                    .diasDeAtraso(400).calcularPR();

            assertThat(r.debitos().getFirst().percentualJuros()).isEqualByComparingTo("10.60");
        }

        @Test
        void multaApos30DiasE10PorCento() {
            ResultadoModalidade r = CenarioParcelamento.estadualPR().diasDeAtraso(45).calcularPR();

            assertThat(r.debitos().getFirst().percentualMulta()).isEqualByComparingTo("10.00");
        }
    }

    @Nested
    class Limites {

        @Test
        void impostoDaEfdAindaNoPrazoDaMultaDiariaSoParcelaComAMultaDe10PorCento() {
            ResultadoModalidade r = CenarioParcelamento.estadualPR().diasDeAtraso(10).calcularPR();

            assertThat(r.debitos().getFirst().percentualMulta()).isEqualByComparingTo("10.00");
            assertThat(r.avisos()).anyMatch(a -> a.contains("art. 81, §7º"));
        }

        @Test
        void impostoDaEfdTemNoMaximo24Parcelas() {
            ResultadoModalidade r = CenarioParcelamento.estadualPR().diasDeAtraso(400).parcelas(60).calcularPR();

            assertThat(r.parcelas()).isEqualTo(14); // 12.300,00: prazo de 24, reduzido a 14 pela parcela mínima de 6 UPF/PR (849,72)
            assertThat(r.avisos()).anyMatch(a -> a.contains("acima do máximo de 24"));
        }

        @Test
        void semUpfOficialDoMesAParcelaMinimaFicaBloqueadaComAviso() {
            ResultadoModalidade r = CenarioParcelamento.estadualPR().diasDeAtraso(45).calcularPR(); // fev/2024: sem UPF/PR importada

            assertThat(r.elegivel()).isFalse();
            assertThat(r.bloqueio()).contains("UPF/PR de 2024-02").contains("upf-pr.csv");
            assertThat(r.cronograma()).isNotEmpty(); // os valores aparecem, mas não valem como proposta
        }

        @Test
        void primeiraParcelaNaConcessaoEJurosVincendosDaSegundaEmDiante() {
            // data-base 13/02/2025 (UPF/PR 141,62): 1ª na data-base sem juros; 2ª com SELIC de mar/2025 (1%) sobre imposto e multa
            ResultadoModalidade r = CenarioParcelamento.estadualPR().diasDeAtraso(400).calcularPR();

            assertThat(r.bloqueio()).isNull();
            assertThat(r.cronograma().get(0).vencimento()).isEqualTo(CenarioParcelamento.VENCIMENTO.plusDays(400));
            assertThat(r.cronograma().get(0).percentualCorrecao()).isEqualByComparingTo("0");
            assertThat(r.cronograma().get(1).percentualCorrecao()).isEqualByComparingTo("1.00");
            // parcela 1.230,00; imposto + multa = 11.000 de 12.300 → juros de 1% sobre 1.100,00 = 11,00
            assertThat(r.cronograma().get(1).valor()).isEqualByComparingTo("1241.00");
        }
    }
}
