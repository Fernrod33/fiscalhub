package com.fiscalhub.backend.situacao;

import com.fiscalhub.backend.entity.IndiceMonetario;
import com.fiscalhub.backend.entity.enums.TipoIndice;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.repository.IndiceMonetarioRepository;
import com.fiscalhub.backend.service.IndiceMonetarioService;
import com.fiscalhub.backend.situacao.AcrescimosMoratorios.Acrescimos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Multa de 0,33% ao dia limitada a 20%; juros pela soma da SELIC do mês seguinte ao vencimento ao anterior ao pagamento + 1%. */
class AcrescimosMoratoriosTest {

    private static final BigDecimal PRINCIPAL = new BigDecimal("10000.00");

    @Test
    void multaDe30_60e61Dias() {
        AcrescimosMoratorios a = comSelic(YearMonth.of(2024, 1), YearMonth.of(2024, 12), "1.00");
        LocalDate vencimento = LocalDate.of(2024, 3, 15);
        assertThat(a.calcular(PRINCIPAL, vencimento, vencimento.plusDays(30)).percentualMulta()).isEqualByComparingTo("9.90");
        assertThat(a.calcular(PRINCIPAL, vencimento, vencimento.plusDays(60)).percentualMulta()).isEqualByComparingTo("19.80");
        Acrescimos dia61 = a.calcular(PRINCIPAL, vencimento, vencimento.plusDays(61));
        assertThat(dia61.percentualMulta()).isEqualByComparingTo("20.00"); // 20,13% limitado a 20%
        assertThat(dia61.multa()).isEqualByComparingTo("2000.00");
        assertThat(a.calcular(PRINCIPAL, vencimento, vencimento).total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void jurosPelaSomaDaSelicMaisUmPorCentoNoMesDoPagamento() {
        // vence 15/03, paga 20/06: SELIC de abril e maio (soma, não composta) + 1% em junho
        AcrescimosMoratorios a = comSelic(YearMonth.of(2024, 4), YearMonth.of(2024, 5), "0.80");
        Acrescimos r = a.calcular(PRINCIPAL, LocalDate.of(2024, 3, 15), LocalDate.of(2024, 6, 20));
        assertThat(r.percentualJuros()).isEqualByComparingTo("2.60");
        assertThat(r.juros()).isEqualByComparingTo("260.00");
        assertThat(r.selicDe()).isEqualTo(YearMonth.of(2024, 4));
        assertThat(r.selicAte()).isEqualTo(YearMonth.of(2024, 5));
    }

    @Test
    void viradaDeMesENoProprioMes() {
        AcrescimosMoratorios a = comSelic(YearMonth.of(2024, 1), YearMonth.of(2024, 1), "1.00");
        Acrescimos virada = a.calcular(PRINCIPAL, LocalDate.of(2024, 1, 31), LocalDate.of(2024, 2, 1));
        assertThat(virada.diasAtraso()).isEqualTo(1);
        assertThat(virada.percentualMulta()).isEqualByComparingTo("0.33");
        assertThat(virada.percentualJuros()).isEqualByComparingTo("1.00"); // só o 1% do mês do pagamento
        assertThat(virada.selicDe()).isNull();
        Acrescimos mesmoMes = a.calcular(PRINCIPAL, LocalDate.of(2024, 1, 10), LocalDate.of(2024, 1, 25));
        assertThat(mesmoMes.percentualJuros()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(mesmoMes.percentualMulta()).isEqualByComparingTo("4.95");
    }

    @Test
    void semSelicDoMesEhErroSemEstimativa() {
        AcrescimosMoratorios a = comSelic(YearMonth.of(2024, 4), YearMonth.of(2024, 4), "0.80"); // falta maio
        assertThatThrownBy(() -> a.calcular(PRINCIPAL, LocalDate.of(2024, 3, 15), LocalDate.of(2024, 6, 20)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Índice SELIC ausente para 2024-05.");
    }

    /** IndiceMonetarioService real sobre um repositório com a SELIC dada em cada mês do intervalo. */
    private static AcrescimosMoratorios comSelic(YearMonth de, YearMonth ate, String taxa) {
        List<IndiceMonetario> indices = new ArrayList<>();
        for (YearMonth m = de; !m.isAfter(ate); m = m.plusMonths(1)) {
            IndiceMonetario i = new IndiceMonetario();
            i.setTipo(TipoIndice.SELIC);
            i.setAno(m.getYear());
            i.setMes(m.getMonthValue());
            i.setValor(new BigDecimal(taxa));
            indices.add(i);
        }
        IndiceMonetarioRepository repositorio = mock(IndiceMonetarioRepository.class);
        when(repositorio.findByTipoAndPeriodo(any(), anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            YearMonth inicio = YearMonth.of(inv.<Integer>getArgument(1), inv.<Integer>getArgument(2));
            YearMonth fim = YearMonth.of(inv.<Integer>getArgument(3), inv.<Integer>getArgument(4));
            return indices.stream().filter(i -> {
                YearMonth m = YearMonth.of(i.getAno(), i.getMes());
                return !m.isBefore(inicio) && !m.isAfter(fim);
            }).toList();
        });
        return new AcrescimosMoratorios(new IndiceMonetarioService(repositorio), new com.fiscalhub.backend.parcelamento.RegrasParcelamento());
    }
}
