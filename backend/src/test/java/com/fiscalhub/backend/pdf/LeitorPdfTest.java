package com.fiscalhub.backend.pdf;

import com.fiscalhub.backend.importacao.FalhaImportacao;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeitorPdfTest {

    private static final PDType1Font HELVETICA = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    @Test
    void textoPorLinhaECelulaSemAMarcaDaguaRotacionada() throws IOException {
        DocumentoPdf pdf = LeitorPdf.ler(pdf(conteudo -> {
            texto(conteudo, 50, 700, "Receita");
            texto(conteudo, 250, 700, "Valor");
            texto(conteudo, 50, 688, "5856-01 - COFINS");
            texto(conteudo, 250, 688, "33.079,00");
            conteudo.beginText(); // marca d'água diagonal (35 graus)
            conteudo.setFont(HELVETICA, 60);
            conteudo.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(35), 150, 300));
            conteudo.showText("DOCUMENTO FICTICIO");
            conteudo.endText();
        }));
        assertThat(pdf.linhas()).hasSize(2);
        assertThat(pdf.texto()).doesNotContain("FICTICIO");
        DocumentoPdf.Linha linha = pdf.linhas().get(1);
        assertThat(linha.celulas()).extracting(DocumentoPdf.Celula::texto).containsExactly("5856-01 - COFINS", "33.079,00");
        assertThat(linha.origem()).isEqualTo(new DocumentoPdf.Origem(1, 2, "5856-01 - COFINS 33.079,00"));
    }

    @Test
    void marcaDaguaDaMassaFicaDeFora() throws IOException {
        DocumentoPdf pdf = LeitorPdf.ler(Files.readAllBytes(Path.of("src/test/resources/massa-ficticia/pgfn/CAPAG_11222333000181.pdf")));
        assertThat(pdf.linhas()).noneMatch(l -> l.texto().equals("DOCUMENTO FICTÍCIO"));
        assertThat(pdf.campo("Capacidade de pagamento estimada")).get() // rótulo quebrado em duas linhas, valor no meio
                .extracting(r -> r.get(1)).isEqualTo("400.000,00");
    }

    @Test
    void pdfSemTextoEhErroComAOrientacao() throws IOException {
        assertThatThrownBy(() -> LeitorPdf.ler(pdf(conteudo -> conteudo.addRect(10, 10, 100, 100))))
                .isInstanceOf(FalhaImportacao.class)
                .satisfies(e -> assertThat(((FalhaImportacao) e).regra()).isEqualTo("PDF_SEM_TEXTO"))
                .hasMessageContaining("scan e foto não são aceitos");
    }

    @Test
    void pdfComSenhaEhRecusado() throws IOException {
        byte[] protegido;
        try (PDDocument d = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            d.addPage(new PDPage());
            StandardProtectionPolicy politica = new StandardProtectionPolicy("dono", "usuario", new AccessPermission());
            politica.setEncryptionKeyLength(128);
            d.protect(politica);
            d.save(saida);
            protegido = saida.toByteArray();
        }
        assertThatThrownBy(() -> LeitorPdf.ler(protegido)).isInstanceOf(FalhaImportacao.class)
                .satisfies(e -> assertThat(((FalhaImportacao) e).regra()).isEqualTo("PDF_CRIPTOGRAFADO"));
    }

    @Test
    void limitesDePaginasEDeConteudoInvalido() throws IOException {
        byte[] grande;
        try (PDDocument d = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            for (int i = 0; i <= LeitorPdf.LIMITE_PAGINAS; i++) {
                d.addPage(new PDPage());
            }
            d.save(saida);
            grande = saida.toByteArray();
        }
        assertThatThrownBy(() -> LeitorPdf.ler(grande)).satisfies(e -> assertThat(((FalhaImportacao) e).regra()).isEqualTo("PDF_PAGINAS_DEMAIS"));
        assertThatThrownBy(() -> LeitorPdf.ler("isto não é PDF".getBytes()))
                .satisfies(e -> assertThat(((FalhaImportacao) e).regra()).isEqualTo("PDF_INVALIDO"));
        assertThatThrownBy(() -> LeitorPdf.ler(new byte[(int) LeitorPdf.LIMITE_BYTES + 1]))
                .satisfies(e -> assertThat(((FalhaImportacao) e).regra()).isEqualTo("PDF_GRANDE_DEMAIS"));
    }

    @Test
    void normalizadoresPtBr() {
        assertThat(LeitorPdf.valor("1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(LeitorPdf.valor("R$ 512.791,83")).isEqualByComparingTo("512791.83");
        assertThat(LeitorPdf.valor("-8,00")).isEqualByComparingTo("-8.00");
        assertThat(LeitorPdf.valor("(8,00)")).isEqualByComparingTo("-8.00");
        assertThat(LeitorPdf.valor("150.156,17 C")).isEqualByComparingTo("-150156.17");
        assertThat(LeitorPdf.valor("0,00")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(LeitorPdf.valor("-")).isNull();
        assertThatThrownBy(() -> LeitorPdf.valor("12.34")).isInstanceOf(NumberFormatException.class);
        assertThat(LeitorPdf.data("24/12/2024")).isEqualTo(LocalDate.of(2024, 12, 24));
        assertThat(LeitorPdf.data(" - ")).isNull();
        assertThat(LeitorPdf.competencia("PA 08/2026")).isEqualTo(YearMonth.of(2026, 8));
        assertThat(LeitorPdf.cnpj("CAD/ICMS 9012345663 — CNPJ 11.222.333/0001-81 — EMPRESA")).isEqualTo("11222333000181");
        assertThat(LeitorPdf.cpf("ROBERTO - CPF 529.982.247-25")).isEqualTo("52998224725");
        assertThat(LeitorPdf.espacos(" a  b \n c ")).isEqualTo("a b c");
    }

    private interface Desenho {
        void desenhar(PDPageContentStream conteudo) throws IOException;
    }

    private static byte[] pdf(Desenho desenho) throws IOException {
        try (PDDocument d = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            d.addPage(pagina);
            try (PDPageContentStream conteudo = new PDPageContentStream(d, pagina)) {
                desenho.desenhar(conteudo);
            }
            d.save(saida);
            return saida.toByteArray();
        }
    }

    private static void texto(PDPageContentStream conteudo, float x, float y, String texto) throws IOException {
        conteudo.beginText();
        conteudo.setFont(HELVETICA, 8);
        conteudo.newLineAtOffset(x, y);
        conteudo.showText(texto);
        conteudo.endText();
    }
}
