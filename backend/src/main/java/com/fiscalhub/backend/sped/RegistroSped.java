package com.fiscalhub.backend.sped;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Uma linha SPED lida pelo nome do campo (do leiaute da versão), com a linha do arquivo para apontar a origem. */
public final class RegistroSped {

    private final String registro;
    private final long linha;
    private final String[] campos;
    private final Map<String, Integer> indices;

    RegistroSped(String registro, long linha, String[] campos, Map<String, Integer> indices) {
        this.registro = registro;
        this.linha = linha;
        this.campos = campos;
        this.indices = indices;
    }

    public String registro() {
        return registro;
    }

    public long linha() {
        return linha;
    }

    /** Valor do campo sem espaços nas pontas; campo vazio ou inexistente nesta versão -> null. */
    public String texto(String campo) {
        Integer i = indices.get(campo);
        if (i == null || i >= campos.length) {
            return null;
        }
        String valor = campos[i].trim();
        return valor.isEmpty() ? null : valor;
    }

    public BigDecimal decimal(String campo) {
        try {
            return LeitorSped.decimal(texto(campo));
        } catch (NumberFormatException e) {
            throw new LeitorSped.LinhaInvalida(linha, campo + ": " + e.getMessage());
        }
    }

    public LocalDate data(String campo) {
        try {
            return LeitorSped.data(texto(campo));
        } catch (NumberFormatException e) {
            throw new LeitorSped.LinhaInvalida(linha, campo + ": " + e.getMessage());
        }
    }

    /** Valor + indicador D/C na convenção única do sistema: devedor positivo, credor negativo. Vazio -> zero. */
    public BigDecimal saldo(String campoValor, String campoIndicador) {
        BigDecimal valor = decimal(campoValor);
        return Saldos.comSinal(valor, texto(campoIndicador));
    }
}
