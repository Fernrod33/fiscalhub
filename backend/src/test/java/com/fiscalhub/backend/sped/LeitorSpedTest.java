package com.fiscalhub.backend.sped;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeitorSpedTest {

    private static LeitorSped leitor(String texto) {
        return new LeitorSped(new ByteArrayInputStream(texto.getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    void leLinhasEmIso88591ComCrLfContandoLinhasEBytes() throws IOException {
        LeitorSped l = leitor("|0000|LECD|AÇÃO|\r\n|I010|G|9.00|\n|9999|3|");
        assertThat(l.proximaLinha()).isEqualTo("|0000|LECD|AÇÃO|");
        assertThat(l.numeroLinha()).isEqualTo(1);
        assertThat(l.bytesLidos()).isEqualTo(18); // 16 caracteres + CR + LF, 1 byte cada em ISO-8859-1
        assertThat(l.proximaLinha()).isEqualTo("|I010|G|9.00|");
        assertThat(l.proximaLinha()).isEqualTo("|9999|3|"); // última linha sem quebra
        assertThat(l.proximaLinha()).isNull();
        assertThat(l.numeroLinha()).isEqualTo(3);
    }

    @Test
    void registrosForaDoInteressePuladosMasContados() throws IOException {
        String longa = "|C170|" + "x".repeat(LeitorSped.LIMITE_LINHA * 2) + "|"; // pulada: nem o limite de linha vale
        String texto = "|0000|006|\r\n|C100|1|\r\n" + longa + "\r\n|E110|10,00|\r\n\r\nlixo\n|9999|7|";
        LeitorSped l = new LeitorSped(new ByteArrayInputStream(texto.getBytes(StandardCharsets.ISO_8859_1)),
                java.util.Set.of("0000", "E110", "9999"));
        assertThat(l.proximaLinha()).isEqualTo("|0000|006|");
        assertThat(l.proximaLinha()).isEqualTo("|E110|10,00|");
        assertThat(l.numeroLinha()).isEqualTo(4);
        assertThat(l.proximaLinha()).isEmpty(); // linha em branco e fora do formato voltam para quem lê decidir
        assertThat(l.proximaLinha()).isEqualTo("lixo");
        assertThat(l.proximaLinha()).isEqualTo("|9999|7|");
        assertThat(l.numeroLinha()).isEqualTo(7);
        assertThat(l.contagem()).containsEntry("C100", 1L).containsEntry("C170", 1L).containsEntry("E110", 1L).containsEntry("9999", 1L);
    }

    @Test
    void registroSaiSemQuebrarALinhaECamposSeguemANumeracaoDoManual() {
        assertThat(LeitorSped.registro("|I250|1.1|...|")).isEqualTo("I250");
        assertThat(LeitorSped.registro("lixo")).isNull();
        String[] campos = LeitorSped.campos("|I155|1.1.01||1000,00|D|");
        assertThat(campos[1]).isEqualTo("I155");
        assertThat(campos[2]).isEqualTo("1.1.01");
        assertThat(campos[3]).isEmpty();
        assertThat(campos[4]).isEqualTo("1000,00");
    }

    @Test
    void numerosComVirgulaEDatasDdMmAaaa() {
        assertThat(LeitorSped.decimal("1234,56")).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(LeitorSped.decimal("0")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(LeitorSped.decimal("")).isNull();
        assertThatThrownBy(() -> LeitorSped.decimal("1.234,56")).isInstanceOf(NumberFormatException.class);
        assertThat(LeitorSped.data("31122024")).isEqualTo(LocalDate.of(2024, 12, 31));
        assertThatThrownBy(() -> LeitorSped.data("31022024")).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void linhaAcimaDoLimiteEhRecusada() {
        LeitorSped l = leitor("|J005|" + "x".repeat(LeitorSped.LIMITE_LINHA) + "|\n");
        assertThatThrownBy(l::proximaLinha).isInstanceOf(LeitorSped.LinhaInvalida.class);
    }

    @Test
    void saldoComSinalDevedorPositivoCredorNegativo() {
        assertThat(Saldos.comSinal(new BigDecimal("10"), "D")).isEqualByComparingTo("10");
        assertThat(Saldos.comSinal(new BigDecimal("10"), "C")).isEqualByComparingTo("-10");
        assertThat(Saldos.comSinal(null, "C")).isEqualByComparingTo("0");
    }
}
