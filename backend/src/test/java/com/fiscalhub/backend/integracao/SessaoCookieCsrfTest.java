package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.security.CookieSessao;
import com.fiscalhub.backend.security.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sessão em cookie httpOnly + CSRF (cookie XSRF-TOKEN devolvido no cabeçalho X-XSRF-TOKEN). */
@TesteIntegracao
@Transactional
class SessaoCookieCsrfTest {

    private static final String NOVO_CLIENTE = "{\"razaoSocial\":\"Cliente CSRF\",\"cnpj\":\"11222333000181\"}";

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired JwtUtils jwtUtils;

    private Usuario admin;
    private Cookie sessao;

    @BeforeEach
    void setUp() {
        admin = dados.usuario(dados.escritorio("Cookies"), "admin-cookie@test.com", Papel.ADMIN);
        sessao = new Cookie(CookieSessao.NOME, jwtUtils.gerarToken(admin));
    }

    @Test
    void loginEmiteCookieHttpOnlySecureLaxESemTokenNoCorpo() throws Exception {
        var resposta = mvc.perform(post("/api/auth/login").with(DadosTeste.CSRF).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"admin-cookie@test.com\",\"senha\":\"" + DadosTeste.SENHA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin-cookie@test.com"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn().getResponse();

        assertThat(resposta.getHeaders(HttpHeaders.SET_COOKIE)).anySatisfy(c -> assertThat(c)
                .startsWith(CookieSessao.NOME + "=")
                .contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api", "Max-Age=86400"));
    }

    @Test
    void alteracaoSemCsrfRecebe403MesmoComSessao() throws Exception {
        mvc.perform(post("/api/clientes").cookie(sessao).contentType(APPLICATION_JSON).content(NOVO_CLIENTE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Token CSRF ausente ou inválido. Recarregue a página."));
        mvc.perform(get("/api/clientes").cookie(sessao)).andExpect(status().isOk()); // leitura não exige CSRF
    }

    @Test
    void alteracaoComCookieECabecalhoCsrfPassa() throws Exception {
        String setCookie = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElse("");
        assertThat(setCookie).startsWith("XSRF-TOKEN=").doesNotContain("HttpOnly"); // o JS precisa ler o valor
        Cookie xsrf = new Cookie("XSRF-TOKEN", setCookie.substring("XSRF-TOKEN=".length(), setCookie.indexOf(';')));

        var criado = mvc.perform(post("/api/clientes").cookie(sessao, xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(APPLICATION_JSON).content(NOVO_CLIENTE))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        // o token continua valendo: requisição autenticada não apaga o cookie XSRF-TOKEN
        assertThat(criado.getHeaders(HttpHeaders.SET_COOKIE)).noneMatch(c -> c.startsWith("XSRF-TOKEN="));
        mvc.perform(post("/api/clientes").cookie(sessao, xsrf).header("X-XSRF-TOKEN", "outro-valor")
                        .contentType(APPLICATION_JSON).content(NOVO_CLIENTE))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutApagaOCookieEInvalidaOToken() throws Exception {
        var resposta = mvc.perform(post("/api/auth/logout").cookie(sessao).with(DadosTeste.CSRF))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();
        assertThat(resposta.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(c -> assertThat(c).startsWith(CookieSessao.NOME + "=;").contains("Max-Age=0"));

        // uma cópia do JWT feita antes do logout também deixa de valer
        mvc.perform(get("/api/usuarios/me").cookie(sessao)).andExpect(status().isUnauthorized());
    }
}
