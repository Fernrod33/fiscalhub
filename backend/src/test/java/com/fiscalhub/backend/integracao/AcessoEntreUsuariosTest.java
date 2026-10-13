package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Declaracao;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.Empresa;
import com.fiscalhub.backend.entity.SimulacaoParcelamento;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.DocumentoEsfera;
import com.fiscalhub.backend.entity.enums.DocumentoJuridicao;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.entity.enums.ModalidadeParcelamento;
import com.fiscalhub.backend.entity.enums.OrgaoReceita;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.repository.DeclaracaoRepository;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.fiscalhub.backend.repository.EmpresaRepository;
import com.fiscalhub.backend.repository.SimulacaoParcelamentoRepository;
import com.fiscalhub.backend.repository.UsuarioRepository;
import com.fiscalhub.backend.security.JwtUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
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
 * Prova que o usuário A não lê, edita nem exclui recursos do usuário B (sempre 404),
 * que /api/usuarios nunca devolve a senha e que sem token a resposta é 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestcontainer.class)
@Transactional
class AcessoEntreUsuariosTest {

    private static final String DECLARACAO_JSON = """
            {"tipo":"DCTF","periodoInicio":"2024-01-01","periodoFim":"2024-01-31",
             "valorTotal":100.00,"status":"EM_ABERTO"}""";
    private static final String EMPRESA_JSON = """
            {"razaoSocial":"Invasora Ltda","cnpj":"12345678000195"}""";

    @Autowired MockMvc mvc;
    @Autowired JwtUtils jwtUtils;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired DeclaracaoRepository declaracaoRepository;
    @Autowired SimulacaoParcelamentoRepository simulacaoRepository;
    @Autowired DocumentoFiscalRepository documentoRepository;

    private String tokenA;
    private String tokenB;
    private Usuario usuarioA;
    private Empresa empresaB;
    private Declaracao declaracaoB;
    private SimulacaoParcelamento simulacaoB;
    private DocumentoFiscal documentoB;

    @BeforeEach
    void setUp() {
        usuarioA = usuario("a@test.com");
        Usuario usuarioB = usuario("b@test.com");
        empresaB = empresa(usuarioB);
        declaracaoB = declaracao(empresaB);
        simulacaoB = simulacao(declaracaoB);
        documentoB = documento(empresaB);
        tokenA = "Bearer " + jwtUtils.generateTokenFromUsername(usuarioA.getEmail());
        tokenB = "Bearer " + jwtUtils.generateTokenFromUsername(usuarioB.getEmail());
    }

    @Test
    void usuarioANaoLeEditaNemExcluiRecursosDeB() throws Exception {
        SoftAssertions softly = new SoftAssertions();
        for (Map.Entry<String, MockHttpServletRequestBuilder> chamada : chamadasSobreRecursosDeB().entrySet()) {
            int status = mvc.perform(chamada.getValue().header(AUTHORIZATION, tokenA)).andReturn().getResponse().getStatus();
            softly.assertThat(status).as(chamada.getKey()).isEqualTo(404);
        }
        softly.assertAll();

        assertThat(empresaRepository.findById(empresaB.getId())).isPresent();
        assertThat(declaracaoRepository.findById(declaracaoB.getId())).isPresent();
        assertThat(simulacaoRepository.findById(simulacaoB.getId())).get()
                .extracting(SimulacaoParcelamento::getAprovada).isEqualTo(false);
        assertThat(documentoRepository.findById(documentoB.getId())).isPresent();
    }

    /** Controle positivo: os mesmos recursos existem e o dono os enxerga, então o 404 acima é do controle de acesso. */
    @Test
    void donoLeOsProprios() throws Exception {
        for (String url : List.of(
                "/api/empresas/" + empresaB.getId(),
                "/api/declaracoes/" + declaracaoB.getId(),
                "/api/simulacoes/" + simulacaoB.getId(),
                "/api/empresas/" + empresaB.getId() + "/documentos/" + documentoB.getId())) {
            mvc.perform(get(url).header(AUTHORIZATION, tokenB)).andExpect(status().isOk());
        }
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
        for (String url : List.of("/api/usuarios/me", "/api/empresas/minhas",
                "/api/declaracoes/" + declaracaoB.getId(), "/api/simulacoes/" + simulacaoB.getId(),
                "/api/empresas/" + empresaB.getId() + "/documentos")) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(get(url).header(AUTHORIZATION, "Bearer token-invalido")).andExpect(status().isUnauthorized());
        }
    }

    private Map<String, MockHttpServletRequestBuilder> chamadasSobreRecursosDeB() {
        Long e = empresaB.getId(), d = declaracaoB.getId(), s = simulacaoB.getId(), doc = documentoB.getId();
        String simulacaoJson = """
                {"declaracaoId":%d,"orgao":"RECEITA_FEDERAL","modalidade":"ORDINARIO_RFB","numeroParcelas":10}"""
                .formatted(d);
        return Map.ofEntries(
                Map.entry("GET empresa", get("/api/empresas/{id}", e)),
                Map.entry("PUT empresa", put("/api/empresas/{id}", e).contentType(APPLICATION_JSON).content(EMPRESA_JSON)),
                Map.entry("DELETE empresa", delete("/api/empresas/{id}", e)),
                Map.entry("GET declaracao", get("/api/declaracoes/{id}", d)),
                Map.entry("GET declaracoes da empresa", get("/api/declaracoes/empresa/{id}", e)),
                Map.entry("POST declaracao na empresa", post("/api/declaracoes").param("empresaId", e.toString())
                        .contentType(APPLICATION_JSON).content(DECLARACAO_JSON)),
                Map.entry("PUT declaracao", put("/api/declaracoes/{id}", d).contentType(APPLICATION_JSON).content(DECLARACAO_JSON)),
                Map.entry("DELETE declaracao", delete("/api/declaracoes/{id}", d)),
                Map.entry("GET simulacao", get("/api/simulacoes/{id}", s)),
                Map.entry("GET simulacoes da declaracao", get("/api/simulacoes/declaracao/{id}", d)),
                Map.entry("POST calcular", post("/api/simulacoes/calcular").contentType(APPLICATION_JSON).content(simulacaoJson)),
                Map.entry("POST calcular/salvar", post("/api/simulacoes/calcular/salvar").contentType(APPLICATION_JSON).content(simulacaoJson)),
                Map.entry("PATCH aprovar simulacao", patch("/api/simulacoes/{id}/aprovar", s)),
                Map.entry("DELETE simulacao", delete("/api/simulacoes/{id}", s)),
                Map.entry("GET documentos da empresa", get("/api/empresas/{e}/documentos", e)),
                Map.entry("GET documento", get("/api/empresas/{e}/documentos/{d}", e, doc)),
                Map.entry("GET arquivo do documento", get("/api/empresas/{e}/documentos/{d}/arquivo", e, doc)),
                Map.entry("POST importar documento", multipart("/api/empresas/{e}/documentos", e)
                        .file(new MockMultipartFile("arquivo", "x.csv", "text/csv", "a;b".getBytes()))
                        .file(new MockMultipartFile("documento", "", "application/json",
                                "{\"tipo\":\"OUTRO\",\"tributos\":[\"IRPJ\"],\"periodoInicio\":\"2024-01-01\",\"periodoFim\":\"2024-01-31\"}".getBytes())))
        );
    }

    private Usuario usuario(String email) {
        Usuario u = new Usuario();
        u.setNome(email);
        u.setEmail(email);
        u.setSenha("$2a$10$hashDeTesteQueNuncaPodeAparecerNaResposta");
        return usuarioRepository.save(u);
    }

    private Empresa empresa(Usuario dono) {
        Empresa e = new Empresa();
        e.setRazaoSocial("Empresa de " + dono.getEmail());
        e.setCnpj("11222333000181");
        e.setUsuario(dono);
        return empresaRepository.save(e);
    }

    private Declaracao declaracao(Empresa empresa) {
        Declaracao d = new Declaracao();
        d.setTipo("DCTF");
        d.setTributo(TipoTributo.IRPJ);
        d.setPeriodoInicio(LocalDate.of(2024, 1, 1));
        d.setPeriodoFim(LocalDate.of(2024, 1, 31));
        d.setValorTotal(new BigDecimal("1000.00"));
        d.setDataVencimento(LocalDate.of(2024, 2, 20));
        d.setEmpresa(empresa);
        d.setUsuario(empresa.getUsuario());
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

    private DocumentoFiscal documento(Empresa empresa) {
        DocumentoFiscal d = new DocumentoFiscal();
        d.setEmpresa(empresa);
        d.setUsuario(empresa.getUsuario());
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
