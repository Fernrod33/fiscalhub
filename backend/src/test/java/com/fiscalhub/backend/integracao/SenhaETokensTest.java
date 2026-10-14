package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Escritorio;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.repository.UsuarioRepository;
import com.fiscalhub.backend.security.CookieSessao;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
        RequestPostProcessor provisorio = dados.sessao(novo);

        mvc.perform(get("/api/clientes").with(provisorio))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problemas/senha-troca-obrigatoria"));
        mvc.perform(get("/api/usuarios/me").with(provisorio)).andExpect(status().isOk());

        Cookie novaSessao = mvc.perform(put("/api/usuarios/me/senha").with(provisorio)
                        .contentType(APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"" + DadosTeste.SENHA + "\",\"novaSenha\":\"definitiva1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieSessao.NOME);

        mvc.perform(get("/api/clientes").with(provisorio)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/clientes").cookie(novaSessao)).andExpect(status().isOk());
    }

    @Test
    void trocaDeSenhaInvalidaTokensAnteriores() throws Exception {
        RequestPostProcessor antigo = dados.sessao(admin);
        mvc.perform(put("/api/usuarios/me/senha").with(antigo).contentType(APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"" + DadosTeste.SENHA + "\",\"novaSenha\":\"definitiva1\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/usuarios/me").with(antigo)).andExpect(status().isUnauthorized());
    }

    @Test
    void mudarPapelOuDesativarInvalidaOTokenDoAfetado() throws Exception {
        Usuario consultor = dados.usuario(escritorio, "cons-tok@test.com", Papel.CONSULTOR);
        RequestPostProcessor tokenConsultor = dados.sessao(consultor);
        RequestPostProcessor tokenAdmin = dados.sessao(admin);

        mvc.perform(patch("/api/usuarios/{id}/papel", consultor.getId()).with(tokenAdmin)
                .contentType(APPLICATION_JSON).content("{\"papel\":\"ASSISTENTE\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/usuarios/me").with(tokenConsultor)).andExpect(status().isUnauthorized());

        RequestPostProcessor tokenAssistente = dados.sessao(consultor);
        mvc.perform(patch("/api/usuarios/{id}/desativar", consultor.getId()).with(tokenAdmin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/usuarios/me").with(tokenAssistente)).andExpect(status().isUnauthorized());
        // o token do ADMIN que fez as mudanças segue valendo
        mvc.perform(get("/api/usuarios/me").with(tokenAdmin)).andExpect(status().isOk());
    }
}
