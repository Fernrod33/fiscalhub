package com.fiscalhub.backend.contabil.planilha;

import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * .xlsx em stream (XSSFReader + SAX): as abas não são carregadas inteiras. Fórmulas valem pelo valor em cache
 * (nunca avaliadas). Proteções: zip bomb (razão mínima de compressão e tamanho máximo por entrada), XML sem entidades
 * externas (XMLHelper do POI) e limites de linhas/colunas.
 */
final class PlanilhaXlsx implements FontePlanilha {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PlanilhaXlsx.class);

    static {
        ZipSecureFile.setMinInflateRatio(0.01);              // descompactado no máximo 100x o compactado
        ZipSecureFile.setMaxEntrySize(200L * 1024 * 1024);   // nenhuma parte do .xlsx acima de 200 MB
    }

    private final Path temporario;
    private final OPCPackage pacote;

    PlanilhaXlsx(InputStream in) throws IOException {
        // o OPC precisa de acesso aleatório ao zip: arquivo temporário em vez de carregar tudo na memória
        temporario = Files.createTempFile("fiscalhub-planilha-", ".xlsx");
        try (in) {
            Files.copy(in, temporario, StandardCopyOption.REPLACE_EXISTING);
            pacote = OPCPackage.open(temporario.toFile(), PackageAccess.READ);
        } catch (OpenXML4JException | RuntimeException e) {
            Files.deleteIfExists(temporario);
            log.warn("Falha ao abrir .xlsx", e); // detalhe (inclui caminho temporário) só no log
            throw new PlanilhaInvalida(0, "Não foi possível abrir o .xlsx: arquivo corrompido ou não é uma planilha do Excel.");
        }
    }

    @Override
    public List<String> abas() throws IOException {
        List<String> nomes = new ArrayList<>();
        try {
            XSSFReader.SheetIterator it = (XSSFReader.SheetIterator) new XSSFReader(pacote).getSheetsData();
            while (it.hasNext()) {
                it.next().close(); // só o nome: o conteúdo da aba não é lido
                nomes.add(it.getSheetName());
            }
        } catch (OpenXML4JException e) {
            log.warn("Abas ilegíveis no .xlsx", e);
            throw new PlanilhaInvalida(0, "Não foi possível ler as abas da planilha.");
        }
        return nomes;
    }

    @Override
    public void ler(String aba, Leitor leitor) throws IOException {
        try {
            XSSFReader reader = new XSSFReader(pacote);
            ReadOnlySharedStringsTable textos = new ReadOnlySharedStringsTable(pacote);
            StylesTable estilos = reader.getStylesTable();
            XSSFReader.SheetIterator it = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (it.hasNext()) {
                try (InputStream folha = it.next()) {
                    if (aba == null || aba.equals(it.getSheetName())) {
                        lerFolha(folha, textos, estilos, leitor);
                        return;
                    }
                }
            }
            throw new PlanilhaInvalida(0, "A aba " + aba + " não existe na planilha.");
        } catch (OpenXML4JException | SAXException | javax.xml.parsers.ParserConfigurationException e) {
            log.warn("Aba ilegível no .xlsx", e);
            throw new PlanilhaInvalida(0, "Não foi possível ler a aba da planilha (conteúdo inválido).");
        }
    }

    private static void lerFolha(InputStream folha, ReadOnlySharedStringsTable textos, StylesTable estilos, Leitor leitor)
            throws IOException, SAXException, javax.xml.parsers.ParserConfigurationException {
        XMLReader xml = XMLHelper.newXMLReader(); // sem DTD nem entidades externas (XXE)
        Coletor coletor = new Coletor(leitor);
        // formulasNotResults=false: o handler entrega o resultado em cache, não a fórmula
        xml.setContentHandler(new XSSFSheetXMLHandler(estilos, null, textos, coletor, new FormatoBruto(), false));
        try {
            xml.parse(new InputSource(folha));
        } catch (Parar fim) {
            // o leitor pediu para parar (ex.: prévia das primeiras linhas)
        }
    }

    @Override
    public void close() throws IOException {
        pacote.revert(); // somente leitura: fecha sem gravar
        Files.deleteIfExists(temporario);
    }

    /** Número sai cru ("1234.56"), sem a máscara da célula; datas e textos seguem o formato normal. */
    private static final class FormatoBruto extends DataFormatter {
        @Override
        public String formatRawCellContents(double valor, int indiceFormato, String formato) {
            if (org.apache.poi.ss.usermodel.DateUtil.isADateFormat(indiceFormato, formato)) {
                return super.formatRawCellContents(valor, indiceFormato, formato);
            }
            return BigDecimal.valueOf(valor).stripTrailingZeros().toPlainString();
        }
    }

    private static final class Parar extends RuntimeException {
        Parar() {
            super(null, null, false, false);
        }
    }

    /** Monta cada linha com as células na posição da coluna (lacunas viram ""). */
    private static final class Coletor implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final Leitor leitor;
        private final TreeMap<Integer, String> celulas = new TreeMap<>();
        private int linhasLidas;

        Coletor(Leitor leitor) {
            this.leitor = leitor;
        }

        @Override
        public void startRow(int linha) {
            celulas.clear();
            if (++linhasLidas > LIMITE_LINHAS) {
                throw new PlanilhaInvalida(linha + 1, "A planilha passa de " + LIMITE_LINHAS + " linhas.");
            }
        }

        @Override
        public void cell(String referencia, String valor, XSSFComment comentario) {
            int coluna = new CellReference(referencia).getCol();
            if (coluna >= LIMITE_COLUNAS) {
                throw new PlanilhaInvalida(new CellReference(referencia).getRow() + 1,
                        "A planilha passa de " + LIMITE_COLUNAS + " colunas.");
            }
            celulas.put(coluna, valor == null ? "" : valor);
        }

        @Override
        public void endRow(int linha) {
            List<String> valores = new ArrayList<>();
            int ultima = celulas.isEmpty() ? -1 : celulas.lastKey();
            for (int c = 0; c <= ultima; c++) {
                valores.add(celulas.getOrDefault(c, ""));
            }
            if (!leitor.linha(linha + 1L, valores)) {
                throw new Parar();
            }
        }
    }
}
