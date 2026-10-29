package com.fiscalhub.backend.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.Debito;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ciclo de vida dos achados, isolamento, papéis e auditoria dos pontos de atenção. */
@TesteIntegracao
@Transactional
class PontosAtencaoTest {

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private RequestPostProcessor consultor;
    private Usuario usuario;
    private Cliente cliente;
    private Debito debito;

    @BeforeEach
    void setUp() {
        usuario = dados.usuario(dados.escritorio("Escritorio"), "c@test.com", Papel.CONSULTOR);
        consultor = dados.sessao(usuario);
        cliente = dados.cliente(usuario, "11222333000181");
        debito = dados.debito(dados.documento(cliente), "1000.00", LocalDate.of(2024, 2, 20));
    }

    private JsonNode abertos() throws Exception {
        return json.readTree(mvc.perform(get("/api/clientes/{c}/pontos-atencao?estado=NOVO", cliente.getId()).with(consultor))
                .andReturn().getResponse().getContentAsString());
    }

    private void reavaliar() throws Exception {
        mvc.perform(post("/api/clientes/{c}/pontos-atencao/reavaliar", cliente.getId()).with(consultor)).andExpect(status().isOk());
    }

    private long r07() throws Exception {
        for (JsonNode p : json.readTree(mvc.perform(get("/api/clientes/{c}/pontos-atencao", cliente.getId()).with(consultor))
                .andReturn().getResponse().getContentAsString())) {
            if ("R07".equals(p.get("regra").asText())) return p.get("id").asLong();
        }
        throw new AssertionError("sem R07");
    }

    private String estado(long id) {
        return jdbc.queryForObject("SELECT estado FROM ponto_atencao WHERE id = ?", String.class, id);
    }

    @Test
    void detectaSemDuplicarEResolveQuandoACondicaoSome() throws Exception {
        reavaliar();
        reavaliar();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ponto_atencao WHERE cliente_id = ? AND regra = 'R07'", Integer.class, cliente.getId())).isEqualTo(1);
        long id = r07();
        assertThat(abertos().findValuesAsText("regra")).contains("R07");
        mvc.perform(get("/api/clientes/{c}/pontos-atencao/{p}", cliente.getId(), id).with(consultor))
                .andExpect(jsonPath("$.evidencias[0].link").value("situacao-fiscal?debito=" + debito.getId()))
                .andExpect(jsonPath("$.fundamento").value(org.hamcrest.Matchers.containsString("Portaria Conjunta RFB/PGFN 1.751/2014")));

        jdbc.update("DELETE FROM debito WHERE id = ?", debito.getId());
        reavaliar();
        assertThat(estado(id)).isEqualTo("RESOLVIDO");
        assertThat(jdbc.queryForList("SELECT comentario FROM ponto_atencao_historico WHERE ponto_id = ? ORDER BY id", String.class, id))
                .containsExactly("detectado", "condição não ocorre mais");
    }

    @Test
    void descartadoNaoVoltaComAMesmaEvidenciaEVoltaSeElaMudar() throws Exception {
        reavaliar();
        long id = r07();
        mvc.perform(patch("/api/clientes/{c}/pontos-atencao/{p}/estado", cliente.getId(), id).with(consultor).contentType(APPLICATION_JSON)
                .content("{\"estado\":\"DESCARTADO\"}")).andExpect(status().isBadRequest()); // sem justificativa
        mvc.perform(patch("/api/clientes/{c}/pontos-atencao/{p}/estado", cliente.getId(), id).with(consultor).contentType(APPLICATION_JSON)
                .content("{\"estado\":\"DESCARTADO\",\"comentario\":\"débito já pago, aguardando baixa\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("DESCARTADO"))
                .andExpect(jsonPath("$.historico[1].comentario").value("débito já pago, aguardando baixa"));
        reavaliar();
        assertThat(estado(id)).isEqualTo("DESCARTADO");

        jdbc.update("UPDATE debito SET valor_principal = 1500.00 WHERE id = ?", debito.getId());
        reavaliar();
        assertThat(estado(id)).isEqualTo("NOVO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_evento WHERE acao = 'PONTO_ATENCAO_ESTADO'", Integer.class)).isPositive();
    }

    @Test
    void naoAvaliadasDizemOQueFaltaImportar() throws Exception {
        reavaliar();
        mvc.perform(get("/api/clientes/{c}/pontos-atencao/nao-avaliadas", cliente.getId()).with(consultor))
                .andExpect(jsonPath("$[?(@.codigo == 'R05')].falta").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("EFD ICMS/IPI"))))
                .andExpect(jsonPath("$[?(@.codigo == 'R11')].falta").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("ECF"))));
    }

    @Test
    void isolamentoEPapeis() throws Exception {
        reavaliar();
        long id = r07();
        RequestPostProcessor outro = dados.sessao(dados.usuario(dados.escritorio("Outro"), "o@test.com", Papel.ADMIN));
        mvc.perform(get("/api/clientes/{c}/pontos-atencao", cliente.getId()).with(outro)).andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes/{c}/pontos-atencao/{p}", cliente.getId(), id).with(outro)).andExpect(status().isNotFound());
        mvc.perform(post("/api/clientes/{c}/pontos-atencao/reavaliar", cliente.getId()).with(outro)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/clientes/{c}/pontos-atencao/{p}/estado", cliente.getId(), id).with(outro).contentType(APPLICATION_JSON)
                .content("{\"estado\":\"EM_ANALISE\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/escritorio/pontos-atencao/resumo").with(outro)).andExpect(jsonPath("$.clientes").isEmpty());

        RequestPostProcessor assistente = dados.sessao(dados.usuario(usuario.getEscritorio(), "a@test.com", Papel.ASSISTENTE));
        mvc.perform(get("/api/clientes/{c}/pontos-atencao", cliente.getId()).with(assistente)).andExpect(status().isOk());
        mvc.perform(patch("/api/clientes/{c}/pontos-atencao/{p}/estado", cliente.getId(), id).with(assistente).contentType(APPLICATION_JSON)
                .content("{\"estado\":\"EM_ANALISE\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/escritorio/pontos-atencao/regras/R05").with(consultor).contentType(APPLICATION_JSON)
                .content("{\"parametros\":{\"mesesConsecutivos\":4}}")).andExpect(status().isForbidden()); // só ADMIN

        mvc.perform(get("/api/escritorio/pontos-atencao/resumo").with(consultor))
                .andExpect(jsonPath("$.clientes[0].clienteId").value(cliente.getId().intValue()))
                .andExpect(jsonPath("$.clientes[0].criticos").value(1));
    }

    @Test
    void parametrosDoEscritorioSobrepoemOPadraoESaoValidados() throws Exception {
        RequestPostProcessor admin = dados.sessao(dados.usuario(usuario.getEscritorio(), "adm@test.com", Papel.ADMIN));
        mvc.perform(put("/api/escritorio/pontos-atencao/regras/R05").with(admin).contentType(APPLICATION_JSON)
                .content("{\"parametros\":{\"mesesConsecutivos\":4},\"severidade\":\"ALTA\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parametros.mesesConsecutivos").value(4))
                .andExpect(jsonPath("$.parametrosPadrao.mesesConsecutivos").value(3)).andExpect(jsonPath("$.severidade").value("ALTA"));
        mvc.perform(put("/api/escritorio/pontos-atencao/regras/R05").with(admin).contentType(APPLICATION_JSON)
                .content("{\"parametros\":{\"novo\":1}}")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_evento WHERE acao = 'PARAMETROS_PONTOS_ALTERAR'", Integer.class)).isPositive();
    }
}
