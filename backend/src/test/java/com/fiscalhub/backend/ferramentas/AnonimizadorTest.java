package com.fiscalhub.backend.ferramentas;

import com.fiscalhub.backend.pdf.LeitorPdf;
import com.fiscalhub.backend.util.CnpjValidationUtils;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AnonimizadorTest {

    private static byte[] massa(String arquivo) throws Exception {
        return new ClassPathResource("massa-ficticia/" + arquivo).getContentAsByteArray();
    }

    @Test
    void digitosVerificadoresFicticiosValidos() {
        assertThat(CnpjValidationUtils.isValid(Anonimizador.comDv("112223330001"))).isTrue();
        assertThat(Anonimizador.comDv("112223330001")).isEqualTo("11222333000181");
        assertThat(CnpjValidationUtils.isValidCpf(Anonimizador.comDv("123456789"))).isTrue();
    }

    @Test
    void spedTrocaEmpresaECnpjSemMudarEstruturaNemContas() throws Exception {
        Anonimizador a = new Anonimizador(42, null);
        byte[] original = massa("ecd/ECD_2025_11222333000181_ORIGINAL.txt");
        String antes = new String(original, StandardCharsets.ISO_8859_1);
        String depois = new String(a.sped(original), StandardCharsets.ISO_8859_1);

        assertThat(depois).doesNotContain("11222333000181", "METALURGICA PINHEIRAL");
        String[] la = antes.split("\r\n", -1);
        String[] ld = depois.split("\r\n", -1);
        assertThat(ld).hasSameSizeAs(la);
        for (int i = 0; i < la.length; i++) {
            assertThat(ld[i].split("\\|", -1)).as("campos da linha %d", i + 1).hasSameSizeAs(la[i].split("\\|", -1));
            if (la[i].startsWith("|I050|") || la[i].startsWith("|I155|") || la[i].startsWith("|9900|")) {
                assertThat(ld[i]).isEqualTo(la[i]); // contas, saldos e contagens intactos
            }
        }
        String cnpj = ld[0].split("\\|")[6];
        assertThat(CnpjValidationUtils.isValid(cnpj)).isTrue();
        assertThat(depois.split(cnpj, -1).length - 1).isEqualTo(antes.split("11222333000181", -1).length - 1); // consistente
    }

    @Test
    void pdfReescritoComOsMesmosNomesDoSpedEOReconhecimentoDoLayoutMantido() throws Exception {
        Anonimizador a = new Anonimizador(7, null);
        a.sped(massa("ecd/ECD_2025_11222333000181_ORIGINAL.txt")); // aprende o nome e o CNPJ da empresa
        byte[] pdf = a.pdf(massa("situacao-fiscal/Relatorio_Situacao_Fiscal_11222333000181_20261014.pdf"));

        String antes = LeitorPdf.ler(massa("situacao-fiscal/Relatorio_Situacao_Fiscal_11222333000181_20261014.pdf")).texto();
        String depois = LeitorPdf.ler(pdf).texto();
        assertThat(antes).contains("11.222.333/0001-81");
        assertThat(depois).doesNotContain("11.222.333/0001-81", "METALURGICA PINHEIRAL")
                .contains("DOCUMENTO FICTÍCIO PARA TESTE DE SOFTWARE"); // a faixa que o adaptador reconhece continua
        Matcher m = Pattern.compile("\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}").matcher(depois);
        assertThat(m.find()).isTrue();
        assertThat(CnpjValidationUtils.isValid(m.group().replaceAll("\\D", ""))).isTrue();
        assertThat(a.contagem).doesNotContainKey("pdf-sem-glifo");
    }

    @Test
    void escalaMultiplicaOsValoresEmReais() {
        Anonimizador a = new Anonimizador(1, new BigDecimal("0.5"));
        assertThat(a.texto("Total R$ 1.234,56 e 10,00")).isEqualTo("Total R$ 617,28 e 5,00");
    }
}
