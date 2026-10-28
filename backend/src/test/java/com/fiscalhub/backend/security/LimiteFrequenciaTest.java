package com.fiscalhub.backend.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimiteFrequenciaTest {

    /** Relógio que o teste avança. */
    static final class Relogio extends Clock {
        Instant agora = Instant.parse("2026-10-28T12:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }
    }

    @Test
    void janelaDeUmMinutoPorUsuarioEOperacao() {
        Relogio relogio = new Relogio();
        LimiteFrequencia limite = new LimiteFrequencia(relogio);
        for (int i = 0; i < 3; i++) {
            limite.verificar("emissões", 1, 3);
        }
        assertThatThrownBy(() -> limite.verificar("emissões", 1, 3)).isInstanceOf(LimiteFrequencia.Excedido.class)
                .hasMessageContaining("3 emissões por minuto").satisfies(e -> assertThat(((LimiteFrequencia.Excedido) e).segundos()).isEqualTo(60));
        limite.verificar("emissões", 2, 3); // outro usuário
        limite.verificar("prévias", 1, 3);  // outra operação
        relogio.agora = relogio.agora.plus(Duration.ofSeconds(61));
        limite.verificar("emissões", 1, 3); // a janela andou
    }
}
