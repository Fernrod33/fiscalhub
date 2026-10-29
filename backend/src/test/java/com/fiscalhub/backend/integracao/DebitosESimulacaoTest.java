package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Cliente;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.fiscalhub.backend.entity.Debito;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import org.springframework.jdbc.core.JdbcTemplate;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Débito só por importação (com documento de origem) e simulação consolidando vários débitos. */
@TesteIntegracao
@Transactional
class DebitosESimulacaoTest {

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired JdbcTemplate jdbc;

    private RequestPostProcessor token;
    private Cliente cliente;
    private Debito debito1;
    private Debito debito2;

    @BeforeEach
    void setUp() {
        Usuario consultor = dados.usuario(dados.escritorio("Escritorio"), "c@test.com", Papel.CONSULTOR);
        token = dados.sessao(consultor);
        cliente = dados.cliente(consultor, "11222333000181");
        DocumentoFiscal origem = dados.documento(cliente);
        debito1 = dados.debito(origem, "600.00", LocalDate.of(2024, 2, 20));
        debito2 = dados.debito(origem, "400.00", LocalDate.of(2024, 3, 20));
    }

    @Test
    void debitosDoClienteTrazemODocumentoDeOrigem() throws Exception {
        mvc.perform(get("/api/clientes/{id}/debitos", cliente.getId()).with(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].documentoOrigemId").value(debito1.getDocumentoOrigem().getId().intValue()))
                .andExpect(jsonPath("$[0].referenciaOrigem").value("registro 1"));
    }

    @Test
    void naoHaCriacaoManualDeDebito() throws Exception {
        mvc.perform(post("/api/clientes/{id}/debitos", cliente.getId()).with(token)
                        .contentType(APPLICATION_JSON).content("{\"valorPrincipal\":1}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void salvaSimulacaoComCadaDebitoEOsVersionamentosERecalculaComoNovaVersao() throws Exception {
        dados.selic(YearMonth.of(2024, 1), YearMonth.of(2024, 12), "1.00");

        String resposta = mvc.perform(post("/api/clientes/{c}/simulacoes", cliente.getId()).with(token)
                        .contentType(APPLICATION_JSON).content(simulacao()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(1))
                .andExpect(jsonPath("$.dataBase").value("2024-04-05"))
                .andExpect(jsonPath("$.versaoRegras").value("2026-10-29"))
                .andExpect(jsonPath("$.versaoIndices").value("SELIC até 2024-12; UPF/PR até 2025-04"))
                .andExpect(jsonPath("$.modalidades[?(@.chave == 'RFB_ORDINARIO')].debitos[*].vencimento")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("2024-02-20", "2024-03-20")))
                .andExpect(jsonPath("$.recomendacao[0].orgao").value("RFB"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(resposta, "$.id")).longValue();

        mvc.perform(post("/api/clientes/{c}/simulacoes/{id}/recalcular", cliente.getId(), id).with(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(2))
                .andExpect(jsonPath("$.anteriorId").value(id));
        mvc.perform(get("/api/clientes/{c}/simulacoes", cliente.getId()).with(token))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versao").value(2));
        assertThat(jdbc.queryForList("SELECT acao FROM auditoria_evento WHERE recurso_tipo = 'SIMULACAO'", String.class))
                .contains("SIMULACAO_CRIAR", "SIMULACAO_RECALCULAR");
    }

    @Test
    void simulacaoDoMotorAntigoApareceRotuladaEERecalculavel() throws Exception {
        dados.selic(YearMonth.of(2024, 1), YearMonth.of(2024, 12), "1.00");
        Long antiga = dados.simulacao(debito1);

        mvc.perform(get("/api/clientes/{c}/simulacoes/{id}", cliente.getId(), antiga).with(token))
                .andExpect(jsonPath("$.motorAntigo").value(org.hamcrest.Matchers.startsWith("Calculada pelo motor antigo")));
        mvc.perform(post("/api/clientes/{c}/simulacoes/{id}/recalcular", cliente.getId(), antiga).with(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(2))
                .andExpect(jsonPath("$.dataBase").value("2024-06-01"))
                .andExpect(jsonPath("$.modalidades[?(@.chave == 'RFB_ORDINARIO')].debitos.length()").value(1));
    }

    @Test
    void semSelicCadastradaRespondeErroClaro() throws Exception {
        mvc.perform(post("/api/clientes/{c}/simulacoes/calcular", cliente.getId()).with(token)
                        .contentType(APPLICATION_JSON).content(simulacao()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Índice SELIC ausente para 2024-03."));
    }

    private String simulacao() {
        return """
                {"debitoIds":[%d,%d],"parcelas":2,"dataBase":"2024-04-05"}""".formatted(debito1.getId(), debito2.getId());
    }
}
