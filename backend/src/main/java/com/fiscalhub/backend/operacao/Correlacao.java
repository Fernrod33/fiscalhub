package com.fiscalhub.backend.operacao;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Id de correlação por requisição: o X-Request-Id do proxy (Caddy) ou um UUID novo. Vai para o MDC (todo log da
 * requisição o leva, inclusive no JSON do perfil prod), para a resposta e para o ProblemDetail do erro 500.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class Correlacao extends OncePerRequestFilter {

    public static final String MDC_CHAVE = "correlacao";
    static final String CABECALHO = "X-Request-Id";
    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9-]{8,64}"); // o cabeçalho vem de fora: nada de injeção no log

    public static String atual() {
        String id = MDC.get(MDC_CHAVE);
        return id != null ? id : UUID.randomUUID().toString();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recebido = request.getHeader(CABECALHO);
        String id = recebido != null && VALIDO.matcher(recebido).matches() ? recebido : UUID.randomUUID().toString();
        MDC.put(MDC_CHAVE, id);
        response.setHeader(CABECALHO, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CHAVE);
        }
    }
}
