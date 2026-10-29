package com.fiscalhub.backend.calendario;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dias úteis, componente único para vencimentos: fim de semana, feriados nacionais fixos (Lei 662/1949, Lei 6.802/1980,
 * Lei 14.759/2023) e móveis (Carnaval — segunda e terça, feriado bancário —, Sexta-feira Santa e Corpus Christi, pela
 * Páscoa), e os feriados estaduais e municipais de {@code resources/calendario/feriados-locais.csv} (dado versionado,
 * com a fonte de cada linha). uf/municipio nulos: só os nacionais (vencimentos federais).
 */
@Component
public class CalendarioDiasUteis {

    private static final List<MonthDay> NACIONAIS = List.of(MonthDay.of(1, 1), MonthDay.of(4, 21), MonthDay.of(5, 1), MonthDay.of(9, 7),
            MonthDay.of(10, 12), MonthDay.of(11, 2), MonthDay.of(11, 15), MonthDay.of(11, 20), MonthDay.of(12, 25));

    /** data: MM-dd (todo ano) ou yyyy-MM-dd (um ano). municipio nulo: feriado do estado inteiro. */
    record FeriadoLocal(String uf, String municipio, String data, String nome, String fonte) {
        boolean em(LocalDate d, String uf, String municipio) {
            return this.uf.equalsIgnoreCase(uf) && (this.municipio == null || this.municipio.equalsIgnoreCase(Objects.toString(municipio, "")))
                    && (data.length() == 5 ? MonthDay.parse("--" + data).equals(MonthDay.from(d)) : LocalDate.parse(data).equals(d));
        }
    }

    private final List<FeriadoLocal> locais = new ArrayList<>();

    public CalendarioDiasUteis() {
        var in = getClass().getClassLoader().getResourceAsStream("calendario/feriados-locais.csv");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            r.lines().filter(l -> !l.isBlank() && !l.startsWith("#") && !l.startsWith("uf;")).map(l -> l.split(";", -1))
                    .forEach(c -> locais.add(new FeriadoLocal(c[0], c[1].isBlank() ? null : c[1], c[2], c[3], c[4])));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean util(LocalDate d, String uf, String municipio) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !feriado(d, uf, municipio);
    }

    public boolean feriado(LocalDate d, String uf, String municipio) {
        LocalDate pascoa = pascoa(d.getYear());
        return NACIONAIS.contains(MonthDay.from(d)) || d.equals(pascoa.minusDays(48)) || d.equals(pascoa.minusDays(47))
                || d.equals(pascoa.minusDays(2)) || d.equals(pascoa.plusDays(60))
                || (uf != null && locais.stream().anyMatch(f -> f.em(d, uf, municipio)));
    }

    /** O último dia útil do mês (vencimento das parcelas federais). */
    public LocalDate ultimoDiaUtil(YearMonth mes, String uf, String municipio) {
        LocalDate d = mes.atEndOfMonth();
        while (!util(d, uf, municipio)) {
            d = d.minusDays(1);
        }
        return d;
    }

    /** Dia útil anterior mais próximo (ou o próprio dia), como na antecipação dos vencimentos federais. */
    public LocalDate utilAnterior(LocalDate d, String uf, String municipio) {
        while (!util(d, uf, municipio)) {
            d = d.minusDays(1);
        }
        return d;
    }

    /** Domingo de Páscoa (algoritmo de Meeus/Jones/Butcher, calendário gregoriano). */
    static LocalDate pascoa(int ano) {
        int a = ano % 19, b = ano / 100, c = ano % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31, dia = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(ano, mes, dia);
    }
}
