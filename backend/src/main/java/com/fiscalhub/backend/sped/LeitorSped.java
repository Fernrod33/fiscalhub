package com.fiscalhub.backend.sped;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Leitura genérica de arquivos SPED (ECD, ECF, EFD-Contribuições, EFD ICMS/IPI): linha a linha, em ISO-8859-1, em
 * stream, sem carregar o arquivo. Informa o número da linha e os bytes lidos (para o progresso).
 * <p>
 * Com os registros de interesse declarados, as linhas dos demais são consumidas byte a byte sem virar String nem
 * campos (os blocos de documentos de uma EFD são a maior parte de um arquivo de gigabytes); todas as linhas, lidas ou
 * puladas, entram na contagem por registro, que é conferida com o 9900.
 */
public final class LeitorSped implements Closeable {

    /** Maior linha aceita: o CAB_DEM do J005 vai até 65.535 caracteres; acima disso o arquivo não é SPED válido. */
    public static final int LIMITE_LINHA = 128 * 1024;

    private static final DateTimeFormatter DDMMAAAA = DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT);

    private final InputStream in;
    private final byte[] buffer = new byte[64 * 1024];
    private final char[] linha = new char[LIMITE_LINHA];
    private final Set<String> interesse;
    /** Código do registro (4 caracteres num int) -> quantidade de linhas. O último fica à mão: registros vêm em sequência. */
    private final Map<Integer, long[]> contagem = new HashMap<>();
    private int ultimoCodigo = -1;
    private long[] ultimaContagem;
    private int posicao;
    private int limite;
    private long bytesLidos;
    private long numeroLinha;

    /** Todas as linhas são devolvidas. */
    public LeitorSped(InputStream in) {
        this(in, null);
    }

    /** Só as linhas dos registros de interesse (e as fora do formato |REG|, para o erro apontar a linha) são devolvidas. */
    public LeitorSped(InputStream in, Set<String> interesse) {
        this.in = in;
        this.interesse = interesse;
    }

    /**
     * Próxima linha sem o CR/LF, ou null no fim do arquivo. Linha acima do limite: {@link LinhaInvalida}. A linha de um
     * registro fora do interesse é pulada sem ser montada (e sem limite de tamanho).
     */
    public String proximaLinha() throws IOException {
        int tamanho = 0;
        boolean leuAlgo = false;
        boolean pulando = false;
        while (true) {
            if (posicao == limite) {
                limite = in.read(buffer);
                posicao = 0;
                if (limite <= 0) {
                    limite = 0;
                    if (pulando) {
                        numeroLinha++;
                        return null;
                    }
                    return leuAlgo ? fechar(tamanho) : null;
                }
            }
            byte b = buffer[posicao++];
            bytesLidos++;
            leuAlgo = true;
            if (b == '\n') {
                if (!pulando) {
                    return fechar(tamanho);
                }
                numeroLinha++;
                tamanho = 0;
                leuAlgo = false;
                pulando = false;
                continue;
            }
            if (b == '\r' || pulando) {
                continue;
            }
            if (tamanho == LIMITE_LINHA) {
                throw new LinhaInvalida(numeroLinha + 1, "Linha com mais de " + LIMITE_LINHA + " caracteres.");
            }
            linha[tamanho++] = (char) (b & 0xFF); // ISO-8859-1: cada byte é o próprio caractere
            if (tamanho == 6 && linha[0] == '|' && linha[5] == '|') {
                pulando = contarEhPular();
            }
        }
    }

    /** Conta a linha no registro dela e diz se ela fica de fora. Sem objeto novo por linha. */
    private boolean contarEhPular() {
        int codigo = linha[1] << 24 | linha[2] << 16 | linha[3] << 8 | linha[4];
        if (codigo != ultimoCodigo) {
            ultimoCodigo = codigo;
            // [quantidade, 1 = pular]
            ultimaContagem = contagem.computeIfAbsent(codigo, c -> new long[]{0,
                    interesse != null && !interesse.contains(new String(linha, 1, 4)) ? 1 : 0});
        }
        ultimaContagem[0]++;
        return ultimaContagem[1] == 1;
    }

    private String fechar(int tamanho) {
        numeroLinha++;
        return new String(linha, 0, tamanho);
    }

    /** Linhas lidas até agora por registro (inclusive as puladas). */
    public Map<String, Long> contagem() {
        Map<String, Long> porRegistro = new TreeMap<>();
        contagem.forEach((c, n) -> porRegistro.put(new String(new char[]{(char) (c >>> 24), (char) (c >>> 16 & 0xFF),
                (char) (c >>> 8 & 0xFF), (char) (c & 0xFF)}), n[0]));
        return porRegistro;
    }

    public long numeroLinha() {
        return numeroLinha;
    }

    public long bytesLidos() {
        return bytesLidos;
    }

    /** "|I155|..." -> "I155" (todo registro SPED tem 4 caracteres); linha fora do formato SPED -> null. */
    public static String registro(String linha) {
        return linha.length() >= 6 && linha.charAt(0) == '|' && linha.charAt(5) == '|' ? linha.substring(1, 5) : null;
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
