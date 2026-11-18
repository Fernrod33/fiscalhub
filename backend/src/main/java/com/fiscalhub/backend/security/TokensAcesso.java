package com.fiscalhub.backend.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Tokens de uso único dos links enviados por e-mail (256 bits aleatórios; no banco, só o SHA-256). Um token novo
 * invalida os anteriores do mesmo tipo; consumir é atômico (só um pedido vence).
 */
@Component
public class TokensAcesso {

    public enum Tipo {
        CONVITE(Duration.ofHours(72)), RECUPERACAO(Duration.ofMinutes(30));

        final Duration validade;

        Tipo(Duration validade) {
            this.validade = validade;
        }
    }

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final JdbcTemplate jdbc;

    public TokensAcesso(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Devolve o token em claro (vai só no link do e-mail). */
    public String emitir(long usuarioId, Tipo tipo) {
        jdbc.update("UPDATE token_acesso SET usado_em = now() WHERE usuario_id = ? AND tipo = ? AND usado_em IS NULL", usuarioId, tipo.name());
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO token_acesso (usuario_id, tipo, hash, expira_em) VALUES (?, ?, ?, now() + make_interval(secs => ?))",
                usuarioId, tipo.name(), SegurancaContaService.sha256(token), tipo.validade.toSeconds());
        return token;
    }

    /** Dono de um token válido, sem consumi-lo (para conferir o 2º fator antes). */
    public Optional<Long> dono(Tipo tipo, String token) {
        return primeiro(jdbc.queryForList("SELECT usuario_id FROM token_acesso WHERE hash = ? AND tipo = ? AND usado_em IS NULL AND expira_em > now()",
                Long.class, hash(token), tipo.name()));
    }

    public Optional<Long> consumir(Tipo tipo, String token) {
        return primeiro(jdbc.queryForList("""
                UPDATE token_acesso SET usado_em = now() WHERE hash = ? AND tipo = ? AND usado_em IS NULL AND expira_em > now()
                RETURNING usuario_id""", Long.class, hash(token), tipo.name()));
    }

    /** Há um token ainda válido emitido há menos de {@code intervalo} (limite de envios por conta). */
    public boolean emitidoHaMenosDe(long usuarioId, Tipo tipo, Duration intervalo) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM token_acesso WHERE usuario_id = ? AND tipo = ? AND usado_em IS NULL AND expira_em > now()
                               AND criado_em > now() - make_interval(secs => ?))""", Boolean.class, usuarioId, tipo.name(), intervalo.toSeconds()));
    }

    /** Tokens vencidos ou usados há mais de 30 dias saem do banco. */
    @Scheduled(cron = "0 40 3 * * *")
    public void limpar() {
        jdbc.update("DELETE FROM token_acesso WHERE expira_em < now() - interval '30 days'");
    }

    private static String hash(String token) {
        return SegurancaContaService.sha256(token == null ? "" : token);
    }

    private static Optional<Long> primeiro(List<Long> ids) {
        return ids.stream().findFirst();
    }
}
