package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.repository.UsuarioRepository;
import com.fiscalhub.backend.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Erros saem como ProblemDetail (RFC 9457). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestcontainer.class)
@Transactional
class ErrosProblemDetailTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtils jwtUtils;
    @Autowired UsuarioRepository usuarioRepository;

    private String token;

    @BeforeEach
    void setUp() {
        Usuario u = new Usuario();
        u.setNome("Erro");
        u.setEmail("erro@test.com");
        u.setSenha("x");
        usuarioRepository.save(u);
        token = "Bearer " + jwtUtils.generateTokenFromUsername(u.getEmail());
    }

    @Test
    void rotaInexistenteResponde404() throws Exception {
        mvc.perform(get("/api/rota-que-nao-existe").header(AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void validacaoResponde400ComCampos() throws Exception {
        mvc.perform(post("/api/empresas").header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cnpj\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Dados inválidos."))
                .andExpect(jsonPath("$.errors.razaoSocial").exists())
                .andExpect(jsonPath("$.errors.cnpj").exists());
    }

    @Test
    void recursoNaoEncontradoResponde404ComDetalhe() throws Exception {
        mvc.perform(get("/api/empresas/999999").header(AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void semTokenResponde401EmProblemDetail() throws Exception {
        mvc.perform(get("/api/empresas/minhas"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }
}
