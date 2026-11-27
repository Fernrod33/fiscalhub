package com.fiscalhub.backend.contabil.planilha;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV em stream: detecta o encoding (UTF-8, com ou sem BOM; senão Windows-1252/Latin-1, comum em exportações de
 * sistemas contábeis) e o separador (; , ou tab) pelas primeiras linhas. Aspas no padrão RFC 4180.
 */
final class PlanilhaCsv implements FontePlanilha {

    private static final int AMOSTRA = 64 * 1024;
    private static final int LIMITE_LINHA = 128 * 1024;
    static final String ABA = "CSV";

    private final BufferedReader leitor;
    private final char separador;
    private final Charset charset;

    PlanilhaCsv(InputStream in) throws IOException {
        BufferedInputStream buffer = new BufferedInputStream(in, AMOSTRA);
        buffer.mark(AMOSTRA);
        byte[] amostra = buffer.readNBytes(AMOSTRA);
        buffer.reset();
        boolean bom = amostra.length >= 3 && (amostra[0] & 0xFF) == 0xEF && (amostra[1] & 0xFF) == 0xBB && (amostra[2] & 0xFF) == 0xBF;
        charset = bom || utf8Valido(amostra) ? StandardCharsets.UTF_8 : Charset.forName("windows-1252");
        if (bom) {
            buffer.skipNBytes(3);
        }
        separador = detectarSeparador(new String(amostra, charset));
        leitor = new BufferedReader(new InputStreamReader(buffer, charset));
    }

    Charset charset() {
        return charset;
    }

    char separador() {
        return separador;
    }

    @Override
    public List<String> abas() {
        return List.of(ABA);
    }

    @Override
    public void ler(String aba, Leitor consumidor) throws IOException {
        long numero = 0;
        String linha;
        while ((linha = proximoRegistro()) != null) {
            if (++numero > LIMITE_LINHAS) {
                throw new PlanilhaInvalida(numero, "A planilha passa de " + LIMITE_LINHAS + " linhas.");
            }
            List<String> celulas = dividir(linha, separador);
            if (celulas.size() > LIMITE_COLUNAS) {
                throw new PlanilhaInvalida(numero, "A planilha passa de " + LIMITE_COLUNAS + " colunas.");
            }
            if (!consumidor.linha(numero, celulas)) {
                return;
            }
        }
    }

    /** Um registro CSV (pode ocupar mais de uma linha física, dentro de aspas). */
    private String proximoRegistro() throws IOException {
        StringBuilder registro = new StringBuilder();
        boolean dentroDeAspas = false;
        int c;
        while ((c = leitor.read()) != -1) {
            if (registro.length() >= LIMITE_LINHA) {
                throw new PlanilhaInvalida(0, "Linha do CSV com mais de " + LIMITE_LINHA + " caracteres.");
            }
            if (c == '"') {
                dentroDeAspas = !dentroDeAspas;
            } else if ((c == '\n') && !dentroDeAspas) {
                return registro.toString().replaceAll("\r$", "");
            }
            registro.append((char) c);
        }
        return registro.isEmpty() ? null : registro.toString().replaceAll("\r$", "");
    }

    static List<String> dividir(String linha, char separador) {
        List<String> celulas = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean aspas = false;
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                if (aspas && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    atual.append('"'); // aspas duplicadas dentro de campo entre aspas
                    i++;
                } else {
                    aspas = !aspas;
                }
            } else if (c == separador && !aspas) {
                celulas.add(atual.toString().trim());
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }
        celulas.add(atual.toString().trim());
        return celulas;
    }

    private static boolean utf8Valido(byte[] amostra) {
        try {
            // a amostra pode cortar um caractere de vários bytes no fim: ignora o resto incompleto
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(amostra, 0, Math.max(0, amostra.length - (amostra.length == AMOSTRA ? 4 : 0))));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    /** O separador que aparece mais vezes, de forma constante, nas primeiras linhas (fora de aspas). */
    private static char detectarSeparador(String amostra) {
        String[] linhas = amostra.split("\r?\n", 20);
        char melhor = ';';
        int melhorTotal = -1;
        for (char candidato : new char[]{';', ',', '\t'}) {
            int total = 0;
            for (int i = 0; i < Math.min(linhas.length, 19); i++) {
                total += dividir(linhas[i], candidato).size() - 1;
            }
            if (total > melhorTotal) {
                melhor = candidato;
                melhorTotal = total;
            }
        }
        return melhor;
    }

    @Override
    public void close() throws IOException {
        leitor.close();
    }
}
