package com.fiscalhub.backend.sped;

import java.math.BigDecimal;

/**
 * Convenção de sinal única do sistema (banco, API e telas): devedor positivo, credor negativo.
 * Ativo e despesas costumam ser positivos; passivo, patrimônio líquido e receitas, negativos. A tela mostra o valor
 * absoluto com o indicador D/C.
 */
public final class Saldos {

    private Saldos() {
    }

    public static BigDecimal comSinal(BigDecimal valor, String indicadorDC) {
        if (valor == null) {
            return BigDecimal.ZERO;
        }
        return "C".equalsIgnoreCase(indicadorDC) ? valor.negate() : valor;
    }
}
