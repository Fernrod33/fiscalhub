package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.fiscalhub.backend.auditoria.AuditoriaEvento;
import com.fiscalhub.backend.auditoria.AuditoriaEventoRepository;
import com.fiscalhub.backend.entity.Escritorio;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ações auditadas geram evento pelo aspecto; cada ADMIN só vê o próprio escritório; a tabela é só inserção. */
@TesteIntegracao
@Transactional
class AuditoriaTest {

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired AuditoriaEventoRepository repository;
    @Autowired JdbcTemplate jdbc;

    private Escritorio escritorio;
    private Usuario admin;
    private RequestPostProcessor tokenAdmin;

    @BeforeEach
    void setUp() {
        escritorio = dados.escritorio("Auditado");
        admin = dados.usuario(escritorio, "admin-aud@test.com", Papel.ADMIN);
        tokenAdmin = dados.sessao(admin);
    }

    @Test
    void loginComSucessoEComFalhaGeramEvento() throws Exception {
        mvc.perform(post("/api/auth/login").with(DadosTeste.CSRF).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"admin-aud@test.com\",\"senha\":\"" + DadosTeste.SENHA + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").with(DadosTeste.CSRF).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"admin-aud@test.com\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized());

        List<AuditoriaEvento> eventos = eventos();
        assertThat(eventos).extracting(AuditoriaEvento::getAcao).containsOnly(AcaoAuditoria.LOGIN);
        assertThat(eventos).extracting(e -> e.getDetalhes().get("resultado")).containsExactlyInAnyOrder("SUCESSO", "FALHA");
        assertThat(eventos).allSatisfy(e -> {
            assertThat(e.getUsuarioId()).isEqualTo(admin.getId());
            assertThat(e.getIp()).isNotBlank();
            assertThat(e.getDetalhes()).doesNotContainKey("senha");
        });
    }

    @Test
    void criarClienteGeraEventoComORecurso() throws Exception {
        String resposta = mvc.perform(post("/api/clientes").with(tokenAdmin).contentType(APPLICATION_JSON)
                        .content("{\"razaoSocial\":\"Auditada Ltda\",\"cnpj\":\"11222333000181\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long clienteId = ((Number) com.jayway.jsonpath.JsonPath.read(resposta, "$.id")).longValue();

        mvc.perform(AcessoEntreEscritoriosTest.importacao(clienteId).with(tokenAdmin))
                .andExpect(status().isCreated());

        assertThat(eventos()).extracting(AuditoriaEvento::getAcao, AuditoriaEvento::getRecursoTipo)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(AcaoAuditoria.CLIENTE_CRIAR, "CLIENTE"),
                        org.assertj.core.groups.Tuple.tuple(AcaoAuditoria.DOCUMENTO_IMPORTAR, "DOCUMENTO"));
        assertThat(eventos()).filteredOn(e -> e.getAcao() == AcaoAuditoria.CLIENTE_CRIAR)
                .singleElement().extracting(AuditoriaEvento::getRecursoId).isEqualTo(clienteId);
    }

    @Test
    void adminSoVeAAuditoriaDoProprioEscritorioEOutrosPapeisNaoVeem() throws Exception {
        Usuario adminOutro = dados.usuario(dados.escritorio("Outro"), "admin-outro@test.com", Papel.ADMIN);
        mvc.perform(post("/api/clientes").with(dados.sessao(adminOutro)).contentType(APPLICATION_JSON)
                        .content("{\"razaoSocial\":\"De Fora\",\"cnpj\":\"11222333000181\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/auditoria").with(tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/auditoria").with(dados.sessao(adminOutro)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].acao").value("CLIENTE_CRIAR"))
                .andExpect(jsonPath("$.content[0].usuarioNome").value("admin-outro@test.com"))
                .andExpect(jsonPath("$.content[0].resultado").value("SUCESSO"))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        RequestPostProcessor consultor = dados.sessao(dados.usuario(escritorio, "cons-aud@test.com", Papel.CONSULTOR));
        mvc.perform(get("/api/auditoria").with(consultor)).andExpect(status().isForbidden());
    }

    @Test
    void tabelaAceitaSoInsercao() throws Exception {
        mvc.perform(post("/api/clientes").with(tokenAdmin).contentType(APPLICATION_JSON)
                .content("{\"razaoSocial\":\"X\",\"cnpj\":\"11222333000181\"}")).andExpect(status().isCreated());
        repository.flush();

        assertThatThrownBy(() -> jdbc.update("UPDATE auditoria_evento SET acao = 'LOGIN' WHERE escritorio_id = ?", escritorio.getId()))
                .hasMessageContaining("somente insercao");
    }

    private List<AuditoriaEvento> eventos() {
        return repository.findByEscritorioIdOrderByDataHoraDescIdDesc(escritorio.getId(), Pageable.unpaged()).getContent();
    }
}
