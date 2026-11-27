package com.fiscalhub.backend.contabil.planilha;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanilhaLeituraTest {

    private static List<List<String>> ler(String amostra, String extensao) throws IOException {
        try (InputStream in = new ClassPathResource("amostras/" + amostra).getInputStream();
             FontePlanilha fonte = FontePlanilha.abrir(in, extensao)) {
            List<List<String>> linhas = new ArrayList<>();
            fonte.ler(null, (n, celulas) -> linhas.add(celulas));
            return linhas;
        }
    }

    @Test
    void xlsxEmStreamComNumerosCrus() throws IOException {
        List<List<String>> linhas = ler("balancete.xlsx", ".xlsx");
        assertThat(linhas.get(2)).containsExactly("Conta", "Descrição", "Saldo anterior", "D/C", "Débitos", "Créditos", "Saldo atual", "D/C");
        assertThat(linhas.get(5)).containsExactly("1.1.01", "Caixa", "1000", "D", "6000", "3000", "4000", "D");
        try (InputStream in = new ClassPathResource("amostras/balancete.xlsx").getInputStream();
             FontePlanilha fonte = FontePlanilha.abrir(in, ".xlsx")) {
            assertThat(fonte.abas()).containsExactly("Balancete");
        }
    }

    @Test
    void formulaValePeloValorEmCacheENuncaEhAvaliada() throws IOException {
        List<List<String>> linhas = ler("balancete-formulas.xlsx", ".xlsx");
        assertThat(linhas.get(3)).containsExactly("1.1.01", "Caixa", "1000", "6000", "3000", "4000"); // =C4+D4-E4 em cache
        assertThat(linhas.get(6)).endsWith("-1000"); // fornecedores: credor negativo
    }

    @Test
    void csvDetectaEncodingESeparador() throws IOException {
        try (InputStream in = new ClassPathResource("amostras/balancete-latin1.csv").getInputStream();
             PlanilhaCsv csv = new PlanilhaCsv(in)) {
            assertThat(csv.charset().name()).isEqualTo("windows-1252");
            assertThat(csv.separador()).isEqualTo(';');
        }
        try (InputStream in = new ClassPathResource("amostras/balancete.csv").getInputStream();
             PlanilhaCsv csv = new PlanilhaCsv(in)) {
            assertThat(csv.charset()).isEqualTo(StandardCharsets.UTF_8);
            assertThat(csv.separador()).isEqualTo(',');
        }
        assertThat(ler("balancete-latin1.csv", ".csv").get(2)).startsWith("Código", "Descrição da conta");
        assertThat(ler("balancete.csv", ".csv").get(6)).containsExactly("2.1", "Fornecedores, nacionais", "-500.00", "1000.00", "1500.00", "-1000.00");
    }

    @Test
    void numerosNoFormatoBrasileiroCruOuComIndicador() {
        assertThat(NumeroPlanilha.ler("1.234,56").numero()).isEqualByComparingTo("1234.56");
        assertThat(NumeroPlanilha.ler("1234.56").numero()).isEqualByComparingTo("1234.56");
        assertThat(NumeroPlanilha.ler("1.234.567").numero()).isEqualByComparingTo("1234567");
        assertThat(NumeroPlanilha.ler("(1.234,56)").numero()).isEqualByComparingTo("-1234.56");
        assertThat(NumeroPlanilha.ler("-15,5").numero()).isEqualByComparingTo("-15.5");
        NumeroPlanilha.Valor comIndicador = NumeroPlanilha.ler("6.000,00 C");
        assertThat(comIndicador.numero()).isEqualByComparingTo(new BigDecimal("6000"));
        assertThat(comIndicador.indicador()).isEqualTo("C");
        assertThat(NumeroPlanilha.ler("")).isNull();
        assertThatThrownBy(() -> NumeroPlanilha.ler("=SOMA(A1:A3)")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zipBombEhRecusada() throws IOException {
        // pacote .xlsx válido em que a aba descompacta 50 MB de espaços a partir de poucos KB
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (java.util.zip.ZipInputStream original = new java.util.zip.ZipInputStream(
                new ClassPathResource("amostras/balancete.xlsx").getInputStream());
             ZipOutputStream z = new ZipOutputStream(zip)) {
            for (ZipEntry e; (e = original.getNextEntry()) != null; ) {
                z.putNextEntry(new ZipEntry(e.getName()));
                if (e.getName().equals("xl/worksheets/sheet1.xml")) {
                    z.write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>".getBytes());
                    byte[] espacos = new byte[1024 * 1024];
                    java.util.Arrays.fill(espacos, (byte) ' ');
                    for (int i = 0; i < 50; i++) {
                        z.write(espacos);
                    }
                    z.write("</sheetData></worksheet>".getBytes());
                } else {
                    original.transferTo(z);
                }
            }
        }
        assertThatThrownBy(() -> {
            try (FontePlanilha fonte = FontePlanilha.abrir(new ByteArrayInputStream(zip.toByteArray()), ".xlsx")) {
                fonte.ler(null, (n, c) -> true);
            }
        }).hasStackTraceContaining("Zip bomb");
    }
}
