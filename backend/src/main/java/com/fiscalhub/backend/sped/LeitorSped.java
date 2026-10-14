package com.fiscalhub.backend.sped;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Leitura genérica de arquivos SPED (ECD agora; ECF e EFD na Fase 2): linha a linha, em ISO-8859-1, em stream, sem
 * carregar o arquivo. Informa o número da linha e os bytes lidos (para o progresso). O código do registro sai sem
 * quebrar a linha inteira, para pular sem custo os registros que não interessam (ex.: I200/I250 da ECD).
 */
public final class LeitorSped implements Closeable {

    /** Maior linha aceita: o CAB_DEM do J005 vai até 65.535 caracteres; acima disso o arquivo não é SPED válido. */
    public static final int LIMITE_LINHA = 128 * 1024;

    private static final DateTimeFormatter DDMMAAAA = DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT);

    private final InputStream in;
    private final byte[] buffer = new byte[64 * 1024];
    private final char[] linha = new char[LIMITE_LINHA];
    private int posicao;
    private int limite;
    private long bytesLidos;
    private long numeroLinha;

    public LeitorSped(InputStream in) {
        this.in = in;
    }

    /** Próxima linha sem o CR/LF, ou null no fim do arquivo. Linha acima do limite: {@link LinhaInvalida}. */
    public String proximaLinha() throws IOException {
        int tamanho = 0;
        boolean leuAlgo = false;
        while (true) {
            if (posicao == limite) {
                limite = in.read(buffer);
                posicao = 0;
                if (limite <= 0) {
                    limite = 0;
                    return leuAlgo ? fechar(tamanho) : null;
                }
            }
            byte b = buffer[posicao++];
            bytesLidos++;
            leuAlgo = true;
            if (b == '\n') {
                return fechar(tamanho);
            }
            if (b == '\r') {
                continue;
            }
            if (tamanho == LIMITE_LINHA) {
                throw new LinhaInvalida(numeroLinha + 1, "Linha com mais de " + LIMITE_LINHA + " caracteres.");
            }
            linha[tamanho++] = (char) (b & 0xFF); // ISO-8859-1: cada byte é o próprio caractere
        }
    }

    private String fechar(int tamanho) {
        numeroLinha++;
        return new String(linha, 0, tamanho);
    }

    public long numeroLinha() {
        return numeroLinha;
    }

    public long bytesLidos() {
        return bytesLidos;
    }

    /** "|I155|..." -> "I155"; linha fora do formato SPED -> null. */
    public static String registro(String linha) {
        if (linha.length() < 6 || linha.charAt(0) != '|') {
            return null;
        }
        int fim = linha.indexOf('|', 1);
        return fim > 1 ? linha.substring(1, fim) : null;
    }

    /** Campos na numeração do manual: índice 1 é o REG (campo 01). O índice 0 é o vazio antes do primeiro "|". */
    public static String[] campos(String linha) {
        return linha.split("\\|", -1);
    }

    /** Número SPED: vírgula decimal, sem separador de milhar. Vazio -> null. */
    public static BigDecimal decimal(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        if (!valor.matches("-?\\d+(,\\d+)?")) {
            throw new NumberFormatException("Número fora do formato SPED: " + valor);
        }
        return new BigDecimal(valor.replace(',', '.'));
    }

    /** Data SPED DDMMAAAA. Vazio -> null. */
    public static LocalDate data(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor, DDMMAAAA);
        } catch (DateTimeParseException e) {
            throw new NumberFormatException("Data fora do formato DDMMAAAA: " + valor);
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    /** Linha que não dá para ler (longa demais, campo fora do formato). */
    public static class LinhaInvalida extends RuntimeException {
        private final long linha;

        public LinhaInvalida(long linha, String mensagem) {
            super(mensagem);
            this.linha = linha;
        }

        public long linha() {
            return linha;
        }
    }
}
