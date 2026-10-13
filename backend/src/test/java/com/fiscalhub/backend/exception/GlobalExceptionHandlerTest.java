package com.fiscalhub.backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void erroInesperadoNaoVazaMensagemInternaEDevolveIdDeCorrelacao() {
        ProblemDetail pd = new GlobalExceptionHandler().inesperado(new IllegalStateException("senha do banco: xyz"));

        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getDetail()).doesNotContain("senha").doesNotContain("xyz");
        String correlacao = (String) pd.getProperties().get("correlacao");
        assertThat(correlacao).isNotBlank();
        assertThat(pd.getDetail()).contains(correlacao);
    }
}
