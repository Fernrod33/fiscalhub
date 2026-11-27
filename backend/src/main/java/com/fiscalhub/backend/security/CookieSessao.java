package com.fiscalhub.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.util.Optional;

/**
 * O JWT viaja só neste cookie: httpOnly (o JS da página não lê), SameSite=Lax e Secure
 * (desligável em dev, onde tudo roda em http). Alterações de estado exigem também o token CSRF.
 */
@Component
public class CookieSessao {

    public static final String NOME = "fiscalhub_sessao";

    private final boolean seguro;
    private final Duration validade;

    public CookieSessao(@Value("${app.cookie.secure}") boolean seguro, @Value("${app.jwt.expiration-ms}") long validadeMs) {
        this.seguro = seguro;
        this.validade = Duration.ofMillis(validadeMs);
    }

    public void emitir(HttpServletResponse response, String jwt) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(jwt, validade).toString());
    }

    public void limpar(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    public Optional<String> ler(HttpServletRequest request) {
        return Optional.ofNullable(WebUtils.getCookie(request, NOME)).map(c -> c.getValue()).filter(v -> !v.isBlank());
    }

    private ResponseCookie cookie(String valor, Duration maxAge) {
        return ResponseCookie.from(NOME, valor).httpOnly(true).secure(seguro).sameSite("Lax").path("/api").maxAge(maxAge).build();
    }
}
