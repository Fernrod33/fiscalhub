package com.fiscalhub.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Limite global por IP nas rotas de autenticação (/api/auth/**: login, segundo fator, cadastro, CSRF), antes de qualquer
 * processamento (ASVS 2.2.1, 11.1.4). O bloqueio por conta e por IP depois de falhas é o BloqueioLogin.
 */
@Component
public class LimiteAutenticacao extends OncePerRequestFilter {

    private final LimiteFrequencia limite;
    private final int porMinuto;

    public LimiteAutenticacao(LimiteFrequencia limite, @Value("${fiscalhub.login.requisicoes-por-minuto-por-ip:30}") int porMinuto) {
        this.limite = limite;
        this.porMinuto = porMinuto;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            limite.verificar("requisições de autenticação", request.getRemoteAddr(), porMinuto);
        } catch (LimiteFrequencia.Excedido e) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(e.segundos()));
            RespostaProblema.escrever(response, 429, "about:blank", "Too Many Requests",
                    "Muitas requisições de autenticação deste endereço. Tente de novo em instantes.");
            return;
        }
        chain.doFilter(request, response);
    }
}
