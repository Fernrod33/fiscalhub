package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.enums.Papel;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Erros saem como ProblemDetail (RFC 9457). */
@TesteIntegracao
@Transactional
class ErrosProblemDetailTest {

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;

    private RequestPostProcessor token;

    @BeforeEach
    void setUp() {
        token = dados.sessao(dados.usuario(dados.escritorio("Erros"), "erro@test.com", Papel.ADMIN));
    }

    @Test
    void rotaInexistenteResponde404() throws Exception {
        mvc.perform(get("/api/rota-que-nao-existe").with(token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void validacaoResponde400ComCampos() throws Exception {
        mvc.perform(post("/api/clientes").with(token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cnpj\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Dados inválidos."))
                .andExpect(jsonPath("$.errors.razaoSocial").exists())
                .andExpect(jsonPath("$.errors.cnpj").exists());
    }

    @Test
    void recursoNaoEncontradoResponde404ComDetalhe() throws Exception {
        mvc.perform(get("/api/clientes/999999").with(token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void semTokenResponde401EmProblemDetail() throws Exception {
        mvc.perform(get("/api/clientes/minhas"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }
}
