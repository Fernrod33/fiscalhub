package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Escritorio;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.repository.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Senha provisória bloqueia a API até a troca; troca de senha, papel e desativação invalidam tokens antigos. */
@TesteIntegracao
@Transactional
class SenhaETokensTest {

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired UsuarioRepository usuarioRepository;

    private Escritorio escritorio;
    private Usuario admin;

    @BeforeEach
    void setUp() {
        escritorio = dados.escritorio("Tokens");
        admin = dados.usuario(escritorio, "admin-tok@test.com", Papel.ADMIN);
    }

    @Test
    void senhaProvisoriaBloqueiaTudoMenosVerOProprioCadastroETrocarASenha() throws Exception {
        Usuario novo = dados.usuario(escritorio, "novo-tok@test.com", Papel.CONSULTOR);
        novo.setTrocarSenhaNoProximoLogin(true);
        usuarioRepository.saveAndFlush(novo);
        String provisorio = dados.token(novo);

        mvc.perform(get("/api/clientes").header(AUTHORIZATION, provisorio))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problemas/senha-troca-obrigatoria"));
        mvc.perform(get("/api/usuarios/me").header(AUTHORIZATION, provisorio)).andExpect(status().isOk());

        String resposta = mvc.perform(put("/api/usuarios/me/senha").header(AUTHORIZATION, provisorio)
                        .contentType(APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"" + DadosTeste.SENHA + "\",\"novaSenha\":\"definitiva1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String novoToken = "Bearer " + JsonPath.read(resposta, "$.token");

        mvc.perform(get("/api/clientes").header(AUTHORIZATION, provisorio)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/clientes").header(AUTHORIZATION, novoToken)).andExpect(status().isOk());
    }

    @Test
    void trocaDeSenhaInvalidaTokensAnteriores() throws Exception {
        String antigo = dados.token(admin);
        mvc.perform(put("/api/usuarios/me/senha").header(AUTHORIZATION, antigo).contentType(APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"" + DadosTeste.SENHA + "\",\"novaSenha\":\"definitiva1\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/usuarios/me").header(AUTHORIZATION, antigo)).andExpect(status().isUnauthorized());
    }

    @Test
    void mudarPapelOuDesativarInvalidaOTokenDoAfetado() throws Exception {
        Usuario consultor = dados.usuario(escritorio, "cons-tok@test.com", Papel.CONSULTOR);
        String tokenConsultor = dados.token(consultor);
        String tokenAdmin = dados.token(admin);

        mvc.perform(patch("/api/usuarios/{id}/papel", consultor.getId()).header(AUTHORIZATION, tokenAdmin)
                .contentType(APPLICATION_JSON).content("{\"papel\":\"ASSISTENTE\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/usuarios/me").header(AUTHORIZATION, tokenConsultor)).andExpect(status().isUnauthorized());

        String tokenAssistente = dados.token(consultor);
        mvc.perform(patch("/api/usuarios/{id}/desativar", consultor.getId()).header(AUTHORIZATION, tokenAdmin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/usuarios/me").header(AUTHORIZATION, tokenAssistente)).andExpect(status().isUnauthorized());
        // o token do ADMIN que fez as mudanças segue valendo
        mvc.perform(get("/api/usuarios/me").header(AUTHORIZATION, tokenAdmin)).andExpect(status().isOk());
    }
}
