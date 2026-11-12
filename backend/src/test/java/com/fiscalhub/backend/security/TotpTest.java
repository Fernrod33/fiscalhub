package com.fiscalhub.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class TotpTest {

    /** Para os testes de integração calcularem o código como o aplicativo. */
    public static String codigo(String segredoBase32, long passo) {
        return Totp.codigo(Totp.decodificar(segredoBase32), passo);
    }

    @Test
    void vetorDaRfc6238() {
        // RFC 6238, apêndice B (SHA1, segredo "12345678901234567890"): T = 59 s → 94287082; 6 dígitos = 287082
        byte[] segredo = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertThat(Totp.codigo(segredo, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.codigo(segredo, 1111111109L / 30)).isEqualTo("081804");
    }

    @Test
    void base32IdaEVoltaEFolgaDeUmPassoSemReuso() {
        String segredo = Totp.novoSegredo();
        assertThat(segredo).hasSize(32).matches("[A-Z2-7]+");
        Instant agora = Instant.ofEpochSecond(1_800_000_000L);
        long passo = agora.getEpochSecond() / 30;
        assertThat(Totp.validar(segredo, codigo(segredo, passo - 1), null, agora)).hasValue(passo - 1);
        assertThat(Totp.validar(segredo, codigo(segredo, passo), passo, agora)).isEmpty(); // já usado
        assertThat(Totp.validar(segredo, codigo(segredo, passo + 2), null, agora)).isEmpty(); // fora da folga
        assertThat(Totp.validar(segredo, "12345", null, agora)).isEmpty();
    }

    @Test
    void cifraDadosIdaEVoltaComNonceNovo() {
        CifraDados cifra = new CifraDados("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=");
        String a = cifra.cifrar("JBSWY3DPEHPK3PXP");
        assertThat(a).startsWith("v1:").isNotEqualTo(cifra.cifrar("JBSWY3DPEHPK3PXP"));
        assertThat(cifra.decifrar(a)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void bloqueioProgressivoDobraAEsperaEZeraComSucesso() {
        long[] agora = {0};
        java.time.Clock relogio = new java.time.Clock() {
            public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
            public java.time.Clock withZone(java.time.ZoneId z) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(agora[0]); }
        };
        BloqueioLogin b = new BloqueioLogin(3, 100, relogio);
        b.registrarFalha("a@a.com", "1.1.1.1");
        b.registrarFalha("a@a.com", "1.1.1.1");
        b.verificar("a@a.com", "1.1.1.1"); // 2 falhas: ainda livre
        b.registrarFalha("a@a.com", "1.1.1.1");
        assertThat(segundos(b)).isEqualTo(60);
        agora[0] += 61_000;
        b.registrarFalha("A@a.com ", "1.1.1.1"); // mesma conta (normalizada)
        assertThat(segundos(b)).isEqualTo(120);
        agora[0] += 121_000;
        b.registrarSucesso("a@a.com");
        b.verificar("a@a.com", "1.1.1.1");
    }

    private static long segundos(BloqueioLogin b) {
        try {
            b.verificar("a@a.com", "9.9.9.9");
            return 0;
        } catch (BloqueioLogin.Bloqueado e) {
            assertThat(e.alvo()).isEqualTo("CONTA");
            return e.segundos();
        }
    }

    @Test
    void limiteDeAutenticacaoPorIpResponde429SoNasRotasDeAuth() throws Exception {
        LimiteAutenticacao filtro = new LimiteAutenticacao(new LimiteFrequencia(), 2);
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filtro.doFilter(requisicao("/api/auth/login", "10.0.0.1"), res, new MockFilterChain());
            assertThat(res.getStatus()).isEqualTo(i < 2 ? 200 : 429);
        }
        MockHttpServletResponse outroIp = new MockHttpServletResponse();
        filtro.doFilter(requisicao("/api/auth/login", "10.0.0.2"), outroIp, new MockFilterChain());
        assertThat(outroIp.getStatus()).isEqualTo(200);
        MockHttpServletResponse foraDoAuth = new MockHttpServletResponse();
        filtro.doFilter(requisicao("/api/clientes", "10.0.0.1"), foraDoAuth, new MockFilterChain());
        assertThat(foraDoAuth.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletRequest requisicao(String uri, String ip) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", uri);
        r.setRemoteAddr(ip);
        return r;
    }

    @Test
    void senhaForte() {
        assertThat(SenhaForte.Validador.problema("curta")).contains("12");
        assertThat(SenhaForte.Validador.problema("QwertyUiop12")).contains("vazadas");
        assertThat(SenhaForte.Validador.problema("cavalo correto bateria grampo")).isNull();
        assertThat(SenhaForte.Validador.problema("ç".repeat(40))).contains("no máximo"); // 80 bytes: acima do limite do BCrypt
    }
}
