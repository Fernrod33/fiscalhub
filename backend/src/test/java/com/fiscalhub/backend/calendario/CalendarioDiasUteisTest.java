package com.fiscalhub.backend.calendario;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarioDiasUteisTest {

    private final CalendarioDiasUteis cal = new CalendarioDiasUteis();

    @Test
    void pascoaEFeriadosMoveis() {
        assertThat(CalendarioDiasUteis.pascoa(2024)).isEqualTo(LocalDate.of(2024, 3, 31));
        assertThat(CalendarioDiasUteis.pascoa(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(cal.feriado(LocalDate.of(2026, 2, 16), null, null)).as("segunda de Carnaval").isTrue();
        assertThat(cal.feriado(LocalDate.of(2026, 2, 17), null, null)).as("terça de Carnaval").isTrue();
        assertThat(cal.feriado(LocalDate.of(2026, 4, 3), null, null)).as("Sexta-feira Santa").isTrue();
        assertThat(cal.feriado(LocalDate.of(2026, 6, 4), null, null)).as("Corpus Christi").isTrue();
        assertThat(cal.feriado(LocalDate.of(2026, 2, 18), null, null)).as("quarta de cinzas").isFalse();
    }

    @Test
    void ultimoDiaUtilPulaFimDeSemanaEFeriado() {
        assertThat(cal.ultimoDiaUtil(YearMonth.of(2026, 10), null, null)).isEqualTo(LocalDate.of(2026, 10, 30)); // 31 é sábado
        assertThat(cal.ultimoDiaUtil(YearMonth.of(2027, 4), null, null)).isEqualTo(LocalDate.of(2027, 4, 30));
        assertThat(cal.utilAnterior(LocalDate.of(2026, 11, 20), null, null)).isEqualTo(LocalDate.of(2026, 11, 19)); // Consciência Negra
        assertThat(cal.utilAnterior(LocalDate.of(2026, 12, 25), null, null)).isEqualTo(LocalDate.of(2026, 12, 24));
    }

    @Test
    void feriadoLocalSoValeNaUfENoMunicipio() {
        var curitiba = new CalendarioDiasUteis.FeriadoLocal("PR", "Curitiba", "09-08", "teste", "teste");
        assertThat(curitiba.em(LocalDate.of(2026, 9, 8), "PR", "Curitiba")).isTrue();
        assertThat(curitiba.em(LocalDate.of(2026, 9, 8), "PR", "Londrina")).isFalse();
        assertThat(new CalendarioDiasUteis.FeriadoLocal("PR", null, "2026-12-19", "teste", "teste").em(LocalDate.of(2026, 12, 19), "PR", "Londrina")).isTrue();
    }
}
