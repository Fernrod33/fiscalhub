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
        assertThat(detectar("|0000|006|0|", ".txt")).isEqualTo(DocumentoTipo.OUTRO); // SPED ainda não reconhecido
    }

    @Test
    void planilhaXlsxPeloZipECsvPeloTexto() {
        assertThat(detectar("PK\u0003\u0004resto", ".xlsx")).isEqualTo(DocumentoTipo.BALANCETE);
        assertThat(detectar("nao e zip", ".xlsx")).isEqualTo(DocumentoTipo.OUTRO);
        assertThat(detectar("Conta;Descrição;Saldo", ".csv")).isEqualTo(DocumentoTipo.BALANCETE);
    }

    @Test
    void pdfEDesconhecidoViramOutro() {
        assertThat(detectar("%PDF-1.7", ".pdf")).isEqualTo(DocumentoTipo.OUTRO);
        assertThat(detectar("qualquer coisa", ".txt")).isEqualTo(DocumentoTipo.OUTRO);
    }
}
