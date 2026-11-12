package com.fiscalhub.backend.operacao;

import com.fiscalhub.backend.storage.ArmazenamentoArquivos;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Readiness do storage: uma consulta de existência responde sem erro (bucket acessível ou diretório legível). */
@Component("armazenamento")
public class SaudeArmazenamento implements HealthIndicator {

    private final ArmazenamentoArquivos armazenamento;

    public SaudeArmazenamento(ArmazenamentoArquivos armazenamento) {
        this.armazenamento = armazenamento;
    }

    @Override
    public Health health() {
        armazenamento.existe("saude/verificacao"); // exceção = DOWN (o actuator transforma)
        return Health.up().build();
    }
}
