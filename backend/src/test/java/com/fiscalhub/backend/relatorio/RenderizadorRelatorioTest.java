package com.fiscalhub.backend.relatorio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.pdf.DocumentoPdf;
import com.fiscalhub.backend.pdf.LeitorPdf;
import com.fiscalhub.backend.relatorio.ModeloRelatorio.*;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Escape do que vem de arquivo importado (HTML, SVG e PDF), formatação igual à do frontend e contraste WCAG. */
class RenderizadorRelatorioTest {

    static final String SCRIPT = "<script>alert('x')</script>";

    @Test
    void textoComScriptVindoDeArquivoImportadoEhEscapadoENaoQuebraOPdf() {
        RenderizadorRelatorio r = new RenderizadorRelatorio(templates(), new IdentidadeEscritorioService(null, null));
        ModeloRelatorio m = new ModeloRelatorio(TipoRelatorio.SITUACAO_FISCAL, "Situação fiscal resumida",
                new Cabecalho("Escritório " + SCRIPT, null, "#0b4f6c", "Rodapé " + SCRIPT, null, "Empresa " + SCRIPT, "11222333000181",
                        "posição", LocalDateTime.of(2026, 10, 28, 10, 0), 7L),
                List.of(new Indicador("Débitos", "5079.00", Formato.MOEDA, SCRIPT)),
                List.of(new Secao("Pendências", SCRIPT, true, new Tabela(List.of(new Coluna("Descrição", Formato.TEXTO),
                        new Coluna("Valor", Formato.MOEDA)), List.of(Arrays.asList("Omissão " + SCRIPT, null)), null),
                        new Grafico(TipoGrafico.EMPILHADAS, SCRIPT, Formato.MOEDA, List.of("08/2026 " + SCRIPT),
                                List.of(new Serie(SCRIPT, List.of(new BigDecimal("10"))))))),
                List.of("nota " + SCRIPT), List.of(new Fonte(1, "PDF", "arquivo" + SCRIPT + ".pdf", "—", LocalDateTime.now(), "ab")),
                new Versoes("t", "n"), null);

        String html = r.html(m, null, null);
        assertThat(html).doesNotContain("<script").contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;");

        DocumentoPdf pdf = LeitorPdf.ler(r.pdf(m, 7, "f".repeat(64)));
        String texto = String.join("\n", pdf.linhas().stream().map(DocumentoPdf.Linha::texto).toList());
        assertThat(texto).contains("Empresa <script>alert('x')</script>").contains("Relatório nº 7").contains("5.079,00");
    }

    @Test
    void formatacaoIgualAoFrontend() throws Exception {
        JsonNode casos = new ObjectMapper().readTree(Path.of("..", "frontend", "src", "lib", "formatacao.casos.json").toFile());
        assertThat(casos).isNotEmpty();
        for (JsonNode c : casos) {
            String v = c.get("valor").asText();
            String obtido = switch (c.get("formato").asText()) {
                case "moeda" -> Formatos.moeda(new BigDecimal(v));
                case "percentual" -> Formatos.percentual(new BigDecimal(v));
                case "inteiro" -> Formatos.inteiro(new BigDecimal(v));
                case "competencia" -> Formatos.competencia(v);
                case "data" -> Formatos.celula(v, Formato.DATA);
                case "cnpj" -> Formatos.cnpj(v);
                default -> throw new AssertionError(c.toString());
            };
            assertThat(obtido).as(c.toString()).isEqualTo(c.get("esperado").asText());
        }
    }

    @Test
    void contrasteWcag() {
        assertThat(IdentidadeEscritorioService.contraste("#ffffff", "#000000")).isCloseTo(21, org.assertj.core.data.Offset.offset(0.01));
        assertThat(IdentidadeEscritorioService.contraste(IdentidadeEscritorioService.COR_PADRAO, "#ffffff")).isGreaterThan(4.5);
        assertThat(IdentidadeEscritorioService.contraste("#ffd84d", "#ffffff")).isLessThan(4.5);
    }

    static SpringTemplateEngine templates() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
