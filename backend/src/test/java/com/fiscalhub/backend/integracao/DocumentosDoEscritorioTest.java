package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.Papel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/documentos e /resumo: só o escritório do usuário, com filtros e paginação. */
@TesteIntegracao
@Transactional
class DocumentosDoEscritorioTest {

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;

    private RequestPostProcessor sessaoA;
    private Cliente alfa;
    private Cliente beta;
    private Cliente deOutroEscritorio;

    @BeforeEach
    void setUp() {
        Usuario a = dados.usuario(dados.escritorio("Agregado A"), "agregado-a@test.com", Papel.ASSISTENTE);
        Usuario b = dados.usuario(dados.escritorio("Agregado B"), "agregado-b@test.com", Papel.ADMIN);
        sessaoA = dados.sessao(a);
        alfa = dados.cliente(a, "11222333000181");
        beta = dados.cliente(a, "11444777000161");
        deOutroEscritorio = dados.cliente(b, "11222333000181");

        dados.documento(alfa);
        comStatus(dados.documento(alfa), DocumentoStatus.ERRO);
        comStatus(dados.documento(beta), DocumentoStatus.PROCESSADO);
        dados.documento(deOutroEscritorio);
    }

    @Test
    void listaSoOsDocumentosDoEscritorioComNomeDoCliente() throws Exception {
        mvc.perform(get("/api/documentos").with(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].clienteRazaoSocial", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.oneOf(alfa.getRazaoSocial(), beta.getRazaoSocial()))));
    }

    @Test
    void filtraPorClienteStatusETipoEPagina() throws Exception {
        mvc.perform(get("/api/documentos").param("clienteId", beta.getId().toString()).with(sessaoA))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get("/api/documentos").param("status", "ERRO").with(sessaoA))
                .andExpect(jsonPath("$.content[*].clienteId", contains(alfa.getId().intValue())));
        mvc.perform(get("/api/documentos").param("tipo", "DCTF").param("pagina", "1").param("tamanho", "2").with(sessaoA))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void clienteDeOutroEscritorioNoFiltroNaoTrazNada() throws Exception {
        mvc.perform(get("/api/documentos").param("clienteId", deOutroEscritorio.getId().toString()).with(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void resumoContaPorStatusSoNoEscritorio() throws Exception {
        mvc.perform(get("/api/documentos/resumo").with(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RECEBIDO").value(1))
                .andExpect(jsonPath("$.ERRO").value(1))
                .andExpect(jsonPath("$.PROCESSADO").value(1));
    }

    /** Entidade gerenciada: a mudança vai ao banco no flush que antecede a consulta. */
    private static void comStatus(DocumentoFiscal documento, DocumentoStatus status) {
        documento.setStatus(status);
    }
}
