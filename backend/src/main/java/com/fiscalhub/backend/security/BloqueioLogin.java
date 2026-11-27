package com.fiscalhub.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloqueio progressivo de login (ASVS 2.2.1), por conta e por IP: depois de {@code limiar} falhas seguidas, cada nova
 * falha dobra a espera (1, 2, 4... minutos, até 1 hora). Sucesso zera a conta; o IP só esquece depois de 1 hora sem
 * falha (senão quem tem uma conta válida zeraria o contador e seguiria tentando outras). A espera é temporária: um
 * atacante não tranca a conta de ninguém para sempre.
 * ponytail: em memória (uma instância do backend, como o compose); reinício zera os contadores. Banco ou Redis se houver
 * mais de uma instância.
 */
@Component
public class BloqueioLogin {

    /** Tentativa durante o bloqueio: 429 com Retry-After; o motivo vai para a auditoria do LOGIN. */
    public static final class Bloqueado extends LimiteFrequencia.Excedido implements com.fiscalhub.backend.auditoria.DetalheAuditoria {
        private final String alvo;

        Bloqueado(String alvo, long segundos) {
            super("Muitas tentativas de entrada. Tente de novo em " + Math.max(1, (segundos + 59) / 60) + " min.", segundos);
            this.alvo = alvo;
        }

        public String alvo() {
            return alvo;
        }

        @Override
        public java.util.Map<String, Object> detalhesAuditoria() {
            return java.util.Map.of("motivo", "BLOQUEADO", "bloqueio", alvo, "segundos", segundos());
        }
    }

    private record Estado(int falhas, long bloqueadoAte, long ultimaFalha) {
    }

    private static final long MINUTO = 60_000;
    private static final long ESPERA_MAXIMA = 60 * MINUTO;

    private final Map<String, Estado> estados = new ConcurrentHashMap<>();
    private final int limiarConta;
    private final int limiarIp;
    private final Clock relogio;

    @org.springframework.beans.factory.annotation.Autowired
    public BloqueioLogin(@Value("${fiscalhub.login.falhas-por-conta:5}") int limiarConta,
                         @Value("${fiscalhub.login.falhas-por-ip:20}") int limiarIp) {
        this(limiarConta, limiarIp, Clock.systemUTC());
    }

    BloqueioLogin(int limiarConta, int limiarIp, Clock relogio) {
        this.limiarConta = limiarConta;
        this.limiarIp = limiarIp;
        this.relogio = relogio;
    }

    /** Antes de conferir a senha (ou o código): lança Bloqueado se a conta ou o IP estão na espera. */
    public void verificar(String email, String ip) {
        long agora = relogio.millis();
        for (String chave : new String[]{conta(email), "ip:" + ip}) {
            Estado e = estados.get(chave);
            if (e != null && e.bloqueadoAte() > agora) {
                throw new Bloqueado(chave.startsWith("ip:") ? "IP" : "CONTA", (e.bloqueadoAte() - agora + 999) / 1000);
            }
        }
    }

    /** Falha de senha ou de código. */
    public void registrarFalha(String email, String ip) {
        falha(conta(email), limiarConta);
        falha("ip:" + ip, limiarIp);
    }

    public void registrarSucesso(String email) {
        estados.remove(conta(email));
    }

    private void falha(String chave, int limiar) {
        long agora = relogio.millis();
        estados.compute(chave, (k, e) -> {
            int falhas = (e == null || agora - e.ultimaFalha() > ESPERA_MAXIMA ? 0 : e.falhas()) + 1;
            long espera = falhas < limiar ? 0 : Math.min(ESPERA_MAXIMA, MINUTO << Math.min(falhas - limiar, 6));
            return new Estado(falhas, agora + espera, agora);
        });
    }

    private static String conta(String email) {
        return "conta:" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }

    @Scheduled(fixedDelay = 600_000)
    void limpar() {
        long limite = relogio.millis() - ESPERA_MAXIMA;
        estados.values().removeIf(e -> e.ultimaFalha() < limite && e.bloqueadoAte() < relogio.millis());
    }
}
