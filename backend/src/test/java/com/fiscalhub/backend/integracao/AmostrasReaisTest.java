package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.fiscalhub.backend.integracao.Espera.aguardar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Arquivos SPED reais (anonimizados) em amostras-locais/ na raiz do repositório, fora do git: ECD, ECF,
 * EFD-Contribuições e EFD ICMS/IPI. Só roda quando a pasta existe. Cada .txt é importado pela central (cliente
 * cadastrado a partir do arquivo); confere que o tipo é reconhecido, que não há ERRO e, na ECD, que o balanço fecha e o
 * referencial bate com o publicado; nas escriturações fiscais, que há apuração gravada.
 */
@TesteIntegracao
@EnabledIf("amostrasLocaisExistem")
class AmostrasReaisTest {

    static final Path PASTA = Path.of("..", "amostras-locais");

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired DocumentoFiscalRepository documentoRepository;
    @Autowired JdbcTemplate jdbc;

    static boolean amostrasLocaisExistem() {
        return Files.isDirectory(PASTA);
    }

    @TestFactory
    Stream<DynamicTest> cadaSpedRealImportaSemErro() throws IOException {
        List<Path> arquivos;
        try (Stream<Path> s = Files.list(PASTA)) {
            arquivos = s.filter(p -> p.toString().toLowerCase().endsWith(".txt")).sorted().toList();
        }
        return arquivos.stream().map(arquivo -> DynamicTest.dynamicTest(arquivo.getFileName().toString(), () -> importar(arquivo)));
    }

    private void importar(Path arquivo) throws Exception {
        String sufixo = UUID.randomUUID().toString();
        Usuario u = dados.usuario(dados.escritorio("Real " + sufixo), "real-" + sufixo + "@test.com", Papel.CONSULTOR);
        RequestPostProcessor sessao = dados.sessao(u);
        String resposta = mvc.perform(multipart("/api/documentos").file(new MockMultipartFile("arquivo",
                        arquivo.getFileName().toString(), "text/plain", Files.readAllBytes(arquivo))).with(sessao))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(resposta, "$.id")).longValue();
        aguardarFim(id);
        if (documentoRepository.findById(id).orElseThrow().getStatus() == DocumentoStatus.AGUARDANDO_CLIENTE) {
            mvc.perform(post("/api/documentos/{d}/confirmar-cliente", id).with(sessao)).andExpect(status().isOk());
            aguardarFim(id);
        }
        DocumentoTipo tipo = documentoRepository.findById(id).orElseThrow().getTipo();
        assertThat(tipo).as("tipo de %s", arquivo.getFileName())
                .isIn(DocumentoTipo.ECD, DocumentoTipo.ECF, DocumentoTipo.EFD_CONTRIBUICOES, DocumentoTipo.EFD_ICMS_IPI);
        List<String> regras = jdbc.queryForList("SELECT severidade || ':' || regra FROM inconsistencia_importacao WHERE documento_id = ?",
                String.class, id);
        assertThat(regras).as("inconsistências de %s", arquivo.getFileName())
                .noneMatch(r -> r.startsWith("ERRO:"))
                .noneMatch(r -> r.contains("BALANCO_NAO_FECHA"))
                .noneMatch(r -> r.contains("REFERENCIAL_DIFERE_PUBLICADA"));
        if (tipo != DocumentoTipo.ECD && !regras.contains("INFO:IMUNE_OU_ISENTA")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM apuracao_tributo WHERE documento_id = ?", Long.class, id))
                    .as("apurações de %s", arquivo.getFileName()).isPositive();
        }
    }

    private void aguardarFim(long id) {
        aguardar(() -> documentoRepository.findById(id).orElseThrow().getStatus().finalizado()
                && jdbc.queryForObject("SELECT count(*) FROM job_processamento WHERE documento_id = ? AND status IN ('PENDENTE', 'PROCESSANDO')",
                Long.class, id) == 0, 10 * 60_000);
    }
}
