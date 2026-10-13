package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Declaracao;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.SimulacaoParcelamento;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.DocumentoEsfera;
import com.fiscalhub.backend.entity.enums.DocumentoJuridicao;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.entity.enums.ModalidadeParcelamento;
import com.fiscalhub.backend.entity.enums.OrgaoReceita;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.repository.DeclaracaoRepository;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.fiscalhub.backend.repository.ClienteRepository;
import com.fiscalhub.backend.repository.SimulacaoParcelamentoRepository;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
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

    private static final String DECLARACAO_JSON = """
            {"tipo":"DCTF","periodoInicio":"2024-01-01","periodoFim":"2024-01-31",
             "valorTotal":100.00,"status":"EM_ABERTO"}""";
    private static final String CLIENTE_JSON = """
            {"razaoSocial":"Invasora Ltda","cnpj":"12345678000195"}""";

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired ClienteRepository clienteRepository;
    @Autowired DeclaracaoRepository declaracaoRepository;
    @Autowired SimulacaoParcelamentoRepository simulacaoRepository;
    @Autowired DocumentoFiscalRepository documentoRepository;

    private String tokenA;
    private String tokenB;
    private Usuario usuarioA;
    private Usuario usuarioB;
    private Cliente clienteB;
    private Declaracao declaracaoB;
    private SimulacaoParcelamento simulacaoB;
    private DocumentoFiscal documentoB;

    @BeforeEach
    void setUp() {
        usuarioA = dados.usuario(dados.escritorio("Escritorio A"), "a@test.com", Papel.ADMIN);
        usuarioB = dados.usuario(dados.escritorio("Escritorio B"), "b@test.com", Papel.ADMIN);
        clienteB = cliente(usuarioB, "11222333000181");
        declaracaoB = declaracao(clienteB);
        simulacaoB = simulacao(declaracaoB);
        documentoB = documento(clienteB);
        tokenA = dados.token(usuarioA);
        tokenB = dados.token(usuarioB);
    }

    @Test
    void escritorioANaoLeEditaNemExcluiNadaDoB() throws Exception {
        SoftAssertions softly = new SoftAssertions();
        for (Map.Entry<String, MockHttpServletRequestBuilder> chamada : chamadasSobreRecursosDeB().entrySet()) {
            int status = mvc.perform(chamada.getValue().header(AUTHORIZATION, tokenA)).andReturn().getResponse().getStatus();
            softly.assertThat(status).as(chamada.getKey()).isEqualTo(404);
        }
        softly.assertAll();

        assertThat(clienteRepository.findById(clienteB.getId())).isPresent();
        assertThat(declaracaoRepository.findById(declaracaoB.getId())).isPresent();
        assertThat(simulacaoRepository.findById(simulacaoB.getId())).get()
                .extracting(SimulacaoParcelamento::getAprovada).isEqualTo(false);
        assertThat(documentoRepository.findById(documentoB.getId())).isPresent();
    }

    /** "id" no corpo do POST não pode transformar a criação em update de um registro de outro usuário. */
    @Test
    void criarDeclaracaoComIdDeOutroUsuarioNaoSobrescreveORegistroDele() throws Exception {
        Cliente clienteA = cliente(usuarioA, "12345678000195");
        String corpo = DECLARACAO_JSON.replace("{", "{\"id\":" + declaracaoB.getId() + ",");

        mvc.perform(post("/api/declaracoes").param("clienteId", clienteA.getId().toString())
                        .header(AUTHORIZATION, tokenA).contentType(APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(declaracaoB.getId().intValue())));

        Declaracao intacta = declaracaoRepository.findById(declaracaoB.getId()).orElseThrow();
        assertThat(intacta.getCliente().getId()).isEqualTo(clienteB.getId());
        assertThat(intacta.getValorTotal()).isEqualByComparingTo("1000.00");
    }

    /** Controle positivo: qualquer membro do escritório B (não só quem cadastrou) enxerga os recursos dele. */
    @Test
    void membrosDoMesmoEscritorioSeEnxergam() throws Exception {
        String colega = dados.token(dados.usuario(usuarioB.getEscritorio(), "colega-b@test.com", Papel.ASSISTENTE));
        for (String url : List.of(
                "/api/clientes",
                "/api/clientes/" + clienteB.getId(),
                "/api/declaracoes/" + declaracaoB.getId(),
                "/api/simulacoes/" + simulacaoB.getId(),
                "/api/clientes/" + clienteB.getId() + "/documentos/" + documentoB.getId())) {
            mvc.perform(get(url).header(AUTHORIZATION, tokenB)).andExpect(status().isOk());
            mvc.perform(get(url).header(AUTHORIZATION, colega)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/clientes").header(AUTHORIZATION, colega))
                .andExpect(jsonPath("$[0].id").value(clienteB.getId().intValue()));
        mvc.perform(get("/api/clientes").header(AUTHORIZATION, tokenA))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void assistenteLeEImportaMasNaoAlteraNemExclui() throws Exception {
        String assistente = dados.token(dados.usuario(usuarioB.getEscritorio(), "assist-b@test.com", Papel.ASSISTENTE));
        Long c = clienteB.getId();

        mvc.perform(delete("/api/clientes/{id}", c).header(AUTHORIZATION, assistente)).andExpect(status().isForbidden());
        mvc.perform(put("/api/clientes/{id}", c).header(AUTHORIZATION, assistente)
                .contentType(APPLICATION_JSON).content(CLIENTE_JSON)).andExpect(status().isForbidden());
        mvc.perform(post("/api/clientes").header(AUTHORIZATION, assistente)
                .contentType(APPLICATION_JSON).content(CLIENTE_JSON)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/simulacoes/{id}", simulacaoB.getId()).header(AUTHORIZATION, assistente))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/declaracoes/{id}", declaracaoB.getId()).header(AUTHORIZATION, assistente))
                .andExpect(status().isForbidden());

        mvc.perform(importacao(c).header(AUTHORIZATION, assistente)).andExpect(status().isCreated());
        assertThat(clienteRepository.findById(c)).isPresent();
    }

    @Test
    void consultorAlteraClienteMasNaoGerenciaUsuarios() throws Exception {
        String consultor = dados.token(dados.usuario(usuarioB.getEscritorio(), "cons-b@test.com", Papel.CONSULTOR));

        mvc.perform(put("/api/clientes/{id}", clienteB.getId()).header(AUTHORIZATION, consultor)
                .contentType(APPLICATION_JSON).content(CLIENTE_JSON)).andExpect(status().isOk());
        mvc.perform(get("/api/usuarios").header(AUTHORIZATION, consultor)).andExpect(status().isForbidden());
    }

    @Test
    void doisEscritoriosCadastramOMesmoCnpjMasNaoDuasVezesNoMesmo() throws Exception {
        String mesmoCnpj = "{\"razaoSocial\":\"Mesmo Cliente\",\"cnpj\":\"" + clienteB.getCnpj() + "\"}";

        mvc.perform(post("/api/clientes").header(AUTHORIZATION, tokenA).contentType(APPLICATION_JSON).content(mesmoCnpj))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/clientes").header(AUTHORIZATION, tokenB).contentType(APPLICATION_JSON).content(mesmoCnpj))
                .andExpect(status().isConflict());
    }

    @Test
    void respostasDeUsuarioNuncaTrazemSenha() throws Exception {
        mvc.perform(get("/api/usuarios/me").header(AUTHORIZATION, tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("a@test.com"))
                .andExpect(jsonPath("$.senha").doesNotExist());

        String corpo = mvc.perform(put("/api/usuarios/me").header(AUTHORIZATION, tokenA)
                        .contentType(APPLICATION_JSON).content("{\"nome\":\"Novo Nome\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Novo Nome"))
                .andReturn().getResponse().getContentAsString();
        assertThat(corpo).doesNotContain("senha").doesNotContain(usuarioA.getSenha());
    }

    @Test
    void semTokenOuComTokenInvalidoResponde401() throws Exception {
        for (String url : List.of("/api/usuarios/me", "/api/clientes",
                "/api/declaracoes/" + declaracaoB.getId(), "/api/simulacoes/" + simulacaoB.getId(),
                "/api/clientes/" + clienteB.getId() + "/documentos")) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(get(url).header(AUTHORIZATION, "Bearer token-invalido")).andExpect(status().isUnauthorized());
        }
    }

    private Map<String, MockHttpServletRequestBuilder> chamadasSobreRecursosDeB() {
        Long e = clienteB.getId(), d = declaracaoB.getId(), s = simulacaoB.getId(), doc = documentoB.getId();
        String simulacaoJson = """
                {"declaracaoId":%d,"orgao":"RECEITA_FEDERAL","modalidade":"ORDINARIO_RFB","numeroParcelas":10}"""
                .formatted(d);
        return Map.ofEntries(
                Map.entry("GET cliente", get("/api/clientes/{id}", e)),
                Map.entry("PUT cliente", put("/api/clientes/{id}", e).contentType(APPLICATION_JSON).content(CLIENTE_JSON)),
                Map.entry("DELETE cliente", delete("/api/clientes/{id}", e)),
                Map.entry("GET declaracao", get("/api/declaracoes/{id}", d)),
                Map.entry("GET declaracoes do cliente", get("/api/declaracoes/cliente/{id}", e)),
                Map.entry("POST declaracao no cliente", post("/api/declaracoes").param("clienteId", e.toString())
                        .contentType(APPLICATION_JSON).content(DECLARACAO_JSON)),
                Map.entry("PUT declaracao", put("/api/declaracoes/{id}", d).contentType(APPLICATION_JSON).content(DECLARACAO_JSON)),
                Map.entry("DELETE declaracao", delete("/api/declaracoes/{id}", d)),
                Map.entry("GET simulacao", get("/api/simulacoes/{id}", s)),
                Map.entry("GET simulacoes da declaracao", get("/api/simulacoes/declaracao/{id}", d)),
                Map.entry("POST calcular", post("/api/simulacoes/calcular").contentType(APPLICATION_JSON).content(simulacaoJson)),
                Map.entry("POST calcular/salvar", post("/api/simulacoes/calcular/salvar").contentType(APPLICATION_JSON).content(simulacaoJson)),
                Map.entry("PATCH aprovar simulacao", patch("/api/simulacoes/{id}/aprovar", s)),
                Map.entry("DELETE simulacao", delete("/api/simulacoes/{id}", s)),
                Map.entry("GET documentos do cliente", get("/api/clientes/{e}/documentos", e)),
                Map.entry("GET documento", get("/api/clientes/{e}/documentos/{d}", e, doc)),
                Map.entry("GET arquivo do documento", get("/api/clientes/{e}/documentos/{d}/arquivo", e, doc)),
                Map.entry("POST importar documento", importacao(e))
        );
    }

    private static MockHttpServletRequestBuilder importacao(Long clienteId) {
        return multipart("/api/clientes/{e}/documentos", clienteId)
                .file(new MockMultipartFile("arquivo", "x.csv", "text/csv", "a;b".getBytes()))
                .file(new MockMultipartFile("documento", "", "application/json",
                        "{\"tipo\":\"OUTRO\",\"tributos\":[\"IRPJ\"],\"periodoInicio\":\"2024-01-01\",\"periodoFim\":\"2024-01-31\"}".getBytes()));
    }

    private Cliente cliente(Usuario dono, String cnpj) {
        Cliente e = new Cliente();
        e.setRazaoSocial("Cliente de " + dono.getEmail());
        e.setCnpj(cnpj);
        e.setEscritorio(dono.getEscritorio());
        e.setCriadoPor(dono);
        return clienteRepository.save(e);
    }

    private Declaracao declaracao(Cliente cliente) {
        Declaracao d = new Declaracao();
        d.setTipo("DCTF");
        d.setTributo(TipoTributo.IRPJ);
        d.setPeriodoInicio(LocalDate.of(2024, 1, 1));
        d.setPeriodoFim(LocalDate.of(2024, 1, 31));
        d.setValorTotal(new BigDecimal("1000.00"));
        d.setDataVencimento(LocalDate.of(2024, 2, 20));
        d.setCliente(cliente);
        d.setUsuario(cliente.getCriadoPor());
        return declaracaoRepository.save(d);
    }

    private SimulacaoParcelamento simulacao(Declaracao declaracao) {
        SimulacaoParcelamento s = new SimulacaoParcelamento();
        s.setOrgao(OrgaoReceita.RECEITA_FEDERAL);
        s.setModalidade(ModalidadeParcelamento.ORDINARIO_RFB);
        s.setValorOriginal(declaracao.getValorTotal());
        s.setDataVencimentoOriginal(declaracao.getDataVencimento());
        s.setDataCalculo(LocalDate.of(2024, 6, 1));
        s.setDeclaracao(declaracao);
        return simulacaoRepository.save(s);
    }

    private DocumentoFiscal documento(Cliente cliente) {
        DocumentoFiscal d = new DocumentoFiscal();
        d.setCliente(cliente);
        d.setUsuario(cliente.getCriadoPor());
        d.setTipo(DocumentoTipo.DCTF);
        d.setEsfera(DocumentoEsfera.FEDERAL);
        d.setJuridicao(DocumentoJuridicao.UNIAO);
        d.setTributos(Set.of(TipoTributo.IRPJ));
        d.setPeriodoInicio(LocalDate.of(2024, 1, 1));
        d.setPeriodoFim(LocalDate.of(2024, 1, 31));
        d.setNomeOriginalArquivo("dctf.pdf");
        d.setNomeArquivoInterno("interno.pdf");
        d.setExtensao(".pdf");
        d.setMimeType("application/pdf");
        d.setTamanhoBytes(10L);
        d.setCaminhoArquivo("target/test-uploads/interno.pdf");
        return documentoRepository.save(d);
    }
}
