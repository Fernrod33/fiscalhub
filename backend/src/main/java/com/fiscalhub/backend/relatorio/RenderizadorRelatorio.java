package com.fiscalhub.backend.relatorio;

import com.fiscalhub.backend.relatorio.ModeloRelatorio.Formato;
import com.fiscalhub.backend.relatorio.ModeloRelatorio.Grafico;
import com.fiscalhub.backend.relatorio.ModeloRelatorio.Secao;
import com.fiscalhub.backend.relatorio.ModeloRelatorio.Tabela;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Do ModeloRelatorio para HTML (prévia), PDF e Excel. Um só template (templates/relatorios/relatorio.html) para a
 * prévia e o PDF; o Excel sai das mesmas seções. Nada é calculado aqui: só formatação.
 * PDF: A4, fontes embutidas (Inter e Source Serif 4, as do design system), nenhum recurso externo (só data: URIs),
 * SVG sem script.
 */
@Component
public class RenderizadorRelatorio {

    private static final String FONTES = "relatorios/fontes/";

    private final ITemplateEngine templates;
    private final IdentidadeEscritorioService identidade;
    private final String fontesPrevia;

    public RenderizadorRelatorio(ITemplateEngine templates, IdentidadeEscritorioService identidade) {
        this.templates = templates;
        this.identidade = identidade;
        this.fontesPrevia = fontFace("Inter", "inter-latin-wght-normal.woff2") + fontFace("Source Serif 4", "source-serif-4-latin-wght-normal.woff2");
    }

    /** numero null = prévia (marcada como tal, sem número nem hash). */
    public String html(ModeloRelatorio m, Long numero, String hash) {
        Context c = new Context(Locale.of("pt", "BR"));
        c.setVariable("m", m);
        c.setVariable("previa", numero == null);
        c.setVariable("numero", numero == null ? "Prévia" : "Relatório nº " + numero);
        c.setVariable("hash", hash == null ? "—" : hash);
        c.setVariable("logo", identidade.dataUri(m.cabecalho().logoChave()));
        c.setVariable("fontesPrevia", fontesPrevia);
        c.setVariable("f", new Ajudante());
        return templates.process("relatorios/relatorio", c);
    }

    public byte[] pdf(ModeloRelatorio m, long numero, String hash) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (BatikSVGDrawer svg = new BatikSVGDrawer(BatikSVGDrawer.SvgScriptMode.SECURE, BatikSVGDrawer.SvgExternalResourceMode.SECURE)) {
            // ponytail: o texto dos gráficos SVG no PDF sai na sans-serif padrão do Batik (o resolvedor de fontes dele só
            // existe durante o render); registrar a Inter por @font-face com data: URI se a diferença incomodar
            PdfRendererBuilder b = new PdfRendererBuilder();
            b.useFont(() -> fonte("Inter-Regular.ttf"), "Inter", 400, FontStyle.NORMAL, true);
            b.useFont(() -> fonte("Inter-SemiBold.ttf"), "Inter", 600, FontStyle.NORMAL, true);
            b.useFont(() -> fonte("SourceSerif4-Semibold.ttf"), "Source Serif 4", 600, FontStyle.NORMAL, true);
            // nenhum acesso externo: só data: URIs (logotipo), antes e depois de resolver a URI
            b.useExternalResourceAccessControl((uri, tipo) -> uri != null && uri.startsWith("data:"), ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
            b.useExternalResourceAccessControl((uri, tipo) -> uri != null && uri.startsWith("data:"), ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
            b.useSVGDrawer(svg);
            b.useFastMode();
            b.withHtmlContent(html(m, numero, hash), null);
            b.toStream(saida);
            b.run();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar o PDF do relatório", e);
        }
        return saida.toByteArray();
    }

    /** Uma aba por seção com tabela, mais Resumo, Notas e Fontes; números como número, cabeçalho congelado. */
    public byte[] xlsx(ModeloRelatorio m, long numero, String hash) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            Map<Formato, CellStyle> estilos = estilos(wb);
            CellStyle negrito = wb.createCellStyle();
            Font fonteNegrito = wb.createFont();
            fonteNegrito.setBold(true);
            negrito.setFont(fonteNegrito);
            Set<String> nomes = new HashSet<>();

            SXSSFSheet resumo = wb.createSheet(nome("Resumo", nomes));
            int r = 0;
            for (String[] l : List.of(new String[]{m.titulo(), ""}, new String[]{"Relatório nº", String.valueOf(numero)},
                    new String[]{"Cliente", m.cabecalho().clienteRazaoSocial()}, new String[]{"CNPJ", Formatos.cnpj(m.cabecalho().clienteCnpj())},
                    new String[]{"Período", m.cabecalho().periodo()}, new String[]{"Emissão", Formatos.dataHora(m.cabecalho().emissao())},
                    new String[]{"Escritório", m.cabecalho().escritorio()}, new String[]{"SHA-256 do snapshot", hash})) {
                Row row = resumo.createRow(r++);
                row.createCell(0).setCellValue(l[0]);
                row.createCell(1).setCellValue(l[1]);
            }
            r++;
            Row cab = resumo.createRow(r++);
            celula(cab, 0, "Indicador", negrito);
            celula(cab, 1, "Valor", negrito);
            for (var i : m.sumario()) {
                Row row = resumo.createRow(r++);
                row.createCell(0).setCellValue(i.rotulo());
                valor(row.createCell(1), i.valor(), i.formato(), estilos);
            }
            resumo.setColumnWidth(0, 32 * 256);
            resumo.setColumnWidth(1, 70 * 256);

            for (Secao s : m.secoes()) {
                if (s.tabela() != null) {
                    tabela(wb.createSheet(nome(s.titulo(), nomes)), s.tabela(), estilos, negrito);
                }
            }
            SXSSFSheet notas = wb.createSheet(nome("Notas", nomes));
            int n = 0;
            for (String nota : m.notas()) {
                notas.createRow(n++).createCell(0).setCellValue(nota);
            }
            notas.createRow(n + 1).createCell(0).setCellValue("Versão do template: " + m.versoes().template() + " · " + m.versoes().normas());
            notas.setColumnWidth(0, 160 * 256);

            List<List<String>> fontes = m.fontes().stream().map(f -> Arrays.asList(f.tipo(), f.arquivo(), f.periodo(),
                    Formatos.dataHora(f.importadoEm()), f.sha256())).toList();
            tabela(wb.createSheet(nome("Fontes", nomes)), new Tabela(List.of(new ModeloRelatorio.Coluna("Tipo", Formato.TEXTO),
                    new ModeloRelatorio.Coluna("Arquivo", Formato.TEXTO), new ModeloRelatorio.Coluna("Período", Formato.TEXTO),
                    new ModeloRelatorio.Coluna("Importado em", Formato.TEXTO), new ModeloRelatorio.Coluna("SHA-256", Formato.TEXTO)), fontes, null),
                    estilos, negrito);

            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            wb.write(saida);
            wb.dispose();
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void tabela(SXSSFSheet sheet, Tabela t, Map<Formato, CellStyle> estilos, CellStyle negrito) {
        Row cab = sheet.createRow(0);
        for (int c = 0; c < t.colunas().size(); c++) {
            celula(cab, c, t.colunas().get(c).nome(), negrito);
            sheet.setColumnWidth(c, (Formatos.numerico(t.colunas().get(c).formato()) ? 18 : 28) * 256);
        }
        sheet.createFreezePane(0, 1);
        int r = 1;
        List<List<String>> linhas = new ArrayList<>(t.linhas());
        if (t.totais() != null) {
            linhas.add(t.totais());
        }
        for (List<String> l : linhas) {
            Row row = sheet.createRow(r++);
            for (int c = 0; c < t.colunas().size(); c++) {
                valor(row.createCell(c), c < l.size() ? l.get(c) : null, t.colunas().get(c).formato(), estilos);
            }
        }
    }

    /** Número como número (com o formato de moeda, percentual ou inteiro); o resto como texto já formatado. */
    private static void valor(Cell cell, String cru, Formato f, Map<Formato, CellStyle> estilos) {
        BigDecimal n = Formatos.numero(cru);
        if (n != null && Formatos.numerico(f)) {
            cell.setCellValue(f == Formato.PERCENTUAL ? n.movePointLeft(2).doubleValue() : n.doubleValue());
            cell.setCellStyle(estilos.get(f));
        } else if (cru != null) {
            cell.setCellValue(Formatos.celula(cru, f));
        }
    }

    private static Map<Formato, CellStyle> estilos(Workbook wb) {
        DataFormat df = wb.createDataFormat();
        Map<Formato, CellStyle> m = new EnumMap<>(Formato.class);
        for (var e : Map.of(Formato.MOEDA, "\"R$\" #,##0.00;-\"R$\" #,##0.00", Formato.PERCENTUAL, "0.00%", Formato.INTEIRO, "#,##0").entrySet()) {
            CellStyle s = wb.createCellStyle();
            s.setDataFormat(df.getFormat(e.getValue()));
            m.put(e.getKey(), s);
        }
        return m;
    }

    private static void celula(Row row, int c, String v, CellStyle s) {
        Cell cell = row.createCell(c);
        cell.setCellValue(v);
        cell.setCellStyle(s);
    }

    /** Nome de aba do Excel: até 31 caracteres, sem []:*?/\ e único. */
    private static String nome(String titulo, Set<String> usados) {
        String base = titulo.replaceAll("[\\[\\]:*?/\\\\]", " ").strip();
        base = base.length() > 28 ? base.substring(0, 28) : base;
        String n = base;
        for (int i = 2; !usados.add(n.toLowerCase()); i++) {
            n = base + " " + i;
        }
        return n;
    }

    private static java.io.InputStream fonte(String arquivo) {
        try {
            return new ClassPathResource(FONTES + arquivo).getInputStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String fontFace(String familia, String arquivo) {
        try {
            return "@font-face{font-family:'" + familia + "';font-weight:100 900;src:url(data:font/woff2;base64,"
                    + Base64.getEncoder().encodeToString(new ClassPathResource(FONTES + arquivo).getContentAsByteArray()) + ") format('woff2');}";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Funções que o template chama (a regra de formatação é a do Formatos). */
    public static final class Ajudante {
        public String celula(String cru, Formato f) {
            return Formatos.celula(cru, f);
        }

        public boolean numerico(Formato f) {
            return Formatos.numerico(f);
        }

        public String cnpj(String v) {
            return Formatos.cnpj(v);
        }

        public String dataHora(LocalDateTime d) {
            return Formatos.dataHora(d);
        }

        public String svg(Grafico g) {
            return GraficoSvg.svg(g);
        }
    }
}
