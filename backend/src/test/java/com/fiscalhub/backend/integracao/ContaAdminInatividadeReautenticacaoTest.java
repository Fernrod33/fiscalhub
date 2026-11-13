package com.fiscalhub.backend.integracao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.Escritorio;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.security.CookieSessao;
import com.fiscalhub.backend.security.ExigeReautenticacao;
import com.fiscalhub.backend.security.TotpTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prompt 12b: 2º fator obrigatório para ADMIN, expiração por inatividade e reautenticação nas ações destrutivas. */
@TesteIntegracao
class ContaAdminInatividadeReautenticacaoTest {

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired com.fiscalhub.backend.repository.UsuarioRepository usuarios;

    private Escritorio escritorio;
    private Usuario admin;
    private Usuario consultor;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        escritorio = dados.escritorio("Conta " + System.nanoTime());
        admin = dados.usuario(escritorio, "adm-" + System.nanoTime() + "@test.com", Papel.ADMIN);
        consultor = dados.usuario(escritorio, "con-" + System.nanoTime() + "@test.com", Papel.CONSULTOR);
        cliente = dados.cliente(admin, "11222333000181");
    }

    // ---- 2º fator obrigatório para ADMIN

    @Test
    void adminSemTotpSoVeOProprioCadastroEAtivacaoAteAtivar() throws Exception {
        var sessao = dados.sessao(dados.semSegundoFator(admin));
        mvc.perform(get("/api/clientes").with(sessao)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problemas/segundo-fator-obrigatorio"));
        mvc.perform(get("/api/usuarios").with(sessao)).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios/me").with(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.segundoFatorPendente").value(true));
        // a ativação continua liberada
        var ativacao = json.readTree(mvc.perform(post("/api/usuarios/me/segundo-fator/iniciar").with(sessao))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String codigo = TotpTest.codigo(ativacao.get("segredo").asText(), Instant.now().getEpochSecond() / 30);
        mvc.perform(post("/api/usuarios/me/segundo-fator/ativar").with(sessao).contentType(APPLICATION_JSON)
                .content("{\"codigo\":\"" + codigo + "\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/clientes").with(sessao)).andExpect(status().isOk());
    }

    @Test
    void adminNaoDesativaOProprioSegundoFator() throws Exception {
        mvc.perform(post("/api/usuarios/me/segundo-fator/desativar").with(dados.sessao(admin)).contentType(APPLICATION_JSON)
                .content("{\"senha\":\"" + DadosTeste.SENHA + "\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void promoverAAdminExigeAAtivacaoNoProximoAcesso() throws Exception {
        mvc.perform(patch("/api/usuarios/{id}/papel", consultor.getId()).with(dados.sessaoReautenticada(admin))
                .contentType(APPLICATION_JSON).content("{\"papel\":\"ADMIN\"}")).andExpect(status().isOk());
        // a troca de papel derruba as sessões; na nova, o novo ADMIN só ativa o 2º fator
        var sessao = dados.sessao(usuarios.findById(consultor.getId()).orElseThrow());
        mvc.perform(get("/api/clientes").with(sessao)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problemas/segundo-fator-obrigatorio"));
    }

    // ---- inatividade

    @Test
    void sessaoParadaHaMaisDe30MinutosExpiraEAAtivaSegue() throws Exception {
        String parada = dados.token(consultor);
        jdbc.update("UPDATE sessao SET ultimo_uso = now() - interval '31 minutes' WHERE usuario_id = ?", consultor.getId());
        String ativa = dados.token(consultor);
        jdbc.update("UPDATE sessao SET ultimo_uso = now() - interval '29 minutes' WHERE usuario_id = ? AND ultimo_uso > now() - interval '1 minute'",
                consultor.getId());
        mvc.perform(get("/api/usuarios/me").cookie(new Cookie(CookieSessao.NOME, parada))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios/me").cookie(new Cookie(CookieSessao.NOME, ativa))).andExpect(status().isOk());
        mvc.perform(get("/api/usuarios/me/sessao").cookie(new Cookie(CookieSessao.NOME, ativa)))
                .andExpect(jsonPath("$.inatividadeSegundos").value(1800)).andExpect(jsonPath("$.reautenticacaoSegundos").value(300));
    }

    // ---- reautenticação

    @Test
    void asQuatroAcoesDestrutivasExigemReautenticacaoRecente() throws Exception {
        var sessao = dados.sessao(admin);
        for (MockHttpServletRequestBuilder acao : List.of(
                post("/api/clientes/{id}/exclusao", cliente.getId()).contentType(APPLICATION_JSON).content("{\"confirmacao\":\"11222333000181\"}"),
                get("/api/clientes/{id}/exportacao", cliente.getId()),
                patch("/api/usuarios/{id}/desativar", consultor.getId()),
                delete("/api/usuarios/me"),
                patch("/api/usuarios/{id}/papel", consultor.getId()).contentType(APPLICATION_JSON).content("{\"papel\":\"ASSISTENTE\"}"))) {
            mvc.perform(acao.with(sessao)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value("/problemas/reautenticacao-necessaria"));
        }
    }

    @Test
    void reautenticacaoConfereSenhaECodigoEValeSoNaMesmaSessao() throws Exception {
        String jwt = dados.token(admin);
        var sessao = comCookie(jwt);
        // ADMIN tem o 2º fator: só a senha não basta; senha errada também não (400, a sessão continua)
        mvc.perform(post("/api/usuarios/me/reautenticacao").with(sessao).contentType(APPLICATION_JSON)
                .content("{\"senha\":\"" + DadosTeste.SENHA + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/usuarios/me/reautenticacao").with(sessao).contentType(APPLICATION_JSON)
                .content("{\"senha\":\"errada-errada\",\"codigo\":\"" + codigoAgora() + "\"}")).andExpect(status().isBadRequest());
        String token = json.readTree(mvc.perform(post("/api/usuarios/me/reautenticacao").with(sessao).contentType(APPLICATION_JSON)
                        .content("{\"senha\":\"" + DadosTeste.SENHA + "\",\"codigo\":\"" + codigoAgora() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.validadeSegundos").value(300))
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        mvc.perform(patch("/api/usuarios/{id}/papel", consultor.getId()).with(sessao).header(ExigeReautenticacao.CABECALHO, token)
                .contentType(APPLICATION_JSON).content("{\"papel\":\"ASSISTENTE\"}")).andExpect(status().isOk());
        // o mesmo token noutra sessão do mesmo usuário não vale
        mvc.perform(patch("/api/usuarios/{id}/papel", consultor.getId()).with(comCookie(dados.token(admin)))
                .header(ExigeReautenticacao.CABECALHO, token).contentType(APPLICATION_JSON).content("{\"papel\":\"CONSULTOR\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.type").value("/problemas/reautenticacao-necessaria"));
        mvc.perform(get("/api/usuarios/me").with(sessao)).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM auditoria_evento WHERE acao = 'REAUTENTICAR' AND usuario_id = ?", Long.class, admin.getId())).isEqualTo(3);
    }

    private static String codigoAgora() {
        return TotpTest.codigo(DadosTeste.SEGREDO_TOTP, Instant.now().getEpochSecond() / 30);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor comCookie(String jwt) {
        Cookie c = new Cookie(CookieSessao.NOME, jwt);
        return req -> {
            req.setCookies(c, new Cookie("XSRF-TOKEN", "csrf-de-teste"));
            req.addHeader("X-XSRF-TOKEN", "csrf-de-teste");
            return req;
        };
    }
}
