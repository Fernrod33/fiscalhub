package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.fiscalhub.backend.auditoria.AuditoriaEvento;
import com.fiscalhub.backend.auditoria.AuditoriaEventoRepository;
import com.fiscalhub.backend.auditoria.RetencaoAuditoria;
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

/** Ações auditadas geram evento pelo aspecto; cada ADMIN só vê o próprio escritório; a tabela é só inserção, salvo o expurgo da retenção. */
@TesteIntegracao
@Transactional
class AuditoriaTest {

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired AuditoriaEventoRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired RetencaoAuditoria retencao;

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

    @Test
    void expurgoRemoveSoOsForaDoPrazoERegistraOExpurgoEODeleteComumContinuaBloqueado() {
        long antigo = eventoHa("6 years");
        long recente = eventoHa("4 years");

        int removidos = retencao.expurgar();

        assertThat(removidos).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForList("SELECT id FROM auditoria_evento WHERE id IN (?, ?)", Long.class, antigo, recente))
                .containsExactly(recente);
        var registro = jdbc.queryForMap("""
                SELECT detalhes->>'registros' AS registros, detalhes->>'anterioresA' AS ate, detalhes->>'retencaoAnos' AS anos
                FROM auditoria_evento WHERE acao = 'AUDITORIA_EXPURGAR' ORDER BY id DESC LIMIT 1""");
        assertThat(registro).containsEntry("registros", String.valueOf(removidos)).containsEntry("anos", "5");
        assertThat((String) registro.get("ate")).startsWith(java.time.LocalDate.now().minusYears(5).toString());

        // depois do expurgo, na mesma transação, o DELETE comum de um evento antigo continua barrado
        long outroAntigo = eventoHa("7 years");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM auditoria_evento WHERE id = ?", outroAntigo))
                .hasMessageContaining("somente insercao");
    }

    @Test
    void deleteComumContinuaBloqueado() {
        long antigo = eventoHa("10 years");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM auditoria_evento WHERE id = ?", antigo))
                .hasMessageContaining("somente insercao");
    }

    private long eventoHa(String intervalo) {
        return jdbc.queryForObject("INSERT INTO auditoria_evento (escritorio_id, acao, data_hora) VALUES (?, 'LOGIN', now() - ?::interval) RETURNING id",
                Long.class, escritorio.getId(), intervalo);
    }

    private List<AuditoriaEvento> eventos() {
        return repository.findByEscritorioIdOrderByDataHoraDescIdDesc(escritorio.getId(), Pageable.unpaged()).getContent();
    }
}
