package com.fiscalhub.backend.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** ProblemDetail escrito direto no response, para erros que acontecem nos filtros (antes do MVC). */
final class RespostaProblema {

    static final String SENHA_TROCA_OBRIGATORIA = "/problemas/senha-troca-obrigatoria";

    private RespostaProblema() {
    }

    /** Textos fixos do próprio código (sem entrada do usuário), por isso sem escape. */
    static void escrever(HttpServletResponse res, int status, String type, String title, String detail) throws IOException {
        res.setStatus(status);
        res.setContentType("application/problem+json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"type\":\"" + type + "\",\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\"}");
    }
}
