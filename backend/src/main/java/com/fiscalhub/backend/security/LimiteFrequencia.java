package com.fiscalhub.backend.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de frequência por usuário e operação, em janela deslizante de 1 minuto, em memória. Estourou: 429 em
 * ProblemDetail (GlobalExceptionHandler), com Retry-After.
 * ponytail: memória local; com mais de uma instância do backend o limite vale por instância — mover para o banco ou um
 * Redis quando houver mais de uma instância.
 */
@Component
public class LimiteFrequencia {

    public static final class Excedido extends RuntimeException {
        private final long segundos;

        Excedido(String operacao, int limite, long segundos) {
            super("Limite de " + limite + " " + operacao + " por minuto atingido. Tente de novo em " + segundos + " s.");
            this.segundos = segundos;
        }

        public long segundos() {
            return segundos;
        }
    }

    private static final long JANELA_MS = 60_000;
    private final Map<String, Deque<Long>> chamadas = new ConcurrentHashMap<>();
    private final Clock relogio;

    public LimiteFrequencia() {
        this(Clock.systemUTC());
    }

    LimiteFrequencia(Clock relogio) {
        this.relogio = relogio;
    }

    /** Registra a chamada ou lança Excedido. operacao: texto da mensagem ("emissões", "prévias"). */
    public void verificar(String operacao, long usuarioId, int limitePorMinuto) {
        long agora = relogio.millis();
        Deque<Long> fila = chamadas.computeIfAbsent(operacao + ":" + usuarioId, k -> new ArrayDeque<>());
        synchronized (fila) {
            while (!fila.isEmpty() && fila.peekFirst() <= agora - JANELA_MS) {
                fila.pollFirst();
            }
            if (fila.size() >= limitePorMinuto) {
                throw new Excedido(operacao, limitePorMinuto, Math.max(1, (fila.peekFirst() + JANELA_MS - agora + 999) / 1000));
            }
            fila.addLast(agora);
        }
    }
}
