package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.security.CookieSessao;
import jakarta.servlet.http.Cookie;

import com.fiscalhub.backend.entity.Cliente;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.fiscalhub.backend.entity.Debito;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.SimulacaoParcelamento;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.repository.ClienteRepository;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.fiscalhub.backend.repository.SimulacaoParcelamentoRepository;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolamento entre escritórios: o escritório A não lê, edita nem exclui nada do B (sempre 404);
 * membros do mesmo escritório se enxergam; papéis respeitados; /api/usuarios nunca devolve a senha; sem token = 401.
 */
@TesteIntegracao
@Transactional
class AcessoEntreEscritoriosTest {

    private static final String CLIENTE_JSON = """
            {"razaoSocial":"Invasora Ltda","cnpj":"12345678000195"}""";

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired ClienteRepository clienteRepository;
    @Autowired SimulacaoParcelamentoRepository simulacaoRepository;
    @Autowired DocumentoFiscalRepository documentoRepository;

    private RequestPostProcessor tokenA;
    private RequestPostProcessor tokenB;
    private Usuario usuarioA;
    private Usuario usuarioB;
    private Cliente clienteB;
    private DocumentoFiscal documentoB;
    private Debito debitoB;
    private SimulacaoParcelamento simulacaoB;

    @BeforeEach
    void setUp() {
        usuarioA = dados.usuario(dados.escritorio("Escritorio A"), "a@test.com", Papel.ADMIN);
        usuarioB = dados.usuario(dados.escritorio("Escritorio B"), "b@test.com", Papel.ADMIN);
        clienteB = dados.cliente(usuarioB, "11222333000181");
        documentoB = dados.documento(clienteB);
        debitoB = dados.debito(documentoB, "1000.00", LocalDate.of(2024, 2, 20));
        simulacaoB = dados.simulacao(debitoB);
        tokenA = dados.sessao(usuarioA);
        tokenB = dados.sessao(usuarioB);
    }

    @Test
    void escritorioANaoLeEditaNemExcluiNadaDoB() throws Exception {
        SoftAssertions softly = new SoftAssertions();
        for (Map.Entry<String, MockHttpServletRequestBuilder> chamada : chamadasSobreRecursosDeB().entrySet()) {
            int status = mvc.perform(chamada.getValue().with(tokenA)).andReturn().getResponse().getStatus();
            softly.assertThat(status).as(chamada.getKey()).isEqualTo(404);
        }
        softly.assertAll();

        assertThat(clienteRepository.findById(clienteB.getId())).isPresent();
        assertThat(simulacaoRepository.findById(simulacaoB.getId())).get()
                .extracting(SimulacaoParcelamento::getAprovada).isEqualTo(false);
        assertThat(documentoRepository.findById(documentoB.getId())).isPresent();
    }

    /** Débito de outro cliente (mesmo escritório) não entra na simulação deste cliente. */
    @Test
    void simulacaoNaoMisturaDebitoDeOutroCliente() throws Exception {
        Cliente outroClienteB = dados.cliente(usuarioB, "12345678000195");
        String corpo = simulacaoJson(outroClienteB.getId(), debitoB.getId());

        mvc.perform(post("/api/simulacoes/calcular").with(tokenB).contentType(APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
    }

    /** Controle positivo: qualquer membro do escritório B (não só quem cadastrou) enxerga os recursos dele. */
    @Test
    void membrosDoMesmoEscritorioSeEnxergam() throws Exception {
        RequestPostProcessor colega = dados.sessao(dados.usuario(usuarioB.getEscritorio(), "colega-b@test.com", Papel.ASSISTENTE));
        for (String url : List.of(
                "/api/clientes",
                "/api/clientes/" + clienteB.getId(),
                "/api/clientes/" + clienteB.getId() + "/debitos",
                "/api/simulacoes/" + simulacaoB.getId(),
                "/api/documentos/" + documentoB.getId())) {
            mvc.perform(get(url).with(tokenB)).andExpect(status().isOk());
            mvc.perform(get(url).with(colega)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/clientes").with(colega))
                .andExpect(jsonPath("$[0].id").value(clienteB.getId().intValue()));
        mvc.perform(get("/api/clientes").with(tokenA))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void assistenteLeEImportaMasNaoAlteraNemExclui() throws Exception {
        RequestPostProcessor assistente = dados.sessao(dados.usuario(usuarioB.getEscritorio(), "assist-b@test.com", Papel.ASSISTENTE));
        Long c = clienteB.getId();

        mvc.perform(delete("/api/clientes/{id}", c).with(assistente)).andExpect(status().isForbidden());
        mvc.perform(put("/api/clientes/{id}", c).with(assistente)
                .contentType(APPLICATION_JSON).content(CLIENTE_JSON)).andExpect(status().isForbidden());
        mvc.perform(post("/api/clientes").with(assistente)
                .contentType(APPLICATION_JSON).content(CLIENTE_JSON)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/simulacoes/{id}", simulacaoB.getId()).with(assistente))
                .andExpect(status().isForbidden());

        mvc.perform(importacao(c).with(assistente)).andExpect(status().isCreated());
        assertThat(clienteRepository.findById(c)).isPresent();
    }

    @Test
    void consultorAlteraClienteMasNaoGerenciaUsuarios() throws Exception {
        RequestPostProcessor consultor = dados.sessao(dados.usuario(usuarioB.getEscritorio(), "cons-b@test.com", Papel.CONSULTOR));

        mvc.perform(put("/api/clientes/{id}", clienteB.getId()).with(consultor)
                .contentType(APPLICATION_JSON).content(CLIENTE_JSON)).andExpect(status().isOk());
        mvc.perform(get("/api/usuarios").with(consultor)).andExpect(status().isForbidden());
    }

    @Test
    void doisEscritoriosCadastramOMesmoCnpjMasNaoDuasVezesNoMesmo() throws Exception {
        String mesmoCnpj = "{\"razaoSocial\":\"Mesmo Cliente\",\"cnpj\":\"" + clienteB.getCnpj() + "\"}";

        mvc.perform(post("/api/clientes").with(tokenA).contentType(APPLICATION_JSON).content(mesmoCnpj))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/clientes").with(tokenB).contentType(APPLICATION_JSON).content(mesmoCnpj))
                .andExpect(status().isConflict());
    }

    @Test
    void respostasDeUsuarioNuncaTrazemSenha() throws Exception {
        mvc.perform(get("/api/usuarios/me").with(tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("a@test.com"))
                .andExpect(jsonPath("$.senha").doesNotExist());

        String corpo = mvc.perform(put("/api/usuarios/me").with(tokenA)
                        .contentType(APPLICATION_JSON).content("{\"nome\":\"Novo Nome\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Novo Nome"))
                .andReturn().getResponse().getContentAsString();
        assertThat(corpo).doesNotContain("senha").doesNotContain(usuarioA.getSenha());
    }

    @Test
    void semTokenOuComTokenInvalidoResponde401() throws Exception {
        for (String url : List.of("/api/usuarios/me", "/api/clientes",
                "/api/clientes/" + clienteB.getId() + "/debitos", "/api/simulacoes/" + simulacaoB.getId(),
                "/api/clientes/" + clienteB.getId() + "/documentos")) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(get(url).cookie(new Cookie(CookieSessao.NOME, "token-invalido"))).andExpect(status().isUnauthorized());
        }
    }

    private Map<String, MockHttpServletRequestBuilder> chamadasSobreRecursosDeB() {
        Long c = clienteB.getId(), s = simulacaoB.getId(), doc = documentoB.getId();
        String simulacaoJson = simulacaoJson(c, debitoB.getId());
        return Map.ofEntries(
                Map.entry("GET cliente", get("/api/clientes/{id}", c)),
                Map.entry("PUT cliente", put("/api/clientes/{id}", c).contentType(APPLICATION_JSON).content(CLIENTE_JSON)),
                Map.entry("DELETE cliente", delete("/api/clientes/{id}", c)),
                Map.entry("GET debitos do cliente", get("/api/clientes/{id}/debitos", c)),
                Map.entry("GET simulacao", get("/api/simulacoes/{id}", s)),
                Map.entry("GET simulacoes do cliente", get("/api/simulacoes/cliente/{id}", c)),
                Map.entry("POST calcular", post("/api/simulacoes/calcular").contentType(APPLICATION_JSON).content(simulacaoJson)),
                Map.entry("POST calcular/salvar", post("/api/simulacoes/calcular/salvar").contentType(APPLICATION_JSON).content(simulacaoJson)),
                Map.entry("PATCH aprovar simulacao", patch("/api/simulacoes/{id}/aprovar", s)),
                Map.entry("DELETE simulacao", delete("/api/simulacoes/{id}", s)),
                Map.entry("GET documentos do cliente", get("/api/clientes/{c}/documentos", c)),
                Map.entry("GET documento", get("/api/documentos/{d}", doc)),
                Map.entry("GET status do documento", get("/api/documentos/{d}/status", doc)),
                Map.entry("GET inconsistencias do documento", get("/api/documentos/{d}/inconsistencias", doc)),
                Map.entry("GET arquivo do documento", get("/api/documentos/{d}/arquivo", doc)),
                Map.entry("POST reprocessar documento", post("/api/documentos/{d}/reprocessar", doc)),
                Map.entry("POST confirmar cliente do documento", post("/api/documentos/{d}/confirmar-cliente", doc)),
                Map.entry("DELETE documento", delete("/api/documentos/{d}", doc)),
                Map.entry("POST importar documento", importacao(c))
        );
    }

    private static String simulacaoJson(Long clienteId, Long debitoId) {
        return """
                {"clienteId":%d,"debitoIds":[%d],"orgao":"RECEITA_FEDERAL","modalidade":"ORDINARIO_RFB",
                 "numeroParcelas":10,"dataCalculo":"2024-04-05"}""".formatted(clienteId, debitoId);
    }

    static MockHttpServletRequestBuilder importacao(Long clienteId) {
        return multipart("/api/clientes/{c}/documentos", clienteId)
                .file(new MockMultipartFile("arquivo", "x.csv", "text/csv", ("a;b;" + System.nanoTime()).getBytes()))
                .file(new MockMultipartFile("documento", "", "application/json",
                        "{\"tipo\":\"OUTRO\"}".getBytes()));
    }
}
