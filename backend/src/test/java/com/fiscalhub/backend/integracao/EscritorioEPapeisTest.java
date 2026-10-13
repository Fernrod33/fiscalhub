package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Escritorio;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Signup cria escritório + ADMIN; gestão de usuários só pelo ADMIN; último ADMIN não sai; token de desativado cai. */
@TesteIntegracao
@Transactional
class EscritorioEPapeisTest {

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired UsuarioRepository usuarioRepository;

    private Escritorio escritorio;
    private Usuario admin;

    @BeforeEach
    void setUp() {
        escritorio = dados.escritorio("Escritorio Teste");
        admin = dados.usuario(escritorio, "admin@test.com", Papel.ADMIN);
    }

    @Test
    void signupCriaEscritorioEPrimeiroUsuarioComoAdmin() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(APPLICATION_JSON).content("""
                        {"nome":"Ana","email":"ana@novo.com","senha":"segredo1",
                         "nomeEscritorio":"Ana Advocacia","documentoEscritorio":"11.222.333/0001-81"}"""))
                .andExpect(status().isOk());

        Usuario ana = usuarioRepository.findByEmail("ana@novo.com").orElseThrow();
        assertThat(ana.getPapel()).isEqualTo(Papel.ADMIN);
        assertThat(ana.getEscritorio().getNome()).isEqualTo("Ana Advocacia");
        assertThat(ana.getEscritorio().getDocumento()).isEqualTo("11222333000181");

        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"ana@novo.com\",\"senha\":\"segredo1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("ADMIN"))
                .andExpect(jsonPath("$.escritorioId").value(ana.getEscritorio().getId().intValue()));
    }

    @Test
    void signupComDocumentoInvalidoNaoCriaNada() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(APPLICATION_JSON).content("""
                        {"nome":"Bia","email":"bia@novo.com","senha":"segredo1",
                         "nomeEscritorio":"X","documentoEscritorio":"123.456.789-00"}"""))
                .andExpect(status().isBadRequest());
        assertThat(usuarioRepository.findByEmail("bia@novo.com")).isEmpty();
    }

    @Test
    void adminCriaUsuarioComSenhaProvisoriaQueDeveSerTrocada() throws Exception {
        mvc.perform(post("/api/usuarios").header(AUTHORIZATION, dados.token(admin)).contentType(APPLICATION_JSON)
                        .content("{\"nome\":\"Caio\",\"email\":\"caio@test.com\",\"papel\":\"CONSULTOR\",\"senhaProvisoria\":\"provisoria1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("CONSULTOR"))
                .andExpect(jsonPath("$.trocarSenhaNoProximoLogin").value(true))
                .andExpect(jsonPath("$.senha").doesNotExist());

        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"caio@test.com\",\"senha\":\"provisoria1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trocarSenhaNoProximoLogin").value(true));

        Usuario caio = usuarioRepository.findByEmail("caio@test.com").orElseThrow();
        mvc.perform(put("/api/usuarios/me/senha").header(AUTHORIZATION, dados.token(caio)).contentType(APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"provisoria1\",\"novaSenha\":\"definitiva1\"}"))
                .andExpect(status().isNoContent());
        assertThat(caio.getTrocarSenhaNoProximoLogin()).isFalse();
    }

    @Test
    void consultorEAssistenteNaoGerenciamUsuarios() throws Exception {
        for (Papel papel : new Papel[]{Papel.CONSULTOR, Papel.ASSISTENTE}) {
            String token = dados.token(dados.usuario(escritorio, papel + "@test.com", papel));
            mvc.perform(get("/api/usuarios").header(AUTHORIZATION, token)).andExpect(status().isForbidden());
            mvc.perform(post("/api/usuarios").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                            .content("{\"nome\":\"X\",\"email\":\"x@test.com\",\"papel\":\"ADMIN\",\"senhaProvisoria\":\"provisoria1\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/usuarios/{id}/desativar", admin.getId()).header(AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminNaoGerenciaUsuarioDeOutroEscritorio() throws Exception {
        Usuario deFora = dados.usuario(dados.escritorio("Outro"), "fora@test.com", Papel.CONSULTOR);

        mvc.perform(patch("/api/usuarios/{id}/papel", deFora.getId()).header(AUTHORIZATION, dados.token(admin))
                        .contentType(APPLICATION_JSON).content("{\"papel\":\"ASSISTENTE\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/usuarios/{id}/desativar", deFora.getId()).header(AUTHORIZATION, dados.token(admin)))
                .andExpect(status().isNotFound());
        assertThat(deFora.getPapel()).isEqualTo(Papel.CONSULTOR);
        assertThat(deFora.getAtivo()).isTrue();
    }

    @Test
    void ultimoAdminAtivoNaoSaiNemPerdeOPapel() throws Exception {
        mvc.perform(delete("/api/usuarios/me").header(AUTHORIZATION, dados.token(admin)))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/usuarios/{id}/papel", admin.getId()).header(AUTHORIZATION, dados.token(admin))
                        .contentType(APPLICATION_JSON).content("{\"papel\":\"CONSULTOR\"}"))
                .andExpect(status().isBadRequest());
        assertThat(admin.getAtivo()).isTrue();
        assertThat(admin.getPapel()).isEqualTo(Papel.ADMIN);
    }

    @Test
    void usuarioDesativadoNaoLogaETokenAntigoDeixaDeValer() throws Exception {
        Usuario consultor = dados.usuario(escritorio, "consultor@test.com", Papel.CONSULTOR);
        String tokenAntigo = dados.token(consultor);

        mvc.perform(patch("/api/usuarios/{id}/desativar", consultor.getId()).header(AUTHORIZATION, dados.token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        mvc.perform(get("/api/usuarios/me").header(AUTHORIZATION, tokenAntigo)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"consultor@test.com\",\"senha\":\"" + DadosTeste.SENHA + "\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(usuarioRepository.findById(consultor.getId())).isPresent(); // soft delete
    }

    @Test
    void segundoAdminPodeSairETokenComPapelAntigoDeixaDeValer() throws Exception {
        Usuario outroAdmin = dados.usuario(escritorio, "admin2@test.com", Papel.ADMIN);
        String tokenAntigo = dados.token(outroAdmin);

        mvc.perform(patch("/api/usuarios/{id}/papel", outroAdmin.getId()).header(AUTHORIZATION, dados.token(admin))
                        .contentType(APPLICATION_JSON).content("{\"papel\":\"ASSISTENTE\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/usuarios").header(AUTHORIZATION, tokenAntigo)).andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/usuarios/me").header(AUTHORIZATION, dados.token(outroAdmin)))
                .andExpect(status().isNoContent());
    }
}
