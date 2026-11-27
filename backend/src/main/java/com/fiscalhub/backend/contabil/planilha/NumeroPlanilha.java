package com.fiscalhub.backend.contabil.planilha;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valor de uma célula de balancete: "1.234,56", "1234.56" (número cru do .xlsx), "-1.234,56", "(1.234,56)" (negativo),
 * "1.234,56 D" / "1.234,56C" (indicador no próprio valor). Devolve o número com sinal e o indicador, se houver.
 */
final class NumeroPlanilha {

    record Valor(BigDecimal numero, String indicador) {
    }

    private static final Pattern FORMATO = Pattern.compile("^(\\()?\\s*(-)?\\s*R?\\$?\\s*([0-9.,]+)\\s*(\\))?\\s*([DdCc])?$");
    private static final Pattern MILHAR_PONTO = Pattern.compile("^\\d{1,3}(\\.\\d{3})+$");

    private NumeroPlanilha() {
    }

    /** null para célula vazia; texto que não é número: {@link IllegalArgumentException}. */
    static Valor ler(String celula) {
        if (celula == null || celula.isBlank() || celula.trim().equals("-")) {
            return null;
        }
        Matcher m = FORMATO.matcher(celula.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Valor fora do formato numérico: " + celula);
        }
        BigDecimal numero = new BigDecimal(normalizar(m.group(3)));
        if (m.group(1) != null || m.group(2) != null) {
            numero = numero.negate();
        }
        return new Valor(numero, m.group(5) == null ? null : m.group(5).toUpperCase());
    }

    /** Separador decimal: o último entre ponto e vírgula; só pontos em grupos de 3 = milhar. */
    private static String normalizar(String n) {
        int virgula = n.lastIndexOf(',');
        int ponto = n.lastIndexOf('.');
        if (virgula >= 0 && ponto >= 0) {
            return virgula > ponto ? n.replace(".", "").replace(',', '.') : n.replace(",", "");
        }
        if (virgula >= 0) {
            return n.replace(',', '.');
        }
        return MILHAR_PONTO.matcher(n).matches() ? n.replace(".", "") : n;
    }
}
