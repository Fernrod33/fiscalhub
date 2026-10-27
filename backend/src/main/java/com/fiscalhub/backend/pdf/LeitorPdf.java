package com.fiscalhub.backend.pdf;

import com.fiscalhub.backend.importacao.FalhaImportacao;
import com.fiscalhub.backend.pdf.DocumentoPdf.Celula;
import com.fiscalhub.backend.pdf.DocumentoPdf.Linha;
import com.fiscalhub.backend.pdf.DocumentoPdf.Pagina;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitura genérica de PDF (camada 1): texto por página e por linha, cada linha dividida em células pela posição
 * horizontal (colunas de tabela). Texto rotacionado é descartado (marca d'água diagonal). Os adaptadores de cada tipo
 * de documento (camada 2, {@link AdaptadorDocumentoPdf}) trabalham sobre o resultado. Sem OCR: PDF sem texto é erro.
 * <p>
 * PDF não confiável: tamanho e páginas limitados antes de extrair, PDF com senha recusado, cache de streams em arquivo
 * temporário (não em memória). A extração de texto não executa JavaScript, não abre anexos nem busca recursos externos.
 */
public final class LeitorPdf {

    public static final long LIMITE_BYTES = 20L * 1024 * 1024;
    public static final int LIMITE_PAGINAS = 300;
    static final String ORIENTACAO_SEM_TEXTO = "Gere o PDF pelo próprio sistema (e-CAC, Regularize, Receita PR...); "
            + "scan e foto não são aceitos.";

    private static final Pattern VALOR = Pattern.compile("^(-|\\()?\\s*(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*|\\d+),(\\d{2})\\)?\\s*([DC])?$");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private LeitorPdf() {
    }

    public static DocumentoPdf ler(byte[] pdf) {
        if (pdf.length > LIMITE_BYTES) {
            throw new FalhaImportacao("PDF_GRANDE_DEMAIS", "O PDF tem " + pdf.length / 1024 / 1024 + " MB; o limite é "
                    + LIMITE_BYTES / 1024 / 1024 + " MB. Gere o relatório de um período menor.");
        }
        try (PDDocument documento = Loader.loadPDF(new RandomAccessReadBuffer(pdf), "", null, null,
                IOUtils.createTempFileOnlyStreamCache())) {
            if (documento.getNumberOfPages() > LIMITE_PAGINAS) {
                throw new FalhaImportacao("PDF_PAGINAS_DEMAIS", "O PDF tem " + documento.getNumberOfPages()
                        + " páginas; o limite é " + LIMITE_PAGINAS + ". Gere o relatório de um período menor.");
            }
            Coletor coletor = new Coletor();
            coletor.getText(documento);
            if (coletor.paginas.stream().allMatch(p -> p.linhas().isEmpty())) {
                throw new FalhaImportacao("PDF_SEM_TEXTO", "O PDF não tem texto extraível (é imagem). " + ORIENTACAO_SEM_TEXTO);
            }
            return new DocumentoPdf(List.copyOf(coletor.paginas));
        } catch (InvalidPasswordException e) {
            throw new FalhaImportacao("PDF_CRIPTOGRAFADO", "O PDF está protegido por senha. Gere-o de novo no sistema de "
                    + "origem sem senha ou remova a proteção antes de importar.");
        } catch (IOException e) {
            throw new FalhaImportacao("PDF_INVALIDO", "O arquivo não é um PDF válido ou está corrompido.");
        }
    }

    /** Recolhe os caracteres de cada página (sem os rotacionados) e monta linhas e células no fim da página. */
    private static final class Coletor extends PDFTextStripper {

        final List<Pagina> paginas = new ArrayList<>();
        private final List<TextPosition> caracteres = new ArrayList<>();

        Coletor() throws IOException {
            setSortByPosition(true);
        }

        @Override
        protected void processTextPosition(TextPosition t) {
            Matrix m = t.getTextMatrix();
            if (Math.abs(m.getShearX()) < 0.01 && Math.abs(m.getShearY()) < 0.01 && m.getScaleX() > 0) {
                caracteres.add(t); // texto rotacionado (marca d'água diagonal) fica de fora
            }
        }

        @Override
        protected void endPage(PDPage page) {
            paginas.add(new Pagina(paginas.size() + 1, linhas(paginas.size() + 1, caracteres)));
            caracteres.clear();
        }
    }

    /** Agrupa por linha de base (y) e, dentro da linha, em células separadas por espaço largo (coluna de tabela). */
    static List<Linha> linhas(int pagina, List<TextPosition> caracteres) {
        List<TextPosition> ordenados = new ArrayList<>(caracteres);
        ordenados.sort(Comparator.comparingDouble(TextPosition::getYDirAdj).thenComparingDouble(TextPosition::getXDirAdj));
        List<List<TextPosition>> grupos = new ArrayList<>();
        float yAtual = Float.NaN;
        for (TextPosition t : ordenados) {
            float tolerancia = Math.max(1.5f, t.getHeightDir() * 0.5f);
            if (grupos.isEmpty() || Math.abs(t.getYDirAdj() - yAtual) > tolerancia) {
                grupos.add(new ArrayList<>());
                yAtual = t.getYDirAdj();
            }
            grupos.getLast().add(t);
        }
        List<Linha> linhas = new ArrayList<>();
        for (List<TextPosition> grupo : grupos) {
            grupo.sort(Comparator.comparingDouble(TextPosition::getXDirAdj));
            List<Celula> celulas = new ArrayList<>();
            float fonteLinha = 0;
            StringBuilder texto = new StringBuilder();
            float xCelula = 0;
            float fimAnterior = Float.NaN;
            for (TextPosition t : grupo) {
                float fonte = Math.max(t.getFontSizeInPt(), 1);
                fonteLinha = Math.max(fonteLinha, fonte);
                float vao = t.getXDirAdj() - fimAnterior;
                if (Float.isNaN(fimAnterior) || vao > Math.max(3f, fonte * 0.6f)) {
                    fecharCelula(celulas, texto, xCelula);
                    xCelula = t.getXDirAdj();
                } else if (vao > fonte * 0.15f && !texto.isEmpty() && texto.charAt(texto.length() - 1) != ' ') {
                    texto.append(' '); // palavra posicionada sem o caractere de espaço
                }
                texto.append(t.getUnicode());
                fimAnterior = t.getXDirAdj() + t.getWidthDirAdj();
            }
            fecharCelula(celulas, texto, xCelula);
            if (!celulas.isEmpty()) {
                linhas.add(new Linha(pagina, linhas.size() + 1, grupo.getFirst().getYDirAdj(), fonteLinha, List.copyOf(celulas)));
            }
        }
        return linhas;
    }

    private static void fecharCelula(List<Celula> celulas, StringBuilder texto, float x) {
        String normalizado = espacos(texto.toString());
        if (!normalizado.isEmpty()) {
            celulas.add(new Celula(x, normalizado));
        }
        texto.setLength(0);
    }

    // ---- normalizadores pt-BR (usados pelos adaptadores) ----

    public static String espacos(String texto) {
        return texto == null ? "" : texto.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    /** "1.234,56", "R$ 1.234,56", "-1.234,56", "(1.234,56)" ou "1.234,56 D/C" (C = negativo). Vazio ou "-": null. */
    public static BigDecimal valor(String texto) {
        String t = espacos(texto);
        if (t.isEmpty() || "-".equals(t)) {
            return null;
        }
        Matcher m = VALOR.matcher(t);
        if (!m.matches()) {
            throw new NumberFormatException("Valor fora do formato 1.234,56: " + t);
        }
        BigDecimal v = new BigDecimal(m.group(2).replace(".", "") + "." + m.group(3));
        return m.group(1) != null || "C".equals(m.group(4)) ? v.negate() : v;
    }

    /** dd/mm/aaaa; vazio ou "-": null. */
    public static LocalDate data(String texto) {
        String t = espacos(texto);
        if (t.isEmpty() || "-".equals(t)) {
            return null;
        }
        try {
            return LocalDate.parse(t, DATA);
        } catch (DateTimeParseException e) {
            throw new NumberFormatException("Data fora do formato dd/mm/aaaa: " + t);
        }
    }

    /** "08/2026" -> 2026-08 (competência ou período de apuração mensal). */
    public static YearMonth competencia(String texto) {
        Matcher m = Pattern.compile("(\\d{2})/(\\d{4})").matcher(espacos(texto));
        if (!m.find()) {
            throw new NumberFormatException("Competência fora do formato mm/aaaa: " + texto);
        }
        return YearMonth.of(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
    }

    /** Primeiro CNPJ do texto (com ou sem máscara), só os 14 dígitos; null se não houver. */
    public static String cnpj(String texto) {
        Matcher m = Pattern.compile("\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}").matcher(espacos(texto));
        return m.find() ? m.group().replaceAll("\\D", "") : null;
    }

    /** Primeiro CPF do texto (com ou sem máscara), só os 11 dígitos; null se não houver. */
    public static String cpf(String texto) {
        Matcher m = Pattern.compile("(?<![\\d/.])\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}(?![\\d/])").matcher(espacos(texto));
        return m.find() ? m.group().replaceAll("\\D", "") : null;
    }
}
