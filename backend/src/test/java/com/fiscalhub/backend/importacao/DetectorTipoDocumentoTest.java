package com.fiscalhub.backend.importacao;

import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DetectorTipoDocumentoTest {

    private static DocumentoTipo detectar(String inicio, String extensao) {
        return DetectorTipoDocumento.detectar(inicio.getBytes(StandardCharsets.ISO_8859_1), extensao);
    }

    @Test
    void spedPelaPrimeiraLinhaNaoPelaExtensao() {
        assertThat(detectar("|0000|LECD|01012024|31122024|EMPRESA|", ".txt")).isEqualTo(DocumentoTipo.ECD);
        assertThat(detectar("|0000|LECD|01012024|", ".csv")).isEqualTo(DocumentoTipo.ECD);
        assertThat(detectar("ï»¿|0000|LECD|01012024|", ".txt")).isEqualTo(DocumentoTipo.ECD); // BOM
        assertThat(detectar("|0000|LECF|01012024|", ".txt")).isEqualTo(DocumentoTipo.ECF);
        // EFD pela estrutura do 0000 (Guias Práticos): datas nos campos 4/5 (ICMS/IPI) ou 6/7 (Contribuições)
        assertThat(detectar("|0000|019|0|01012025|31012025|EMPRESA|11222333000181||PR|9012345678|4106902|||A|1|\r\n", ".txt"))
                .isEqualTo(DocumentoTipo.EFD_ICMS_IPI);
        assertThat(detectar("|0000|006|0|||01012025|31012025|EMPRESA|11222333000181|PR|4106902||00|2|\r\n", ".txt"))
                .isEqualTo(DocumentoTipo.EFD_CONTRIBUICOES);
        assertThat(detectar("|0000|006|0|", ".txt")).isEqualTo(DocumentoTipo.OUTRO); // SPED ainda não reconhecido
    }

    @Test
    void planilhaXlsxPeloZipECsvPeloTexto() {
        assertThat(detectar("PK\u0003\u0004resto", ".xlsx")).isEqualTo(DocumentoTipo.BALANCETE);
        assertThat(detectar("nao e zip", ".xlsx")).isEqualTo(DocumentoTipo.OUTRO);
        assertThat(detectar("Conta;Descrição;Saldo", ".csv")).isEqualTo(DocumentoTipo.BALANCETE);
    }

    @Test
    void pdfDecDaDctfEDesconhecido() {
        assertThat(detectar("%PDF-1.7", ".pdf")).isEqualTo(DocumentoTipo.PDF); // qual documento, o adaptador diz
        assertThat(detectar("%PDF-1.4", ".txt")).isEqualTo(DocumentoTipo.PDF); // pelo conteúdo, não pela extensão
        assertThat(detectar("DCTFM202411222333000181011202", ".dec")).isEqualTo(DocumentoTipo.DCTF);
        assertThat(detectar("DCTFM sem ano", ".dec")).isEqualTo(DocumentoTipo.OUTRO);
        assertThat(detectar("qualquer coisa", ".txt")).isEqualTo(DocumentoTipo.OUTRO);
    }
}
