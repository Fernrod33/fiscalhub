package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Cliente;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.fiscalhub.backend.entity.Debito;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.SimulacaoParcelamento;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.repository.SimulacaoParcelamentoRepository;
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
    @Autowired SimulacaoParcelamentoRepository simulacaoRepository;

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
    void salvaSimulacaoConsolidandoDoisDebitos() throws Exception {
        dados.selic(YearMonth.of(2024, 1), YearMonth.of(2024, 12), "1.00");

        String resposta = mvc.perform(post("/api/simulacoes/calcular/salvar").with(token)
                        .contentType(APPLICATION_JSON).content(simulacao()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valorOriginal").value(1000.00))
                .andExpect(jsonPath("$.dataVencimentoOriginal").value("2024-02-20"))
                .andExpect(jsonPath("$.clienteId").value(cliente.getId().intValue()))
                .andExpect(jsonPath("$.debitoIds.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        Long id = ((Number) JsonPath.read(resposta, "$.simulacaoId")).longValue();
        SimulacaoParcelamento salva = simulacaoRepository.findById(id).orElseThrow();
        assertThat(salva.getCliente().getId()).isEqualTo(cliente.getId());
        assertThat(salva.getDebitos()).extracting(Debito::getId).containsExactlyInAnyOrder(debito1.getId(), debito2.getId());
    }

    @Test
    void semSelicCadastradaRespondeErroClaro() throws Exception {
        mvc.perform(post("/api/simulacoes/calcular").with(token)
                        .contentType(APPLICATION_JSON).content(simulacao()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Índice SELIC ausente para 2024-03."));
    }

    private String simulacao() {
        return """
                {"clienteId":%d,"debitoIds":[%d,%d],"orgao":"RECEITA_FEDERAL","modalidade":"ORDINARIO_RFB",
                 "numeroParcelas":2,"dataCalculo":"2024-04-05"}""".formatted(cliente.getId(), debito1.getId(), debito2.getId());
    }
}
