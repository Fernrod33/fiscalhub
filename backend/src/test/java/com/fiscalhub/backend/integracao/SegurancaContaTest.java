package com.fiscalhub.backend.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.entity.Escritorio;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.security.CookieSessao;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tarefa 3 da Fase 7: política de senha, bloqueio progressivo, segundo fator, exigência do escritório e sessões. */
@TesteIntegracao
class SegurancaContaTest {

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private Escritorio escritorio;
    private Usuario admin;

    @BeforeEach
    void setUp() {
        escritorio = dados.escritorio("Segurança " + System.nanoTime());
        admin = dados.usuario(escritorio, "seg-" + System.nanoTime() + "@test.com", Papel.ADMIN);
    }

    @Test
    void senhaCurtaOuVazadaERecusadaNoCadastro() throws Exception {
        for (String[] caso : new String[][]{{"curta-123", "pelo menos 12"}, {"passwordpassword", "vazadas"}}) {
            mvc.perform(post("/api/auth/signup").with(DadosTeste.CSRF).contentType(APPLICATION_JSON).content("""
                            {"nome":"X","email":"x%d@novo.com","senha":"%s","nomeEscritorio":"X"}""".formatted(System.nanoTime(), caso[0])))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.senha").value(org.hamcrest.Matchers.containsString(caso[1])));
        }
    }

    @Test
    void cincoFalhasBloqueiamAContaMesmoComASenhaCertaEAuditam() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(admin.getEmail(), "senha-errada-123").andExpect(status().isUnauthorized());
        }
        login(admin.getEmail(), DadosTeste.SENHA)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM auditoria_evento WHERE acao = 'LOGIN' AND usuario_id = ?
                AND detalhes ->> 'resultado' = 'FALHA' AND detalhes ->> 'motivo' = 'BLOQUEADO'""", Long.class, admin.getId())).isOne();
    }

    @Test
    void segundoFatorAtivaPedeOCodigoNoLoginRecusaReusoEAceitaCodigoDeRecuperacaoUmaVez() throws Exception {
        admin = dados.semSegundoFator(admin);
        RequestPostProcessor sessao = dados.sessao(admin);
        JsonNode ativacao = corpo(mvc.perform(post("/api/usuarios/me/segundo-fator/iniciar").with(sessao)).andExpect(status().isOk()));
        String segredo = ativacao.get("segredo").asText();
        assertThat(ativacao.get("uri").asText()).startsWith("otpauth://totp/FiscalHub:").contains("secret=" + segredo);
        assertThat(ativacao.get("qrSvg").asText()).startsWith("<svg");

        long passo = Instant.now().getEpochSecond() / 30;
        JsonNode codigos = corpo(mvc.perform(post("/api/usuarios/me/segundo-fator/ativar").with(sessao).contentType(APPLICATION_JSON)
                .content("{\"codigo\":\"" + codigo(segredo, passo) + "\"}")).andExpect(status().isOk()));
        assertThat(codigos.get("codigos")).hasSize(10);

        // a senha certa só devolve o desafio, sem cookie de sessão
        String desafio = corpo(login(admin.getEmail(), DadosTeste.SENHA).andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").doesNotExist())).get("desafio").asText();
        segundoFator(desafio, codigo(segredo, passo)).andExpect(status().isUnauthorized()); // o mesmo código não entra duas vezes
        segundoFator(desafio, codigo(segredo, passo + 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.segundoFatorAtivo").value(true));

        String recuperacao = codigos.get("codigos").get(0).asText();
        String desafio2 = corpo(login(admin.getEmail(), DadosTeste.SENHA)).get("desafio").asText();
        segundoFator(desafio2, recuperacao).andExpect(status().isOk());
        segundoFator(desafio2, recuperacao).andExpect(status().isUnauthorized()); // uso único
        assertThat(jdbc.queryForObject("SELECT count(*) FROM codigo_recuperacao WHERE usuario_id = ? AND usado_em IS NOT NULL",
                Long.class, admin.getId())).isOne();
    }

    @Test
    void escritorioQueExigeLevaQuemNaoAtivouParaAAtivacao() throws Exception {
        Usuario consultor = dados.usuario(escritorio, "cons-" + System.nanoTime() + "@test.com", Papel.CONSULTOR);
        jdbc.update("UPDATE escritorio SET exigir_segundo_fator = TRUE WHERE id = ?", escritorio.getId());

        RequestPostProcessor sessao = dados.sessao(consultor);
        mvc.perform(get("/api/clientes").with(sessao)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problemas/segundo-fator-obrigatorio"));
        mvc.perform(get("/api/usuarios/me").with(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.segundoFatorPendente").value(true));
        mvc.perform(post("/api/usuarios/me/segundo-fator/iniciar").with(sessao)).andExpect(status().isOk());
        mvc.perform(put("/api/escritorio/seguranca").with(sessao).contentType(APPLICATION_JSON)
                .content("{\"exigirSegundoFator\":false}")).andExpect(status().isForbidden());
    }

    @Test
    void sessoesPorDispositivoEncerrarUmaNaoDerrubaAsOutrasELogoutSoAAtual() throws Exception {
        Cookie celular = new Cookie(CookieSessao.NOME, dados.token(admin));
        Cookie notebook = new Cookie(CookieSessao.NOME, dados.token(admin));
        JsonNode lista = corpo(mvc.perform(get("/api/usuarios/me/sessoes").cookie(notebook)).andExpect(status().isOk()));
        assertThat(lista).hasSize(2);
        String idCelular = null;
        for (JsonNode s : lista) {
            if (!s.get("atual").asBoolean()) {
                idCelular = s.get("id").asText();
            }
        }
        mvc.perform(delete("/api/usuarios/me/sessoes/" + idCelular).cookie(notebook).with(DadosTeste.CSRF)).andExpect(status().isNoContent());
        mvc.perform(get("/api/usuarios/me").cookie(celular)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios/me").cookie(notebook)).andExpect(status().isOk());

        // sessão de outro usuário: 404
        Cookie outro = new Cookie(CookieSessao.NOME, dados.token(dados.usuario(escritorio, "o" + System.nanoTime() + "@t.com", Papel.CONSULTOR)));
        String idOutro = corpo(mvc.perform(get("/api/usuarios/me/sessoes").cookie(outro))).get(0).get("id").asText();
        mvc.perform(delete("/api/usuarios/me/sessoes/" + idOutro).cookie(notebook).with(DadosTeste.CSRF)).andExpect(status().isNotFound());

        Cookie tablet = new Cookie(CookieSessao.NOME, dados.token(admin));
        mvc.perform(post("/api/auth/logout").cookie(notebook).with(DadosTeste.CSRF)).andExpect(status().isNoContent());
        mvc.perform(get("/api/usuarios/me").cookie(notebook)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios/me").cookie(tablet)).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").with(DadosTeste.CSRF).contentType(APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions segundoFator(String desafio, String codigo) throws Exception {
        return mvc.perform(post("/api/auth/login/segundo-fator").with(DadosTeste.CSRF).contentType(APPLICATION_JSON)
                .content("{\"desafio\":\"" + desafio + "\",\"codigo\":\"" + codigo + "\"}"));
    }

    private JsonNode corpo(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private static String codigo(String segredo, long passo) {
        return com.fiscalhub.backend.security.TotpTest.codigo(segredo, passo);
    }
}
