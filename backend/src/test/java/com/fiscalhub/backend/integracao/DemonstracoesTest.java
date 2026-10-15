package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.fiscalhub.backend.integracao.Espera.aguardar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Plano referencial oficial carregado, Balanço e DRE referenciais, comparativo com AH/AV, conferência e origem. */
@TesteIntegracao
class DemonstracoesTest {

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired DocumentoFiscalRepository documentoRepository;
    @Autowired JdbcTemplate jdbc;

    private Usuario consultor;
    private RequestPostProcessor sessao;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        String sufixo = UUID.randomUUID().toString();
        consultor = dados.usuario(dados.escritorio("Demonstr " + sufixo), "dem-" + sufixo + "@test.com", Papel.CONSULTOR);
        sessao = dados.sessao(consultor);
        cliente = dados.cliente(consultor, "11222333000181");
    }

    @Test
    void tabelasOficiaisCarregadasComAFonte() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tabela_referencial", Long.class)).isEqualTo(16);
        assertThat(jdbc.queryForObject("SELECT fonte FROM tabela_referencial WHERE tabela = 'SPEDECF_DINAMICA_L100_A_2021'",
                String.class)).contains("pacote 112", "tabela 1494");
        assertThat(jdbc.queryForObject("SELECT descricao FROM conta_referencial WHERE tabela = 'SPEDECF_DINAMICA_L100_A_2021' "
                + "AND codigo = '1.01.01.01.01'", String.class)).isEqualTo("Caixa Matriz");
    }

    @Test
    void balancoReferencialComparativoComAhAvConferenciaEOrigem() throws Exception {
        importar("ecd-2020-l9.txt");
        importar("ecd-minima-l9.txt");

        String json = mvc.perform(get("/api/clientes/{c}/demonstracoes", cliente.getId()).param("tipo", "BP")
                        .param("origem", "REFERENCIAL").with(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercicios[*].exercicio", contains(2020, 2021)))
                .andExpect(jsonPath("$.exercicios[1].tabelaReferencial").value("SPEDECF_DINAMICA_L100_A_2021"))
                .andExpect(jsonPath("$.linhas[0].codigo").value("1"))
                .andExpect(jsonPath("$.linhas[0].valores['2021'].valor").value(6000.00))
                .andExpect(jsonPath("$.linhas[0].valores['2020'].valor").value(3000.00))
                .andExpect(jsonPath("$.linhas[0].valores['2021'].ah").value(100.00))
                .andExpect(jsonPath("$.linhas[0].valores['2021'].av").value(100.00))
                .andExpect(jsonPath("$.linhas[?(@.codigo == '1.01.01.01.01')].valores['2021'].av").value(66.67))
                .andExpect(jsonPath("$.linhas[?(@.codigo == '2')].valores['2021'].valor").value(-6000.00)) // credor −
                .andExpect(jsonPath("$.linhas[?(@.codigo == '2')].valores['2021'].ah").value(100.00)) // passivo dobrou: +100%
                .andExpect(jsonPath("$.conferencia[?(@.exercicio == 2021 && @.item == 'Ativo')].diferenca").value(0.00))
                .andReturn().getResponse().getContentAsString();

        // origem: contas da empresa que compõem o Ativo, com os saldos (registro e linha) usados
        Number linhaAtivo = JsonPath.read(json, "$.linhas[0].valores['2021'].linhaId");
        mvc.perform(get("/api/clientes/{c}/demonstracoes/linhas/{l}/origem", cliente.getId(), linhaAtivo.longValue()).with(sessao))
                .andExpect(jsonPath("$.origem").value("REFERENCIAL"))
                .andExpect(jsonPath("$.arquivo").value("ecd-minima-l9.txt"))
                .andExpect(jsonPath("$.contas[*].codigo", contains("1.1.01", "1.1.02")))
                .andExpect(jsonPath("$.contas[0].valor").value(4000.00))
                .andExpect(jsonPath("$.contas[0].saldos[0].registro").value("I155"))
                .andExpect(jsonPath("$.contas[0].saldos[0].linha").value(26));
    }

    @Test
    void dreReferencialEPublicadaComOrigemNoJ150() throws Exception {
        importar("ecd-minima-l9.txt");

        mvc.perform(get("/api/clientes/{c}/demonstracoes", cliente.getId()).param("tipo", "DRE").param("origem", "REFERENCIAL").with(sessao))
                .andExpect(jsonPath("$.linhas[0].codigo").value("3"))
                .andExpect(jsonPath("$.linhas[0].valores['2021'].valor").value(-4500.00))
                .andExpect(jsonPath("$.linhas[0].valores['2021'].av").value(56.25)) // sobre a receita líquida (−8.000)
                .andExpect(jsonPath("$.conferencia[0].item").value("Resultado do exercício"))
                .andExpect(jsonPath("$.conferencia[0].publicada").value(-4500.00));

        String json = mvc.perform(get("/api/clientes/{c}/demonstracoes", cliente.getId()).param("tipo", "DRE").param("origem", "PUBLICADA").with(sessao))
                .andExpect(jsonPath("$.linhas[*].codigo", contains("3", "3.1", "3.2")))
                .andReturn().getResponse().getContentAsString();
        Number linha = JsonPath.read(json, "$.linhas[1].valores['2021'].linhaId");
        mvc.perform(get("/api/clientes/{c}/demonstracoes/linhas/{l}/origem", cliente.getId(), linha.longValue()).with(sessao))
                .andExpect(jsonPath("$.registro").value("J150"))
                .andExpect(jsonPath("$.linha").value(51)) // |J150|2|3.1|... é a linha 51 da amostra
                .andExpect(jsonPath("$.contas").isEmpty());
    }

    @Test
    void contaSemMapeamentoAparecaNaConferenciaComADiferenca() throws Exception {
        importar("ecd-sem-i051-l9.txt");
        mvc.perform(get("/api/clientes/{c}/demonstracoes", cliente.getId()).param("tipo", "BP").with(sessao))
                .andExpect(jsonPath("$.conferencia[?(@.item == 'Ativo')].diferenca").value(-2000.00));
        assertThat(jdbc.queryForList("SELECT regra FROM inconsistencia_importacao i JOIN documento_fiscal d ON d.id = i.documento_id "
                + "WHERE d.cliente_id = ?", String.class, cliente.getId())).contains("CONTA_SEM_I051", "REFERENCIAL_DIFERE_PUBLICADA");
    }

    @Test
    void exercicioSemTabelaOficialTemAlertaESoAPublicada() throws Exception {
        importar("ecd-2024-l9.txt");
        assertThat(regras()).contains("TABELA_REFERENCIAL_AUSENTE");
        assertThat(jdbc.queryForObject("""
                SELECT mensagem FROM inconsistencia_importacao i JOIN documento_fiscal d ON d.id = i.documento_id
                WHERE d.cliente_id = ? AND regra = 'TABELA_REFERENCIAL_AUSENTE'""", String.class, cliente.getId()))
                .contains("exercício 2024", "carregadas: 2018 a 2021", "Só a demonstração publicada");
        assertThat(jdbc.queryForList("""
                SELECT d.origem FROM demonstracao d JOIN escrituracao_contabil e ON e.id = d.escrituracao_id
                WHERE e.cliente_id = ? ORDER BY d.origem""", String.class, cliente.getId())).containsOnly("PUBLICADA");
    }

    @Test
    void planoReferencialNaoSuportadoTemAlertaClaro() throws Exception {
        // COD_PLAN_REF 3 (instituições financeiras): fora do escopo; mesmo tamanho de linha, contagens intactas
        String ecd = new String(new ClassPathResource("amostras/ecd-minima-l9.txt").getContentAsByteArray(), StandardCharsets.ISO_8859_1)
                .replace("|N|N|0|0|1|", "|N|N|0|0|3|");
        importar("ecd-plano-3.txt", ecd.getBytes(StandardCharsets.ISO_8859_1));
        assertThat(regras()).contains("PLANO_REFERENCIAL_NAO_SUPORTADO");
    }

    @Test
    void outroEscritorioNaoVeDemonstracoesNemOrigem() throws Exception {
        importar("ecd-minima-l9.txt");
        Long linha = jdbc.queryForObject("""
                SELECT l.id FROM linha_demonstracao l JOIN demonstracao d ON d.id = l.demonstracao_id
                JOIN escrituracao_contabil e ON e.id = d.escrituracao_id WHERE e.cliente_id = ? LIMIT 1""", Long.class, cliente.getId());
        RequestPostProcessor intruso = dados.sessao(dados.usuario(dados.escritorio("Intruso " + UUID.randomUUID()),
                "intruso-" + UUID.randomUUID() + "@test.com", Papel.ADMIN));

        mvc.perform(get("/api/clientes/{c}/demonstracoes", cliente.getId()).with(intruso)).andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes/{c}/escrituracoes", cliente.getId()).with(intruso)).andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes/{c}/demonstracoes/linhas/{l}/origem", cliente.getId(), linha).with(intruso))
                .andExpect(status().isNotFound());
    }

    private void importar(String amostra) throws Exception {
        importar(amostra, new ClassPathResource("amostras/" + amostra).getContentAsByteArray());
    }

    private List<String> regras() {
        return jdbc.queryForList("SELECT regra FROM inconsistencia_importacao i JOIN documento_fiscal d ON d.id = i.documento_id "
                + "WHERE d.cliente_id = ?", String.class, cliente.getId());
    }

    private void importar(String amostra, byte[] conteudo) throws Exception {
        String resposta = mvc.perform(multipart("/api/clientes/{c}/documentos", cliente.getId())
                        .file(new MockMultipartFile("arquivo", amostra, "text/plain", conteudo)).with(sessao))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(resposta, "$.id")).longValue();
        aguardar(() -> documentoRepository.findById(id).orElseThrow().getStatus().finalizado());
    }
}
