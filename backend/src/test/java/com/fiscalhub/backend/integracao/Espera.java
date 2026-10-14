package com.fiscalhub.backend.integracao;

import java.util.function.BooleanSupplier;

/** Espera simples pelo processamento assíncrono dos jobs. */
final class Espera {

    private Espera() {
    }

    static void aguardar(BooleanSupplier condicao) {
        aguardar(condicao, 10_000);
    }

    static void aguardar(BooleanSupplier condicao, long limiteMs) {
        long limite = System.currentTimeMillis() + limiteMs;
        while (!condicao.getAsBoolean()) {
            if (System.currentTimeMillis() > limite) {
                throw new AssertionError("Condição não atingida em " + limiteMs / 1000 + " s");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }
}
