package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static com.fiscalhub.backend.integracao.Espera.aguardar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Débitos, declarado e pago pelo pipeline: retificação, fontes que divergem, layout desconhecido e simulação. */
@TesteIntegracao
class SituacaoFiscalTest {

    static final Path MASSA = Path.of("src", "test", "resources", "massa-ficticia");
    static final String DEC = "dctf/DCTF_Mensal_112024_11222333000181.dec";

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired DocumentoFiscalRepository documentoRepository;
    @Autowired JdbcTemplate jdbc;

    private RequestPostProcessor sessao;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        String sufixo = UUID.randomUUID().toString();
        Usuario consultor = dados.usuario(dados.escritorio("Situacao " + sufixo), "sit-" + sufixo + "@test.com", Papel.CONSULTOR);
        sessao = dados.sessao(consultor);
        cliente = dados.cliente(consultor, "11222333000181");
    }

    @Test
    void dctfRetificadoraSubstituiAOriginalComHistorico() throws Exception {
        String original = dec();
        long id1 = importar("original.dec", original.getBytes(StandardCharsets.ISO_8859_1));
        String retificadora = original.replace("ORIGINAL    ", "RETIFICADORA")
                .replace("R10512301112024241220240000000041200", "R10512301112024241220240000000045000");
        long id2 = importar("retificadora.dec", retificadora.getBytes(StandardCharsets.ISO_8859_1));
        assertThat(situacao(id1)).isEqualTo("SUBSTITUIDA");
        assertThat(situacao(id2)).isEqualTo("VIGENTE");
        mvc.perform(get("/api/clientes/{c}/declaracoes", cliente.getId()).with(sessao))
                .andExpect(jsonPath("$[?(@.codigoReceita == '5123-01')].valor").value(45000.00))
                .andExpect(jsonPath("$[?(@.codigoReceita == '5123-01')].finalidade").value("SUBSTITUTA"));
    }

    @Test
    void mesmaDeclaracaoEmDoisFormatosComValoresDiferentesViraAlerta() throws Exception {
        importar("DCTF.pdf", Files.readAllBytes(MASSA.resolve("dctf/DCTF_Mensal_112024_11222333000181.pdf")));
        long dec = importar("dctf.dec", dec().replace("R10585601112024241220240000000028190", "R10585601112024241220240000000028000")
                .getBytes(StandardCharsets.ISO_8859_1));
        assertThat(regras(dec)).contains("DECLARACAO_FORMATOS_DIVERGENTES");
        mvc.perform(get("/api/clientes/{c}/declaracoes", cliente.getId()).with(sessao))
                .andExpect(jsonPath("$.length()").value(4)) // um item por código, com as duas origens
                .andExpect(jsonPath("$[?(@.codigoReceita == '5856-01')].divergente").value(true))
                .andExpect(jsonPath("$[?(@.codigoReceita == '6912-01')].origens.length()").value(2));
    }

    @Test
    void mesmaInscricaoEmDuasFontesEhUmDebitoEDivergenciaMarca() throws Exception {
        long rsf = importar("rsf.pdf", Files.readAllBytes(MASSA.resolve("situacao-fiscal/Relatorio_Situacao_Fiscal_11222333000181_20261014.pdf")));
        importar("regularize.pdf", Files.readAllBytes(MASSA.resolve("pgfn/Extrato_Inscricoes_Regularize_11222333000181.pdf")));
        mvc.perform(get("/api/clientes/{c}/debitos", cliente.getId()).param("orgao", "PGFN").with(sessao))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.numeroInscricao == '90 3 24 002201-55')].origens.length()").value(2))
                .andExpect(jsonPath("$[?(@.numeroInscricao == '90 3 24 002201-55')].numeroProcesso").value("10980.700112/2024-31"))
                .andExpect(jsonPath("$[?(@.numeroInscricao == '90 3 24 002201-55')].valorPrincipal").value(38750.00));
        // outra fonte mais recente da mesma inscrição, com outro principal: vale ela e o débito fica divergente
        jdbc.update("""
                INSERT INTO debito (cliente_id, documento_origem_id, escrituracao_id, orgao_credor, tributo, valor_principal,
                    situacao, numero_inscricao, chave, data_referencia, linha, trecho)
                SELECT cliente_id, documento_origem_id, escrituracao_id, orgao_credor, tributo, 40000.00, situacao,
                    numero_inscricao, chave, DATE '2026-10-20', 99, 'fonte nova' FROM debito
                WHERE documento_origem_id = ? AND numero_inscricao = '90 3 24 002201-55'""", rsf);
        mvc.perform(get("/api/clientes/{c}/debitos", cliente.getId()).param("orgao", "PGFN").with(sessao))
                .andExpect(jsonPath("$[?(@.numeroInscricao == '90 3 24 002201-55')].divergente").value(true))
                .andExpect(jsonPath("$[?(@.numeroInscricao == '90 3 24 002201-55')].valorPrincipal").value(40000.00))
                .andExpect(jsonPath("$[?(@.numeroInscricao == '90 3 24 002201-55')].origens.length()").value(3));
        mvc.perform(get("/api/clientes/{c}/debitos", cliente.getId()).param("situacao", "EM_ABERTO").with(sessao))
                .andExpect(jsonPath("$[*].tributo").value(org.hamcrest.Matchers.containsInAnyOrder("IPI", "COFINS")));
    }

    @Test
    void debitoImportadoAlimentaASimulacaoEInscricaoSemVencimentoEhRecusada() throws Exception {
        importar("rsf.pdf", Files.readAllBytes(MASSA.resolve("situacao-fiscal/Relatorio_Situacao_Fiscal_11222333000181_20261014.pdf")));
        dados.selic(YearMonth.of(2026, 10), YearMonth.of(2026, 11), "0.90");
        String debitos = mvc.perform(get("/api/clientes/{c}/debitos", cliente.getId()).with(sessao))
                .andReturn().getResponse().getContentAsString();
        Number cofins = JsonPath.<List<Number>>read(debitos, "$[?(@.tributo == 'COFINS' && @.orgaoCredor == 'RFB')].id").getFirst();
        Number inscricao = JsonPath.<List<Number>>read(debitos, "$[?(@.numeroInscricao == '90 3 24 002201-55')].id").getFirst();
        mvc.perform(post("/api/simulacoes/calcular").with(sessao).contentType(APPLICATION_JSON).content(simulacao(cofins)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorOriginal").value(5079.00))
                .andExpect(jsonPath("$.dataVencimentoOriginal").value("2026-09-25"));
        mvc.perform(post("/api/simulacoes/calcular").with(sessao).contentType(APPLICATION_JSON).content(simulacao(inscricao)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("não traz vencimento ou principal")));
    }

    @Test
    void pdfComLayoutDesconhecidoEPdfSemTexto() throws Exception {
        long desconhecido = importar("outro.pdf", pdf("RELATÓRIO QUALQUER DE OUTRO SISTEMA"));
        assertThat(documentoRepository.findById(desconhecido).orElseThrow().getStatus()).isEqualTo(DocumentoStatus.LAYOUT_NAO_RECONHECIDO);
        assertThat(regras(desconhecido)).containsExactly("LAYOUT_NAO_RECONHECIDO");
        mvc.perform(get("/api/documentos/{d}/status", desconhecido).with(sessao))
                .andExpect(jsonPath("$.mensagem").value(containsString("layout deste PDF não é reconhecido")));
        long imagem = importar("scan.pdf", pdf(null));
        assertThat(documentoRepository.findById(imagem).orElseThrow().getStatus()).isEqualTo(DocumentoStatus.ERRO);
        assertThat(regras(imagem)).containsExactly("PDF_SEM_TEXTO");
    }

    // ---------------------------------------------------------------- apoio

    private long importar(String nome, byte[] conteudo) throws Exception {
        String tipo = nome.endsWith(".pdf") ? "application/pdf" : "application/octet-stream";
        String resposta = mvc.perform(multipart("/api/documentos").file(new MockMultipartFile("arquivo", nome, tipo, conteudo)).with(sessao))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(resposta, "$.id")).longValue();
        aguardar(() -> documentoRepository.findById(id).orElseThrow().getStatus().finalizado()
                && jdbc.queryForObject("SELECT count(*) FROM job_processamento WHERE documento_id = ? AND status IN ('PENDENTE', 'PROCESSANDO')",
                Long.class, id) == 0, 60_000);
        return id;
    }

    private static String dec() throws IOException {
        return Files.readString(MASSA.resolve(DEC), StandardCharsets.ISO_8859_1);
    }

    private String situacao(long documentoId) {
        return jdbc.queryForObject("SELECT situacao FROM escrituracao_contabil WHERE documento_id = ?", String.class, documentoId);
    }

    private List<String> regras(long documentoId) {
        return jdbc.queryForList("SELECT regra FROM inconsistencia_importacao WHERE documento_id = ?", String.class, documentoId);
    }

    private String simulacao(Number debito) {
        return """
                {"clienteId":%d,"debitoIds":[%d],"orgao":"RECEITA_FEDERAL","modalidade":"ORDINARIO_RFB",
                 "numeroParcelas":2,"dataCalculo":"2026-11-20"}""".formatted(cliente.getId(), debito.longValue());
    }

    /** PDF de uma página com a linha dada (null: sem texto, como um scan). */
    private static byte[] pdf(String linha) throws IOException {
        try (PDDocument d = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            d.addPage(pagina);
            try (PDPageContentStream c = new PDPageContentStream(d, pagina)) {
                if (linha != null) {
                    c.beginText();
                    c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                    c.newLineAtOffset(50, 700);
                    c.showText(linha);
                    c.endText();
                } else {
                    c.addRect(50, 50, 200, 200);
                    c.fill();
                }
            }
            d.save(saida);
            return saida.toByteArray();
        }
    }
}
