package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.capacidade.CapagEstimada;
import com.fiscalhub.backend.capacidade.ClassificacaoParana;
import com.fiscalhub.backend.capacidade.ParametrosCapacidade;
import com.fiscalhub.backend.capacidade.RegrasCapacidade;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.Escritorio;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Faixas das duas normas nos valores-limite, com as regras da tabela (as que o cálculo usa), a versão vigente do art. 8º
 * por data, insumo faltando, rótulos, isolamento, papéis e auditoria dos endpoints da capacidade.
 */
@TesteIntegracao
class CapacidadeRegrasEAcessoTest {

    @Autowired RegrasCapacidade regras;
    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired JdbcTemplate jdbc;

    static final LocalDate HOJE = LocalDate.of(2026, 10, 14);

    @Test
    void faixasDaCdaNoPrNosValoresLimite() {
        var r = regras.vigente("PR_CDA_INDICES", HOJE);
        assertThat(r.faixa("parcelamentos", BigDecimal.ZERO)).isEqualByComparingTo("1.000");
        assertThat(r.faixa("parcelamentos", BigDecimal.valueOf(2))).isEqualByComparingTo("1.646");
        assertThat(r.faixa("parcelamentos", BigDecimal.valueOf(3))).isEqualByComparingTo("1.519");
        assertThat(r.faixa("tempoInscricaoAnos", BigDecimal.ONE)).isEqualByComparingTo("9.337");
        assertThat(r.faixa("tempoInscricaoAnos", new BigDecimal("1.000001"))).isEqualByComparingTo("2.748");
        assertThat(r.faixa("tempoInscricaoAnos", BigDecimal.valueOf(4))).isEqualByComparingTo("1.608");
        assertThat(r.faixa("tempoInscricaoAnos", new BigDecimal("4.01"))).isEqualByComparingTo("1.000");
        assertThat(r.faixa("valor", new BigDecimal("499.99"))).isEqualByComparingTo("3.850");
        assertThat(r.faixa("valor", new BigDecimal("500.00"))).isEqualByComparingTo("2.820");
        assertThat(r.faixa("valor", new BigDecimal("249999.99"))).isEqualByComparingTo("1.195");
        assertThat(r.faixa("valor", new BigDecimal("250000.00"))).isEqualByComparingTo("1.000");
        var classes = regras.vigente("PR_CDA_CLASSES", HOJE).parametros();
        assertThat(ClassificacaoParana.classeCda(classes, new BigDecimal("2.865548"))).isEqualTo("B"); // "maiores do que"
        assertThat(ClassificacaoParana.classeCda(classes, new BigDecimal("2.865549"))).isEqualTo("A");
        assertThat(ClassificacaoParana.classeCda(classes, new BigDecimal("1.558998"))).isEqualTo("D");
        assertThat(ClassificacaoParana.classeParcelamento(classes, new BigDecimal("0.70"))).isEqualTo("A");
        assertThat(ClassificacaoParana.classeParcelamento(classes, new BigDecimal("0.30"))).isEqualTo("B");
        assertThat(ClassificacaoParana.classeParcelamento(classes, new BigDecimal("0.299"))).isNull(); // vale o índice
        // média geométrica: (1 × 2,748 × 2,075 × 1,195 × 3,766 × 7,861)^(1/6), CDA 2025.0087654-1 da massa
        assertThat(ClassificacaoParana.mediaGeometrica(List.of(new BigDecimal("1.000"), new BigDecimal("2.748"), new BigDecimal("2.075"),
                new BigDecimal("1.195"), new BigDecimal("3.766"), new BigDecimal("7.861")))).isEqualByComparingTo("2.421736");
    }

    @Test
    void faixasDoDevedorNoPrEVersaoDoArt8PorData() {
        var r = regras.vigente("PR_DEVEDOR_INDICES", HOJE);
        assertThat(r.faixa("endividamento", new BigDecimal("0.10"))).isEqualByComparingTo("4");
        assertThat(r.faixa("endividamento", new BigDecimal("0.100001"))).isEqualByComparingTo("3");
        assertThat(r.faixa("endividamento", new BigDecimal("0.50"))).isEqualByComparingTo("2");
        assertThat(r.faixa("endividamento", new BigDecimal("0.51"))).isEqualByComparingTo("1");
        assertThat(r.faixa("pagamentoDias", BigDecimal.valueOf(30))).isEqualByComparingTo("3");
        assertThat(r.faixa("pagamentoDias", BigDecimal.valueOf(31))).isEqualByComparingTo("2");
        assertThat(r.faixa("pagamentoDias", BigDecimal.valueOf(91))).isEqualByComparingTo("1");
        var atual = regras.vigente("PR_DEVEDOR_CLASSES", HOJE).parametros();
        assertThat(ClassificacaoParana.classeDevedor(atual, BigDecimal.valueOf(18))).isEqualTo("A");
        assertThat(ClassificacaoParana.classeDevedor(atual, BigDecimal.valueOf(17))).isEqualTo("B");
        assertThat(ClassificacaoParana.classeDevedor(atual, BigDecimal.valueOf(15))).isEqualTo("B");
        assertThat(ClassificacaoParana.classeDevedor(atual, BigDecimal.valueOf(14))).isEqualTo("C");
        assertThat(ClassificacaoParana.classeDevedor(atual, BigDecimal.valueOf(11))).isEqualTo("D");
        // redação original (até 23/08/2026): A acima de 12
        var original = regras.vigente("PR_DEVEDOR_CLASSES", LocalDate.of(2026, 8, 23));
        assertThat(original.artigo()).contains("original");
        assertThat(ClassificacaoParana.classeDevedor(original.parametros(), BigDecimal.valueOf(13))).isEqualTo("A");
    }

    @Test
    void faixasDaCapagSaoParametroERevisaoSoComCOuD() {
        var p = ParametrosCapacidade.PADRAO.capag();
        assertThat(CapagEstimada.classe(new BigDecimal("2"), p)).isEqualTo("A");
        assertThat(CapagEstimada.classe(new BigDecimal("1.9999"), p)).isEqualTo("B");
        assertThat(CapagEstimada.classe(new BigDecimal("0.5"), p)).isEqualTo("C");
        assertThat(CapagEstimada.classe(new BigDecimal("0.4999"), p)).isEqualTo("D");
        assertThat(CapagEstimada.revisaoVantajosa("C", "B")).isTrue();
        assertThat(CapagEstimada.revisaoVantajosa("D", "C")).isTrue();
        assertThat(CapagEstimada.revisaoVantajosa("B", "A")).isFalse(); // B não dá desconto
        assertThat(CapagEstimada.revisaoVantajosa("C", "C")).isFalse();
        assertThat(regras.vigente("PGFN_HORIZONTE", HOJE).parametros().get("anos").asInt()).isEqualTo(5);
    }

    @Test
    void semDocumentosOsCriteriosFicamNaoDisponiveisComRotulos() throws Exception {
        Escritorio e = dados.escritorio("Capacidade " + UUID.randomUUID());
        Usuario consultor = dados.usuario(e, "con-" + UUID.randomUUID() + "@t.com", Papel.CONSULTOR);
        Cliente cliente = dados.cliente(consultor, "11444777000161");
        String base = "/api/clientes/" + cliente.getId() + "/capacidade";
        mvc.perform(get(base).with(dados.sessao(consultor))).andExpect(status().isOk())
                .andExpect(jsonPath("$.capagOficial").doesNotExist())
                .andExpect(jsonPath("$.metodologias[0].rotulo").value("ESTIMATIVA"))
                .andExpect(jsonPath("$.metodologias[0].classe").doesNotExist())
                .andExpect(jsonPath("$.metodologias[1].classe").doesNotExist())
                .andExpect(jsonPath("$.metodologias[1].criterios[0].naoDisponivel").value(org.hamcrest.Matchers.startsWith("não disponível — importe")))
                .andExpect(jsonPath("$.metodologias[2].rotuloTexto").value("análise do consultor — não é critério oficial"));
    }

    @Test
    void parametrosEDeParaSoConsultorOuAdminComAuditoriaEIsolamento() throws Exception {
        Escritorio e = dados.escritorio("Parâmetros " + UUID.randomUUID());
        RequestPostProcessor consultor = dados.sessao(dados.usuario(e, "con-" + UUID.randomUUID() + "@t.com", Papel.CONSULTOR));
        Usuario assistenteU = dados.usuario(e, "ass-" + UUID.randomUUID() + "@t.com", Papel.ASSISTENTE);
        RequestPostProcessor assistente = dados.sessao(assistenteU);
        RequestPostProcessor outro = dados.sessao(dados.usuario(dados.escritorio("Outro " + UUID.randomUUID()), "o-" + UUID.randomUUID() + "@t.com", Papel.ADMIN));
        String corpo = """
                {"capag":{"faixaA":2.5,"faixaB":1.2,"faixaC":0.6,"metodoConsiderado":"FLUXO_DE_CAIXA","exercicios":2,
                 "realizacaoDisponivel":100,"realizacaoCreditos":70,"realizacaoEstoques":40,"realizacaoImobilizado":20},
                 "gerencial":{"percentualFcoLivre":25}}""";
        mvc.perform(put("/api/escritorio/parametros-capacidade").with(assistente).contentType(APPLICATION_JSON).content(corpo)).andExpect(status().isForbidden());
        mvc.perform(put("/api/escritorio/parametros-capacidade").with(consultor).contentType(APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.capag.faixaA").value(2.5)).andExpect(jsonPath("$.atualizadoPor").isNotEmpty());
        mvc.perform(put("/api/escritorio/parametros-capacidade").with(consultor).contentType(APPLICATION_JSON)
                .content(corpo.replace("\"faixaB\":1.2", "\"faixaB\":3"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/escritorio/parametros-capacidade").with(outro)).andExpect(jsonPath("$.capag.faixaA").value(2)); // padrão do outro
        mvc.perform(put("/api/escritorio/dfc-depara").with(consultor).contentType(APPLICATION_JSON)
                .content("{\"prefixo\":\"3.01.01.09.01.08\",\"categoria\":\"EMPRESTIMOS\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/escritorio/dfc-depara").with(consultor).contentType(APPLICATION_JSON)
                .content("{\"prefixo\":\"1.01.01\",\"categoria\":\"CAIXA\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/escritorio/dfc-depara").with(outro))
                .andExpect(jsonPath("$[?(@.prefixo == '3.01.01.09.01.08')]").isEmpty());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_evento WHERE acao IN ('PARAMETROS_CAPACIDADE_ALTERAR', 'DFC_DEPARA_ALTERAR') "
                + "AND escritorio_id = ?", Long.class, e.getId())).isEqualTo(2);
        Cliente cliente = dados.cliente(assistenteU, "11444777000161");
        mvc.perform(get("/api/clientes/" + cliente.getId() + "/capacidade").with(outro)).andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes/" + cliente.getId() + "/dfc?exercicio=2025").with(outro)).andExpect(status().isNotFound());
        mvc.perform(post("/api/clientes/" + cliente.getId() + "/capacidade/pr/simulacao").with(outro).contentType(APPLICATION_JSON)
                .content("{\"quitar\":[]}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes/" + cliente.getId() + "/dfc?exercicio=2025").with(assistente)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("importe a ECD")));
    }
}
