package com.fiscalhub.backend.relatorio;

import com.fiscalhub.backend.relatorio.ModeloRelatorio.Formato;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatação pt-BR dos relatórios: a mesma regra do frontend (lib/formatadores.ts, Intl pt-BR), conferida pelos casos
 * de frontend/src/lib/formatacao.casos.json nos testes dos dois lados. Nulo vira "—".
 */
public final class Formatos {

    public static final String VAZIO = "—";
    private static final DecimalFormatSymbols PT_BR = DecimalFormatSymbols.getInstance(Locale.of("pt", "BR"));
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm");

    private Formatos() {
    }

    /** -1234.5 -> "-R$ 1.234,50" (espaço não separável, como o Intl). */
    public static String moeda(BigDecimal v) {
        return v == null ? VAZIO : (v.signum() < 0 ? "-" : "") + "R$ " + decimal(v.abs());
    }

    /** 1234.5 -> "1.234,50". */
    public static String decimal(BigDecimal v) {
        return v == null ? VAZIO : formato("#,##0.00").format(v.setScale(2, RoundingMode.HALF_EVEN));
    }

    /** Percentual como na API (12.5 = 12,5%) -> "12,50%". */
    public static String percentual(BigDecimal v) {
        return v == null ? VAZIO : formato("#,##0.00").format(v.setScale(2, RoundingMode.HALF_EVEN)) + "%";
    }

    public static String inteiro(BigDecimal v) {
        return v == null ? VAZIO : formato("#,##0").format(v.setScale(0, RoundingMode.HALF_EVEN));
    }

    /** "2026-08" ou "2026-08-01" -> "08/2026". */
    public static String competencia(String iso) {
        return iso == null || iso.length() < 7 ? VAZIO : iso.substring(5, 7) + "/" + iso.substring(0, 4);
    }

    public static String data(LocalDate d) {
        return d == null ? VAZIO : DATA.format(d);
    }

    public static String dataHora(LocalDateTime d) {
        return d == null ? VAZIO : DATA_HORA.format(d);
    }

    /** "11222333000181" -> "11.222.333/0001-81"; outro tamanho volta como veio. */
    public static String cnpj(String v) {
        String d = v == null ? "" : v.replaceAll("\\D", "");
        return d.length() == 14 ? d.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5")
                : v == null || v.isBlank() ? VAZIO : v;
    }

    /** Célula de tabela ou indicador: o texto cru pelo formato da coluna; texto que não é número sai como veio. */
    public static String celula(String cru, Formato f) {
        if (cru == null || cru.isEmpty()) {
            return VAZIO;
        }
        BigDecimal n = numero(cru);
        return switch (f) {
            case MOEDA -> n == null ? cru : moeda(n);
            case PERCENTUAL -> n == null ? cru : percentual(n);
            case INTEIRO -> n == null ? cru : inteiro(n);
            case COMPETENCIA -> cru.matches("\\d{4}-\\d{2}.*") ? competencia(cru) : cru;
            case DATA -> cru.matches("\\d{4}-\\d{2}-\\d{2}") ? data(LocalDate.parse(cru)) : cru;
            case CNPJ -> cnpj(cru);
            case TEXTO -> cru;
        };
    }

    /** Texto cru numérico ("1234.50", "-3") ou null. */
    public static BigDecimal numero(String cru) {
        return cru != null && cru.matches("-?\\d+(\\.\\d+)?") ? new BigDecimal(cru) : null;
    }

    public static boolean numerico(Formato f) {
        return f == Formato.MOEDA || f == Formato.PERCENTUAL || f == Formato.INTEIRO;
    }

    private static DecimalFormat formato(String padrao) {
        return new DecimalFormat(padrao, PT_BR); // DecimalFormat não é thread-safe: um por chamada
    }
}
