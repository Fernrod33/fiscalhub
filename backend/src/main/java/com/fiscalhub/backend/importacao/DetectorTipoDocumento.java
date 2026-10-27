package com.fiscalhub.backend.importacao;

import com.fiscalhub.backend.entity.enums.DocumentoTipo;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Tipo do documento pelo conteúdo (a extensão e o tipo informado no upload são só pistas).
 * SPED: a primeira linha |0000|...| identifica a escrituração: ECD e ECF pelo nome no campo 2 ({@link #ASSINATURAS_SPED});
 * as EFD pela estrutura do 0000 ({@link #efd}). PDF: aqui só "é PDF"; qual documento é, quem diz é o adaptador que
 * reconhece o layout (ProcessadorSituacao tenta todos). .dec da DCTF Mensal: cabeçalho DCTFM + ano.
 */
public final class DetectorTipoDocumento {

    /** Bytes lidos do início do arquivo: cabem a primeira linha SPED e as assinaturas binárias. */
    static final int BYTES_INICIO = 512;

    private record Assinatura(String prefixo, DocumentoTipo tipo) {
    }

    private static final Assinatura[] ASSINATURAS_SPED = {
            new Assinatura("|0000|LECD|", DocumentoTipo.ECD),
            new Assinatura("|0000|LECF|", DocumentoTipo.ECF),
    };

    private static final byte[] ZIP = {'P', 'K', 3, 4}; // .xlsx é um zip (OOXML)
    private static final Pattern DEC_DCTF = Pattern.compile("^DCTFM[0-9]{18}.*", Pattern.DOTALL); // ano + CNPJ

    private DetectorTipoDocumento() {
    }

    public static DocumentoTipo detectar(byte[] inicio, String extensao) {
        String texto = new String(inicio, StandardCharsets.ISO_8859_1);
        if (texto.startsWith("ï»¿")) {
            texto = texto.substring(3); // BOM UTF-8 de editores
        }
        for (Assinatura a : ASSINATURAS_SPED) {
            if (texto.startsWith(a.prefixo())) {
                return a.tipo();
            }
        }
        DocumentoTipo efd = efd(texto);
        if (efd != null) {
            return efd;
        }
        if (texto.startsWith("%PDF-")) {
            return DocumentoTipo.PDF;
        }
        if (DEC_DCTF.matcher(texto).matches()) {
            return DocumentoTipo.DCTF;
        }
        if (".xlsx".equals(extensao) && inicio.length >= 4 && Arrays.equals(inicio, 0, 4, ZIP, 0, 4)) {
            return DocumentoTipo.BALANCETE;
        }
        if (".csv".equals(extensao) && !texto.startsWith("|0000|") && !texto.startsWith("%PDF")) {
            return DocumentoTipo.BALANCETE;
        }
        return DocumentoTipo.OUTRO; // SPED ainda não reconhecido ou conteúdo desconhecido
    }

    /**
     * As duas EFD começam por |0000|COD_VER (3 dígitos)| e se distinguem pela posição das datas no 0000 (Guias Práticos):
     * ICMS/IPI |0000|COD_VER|COD_FIN|DT_INI|DT_FIN|...; Contribuições |0000|COD_VER|TIPO_ESCRIT|IND_SIT_ESP|NUM_REC_ANTERIOR|DT_INI|DT_FIN|...
     */
    private static DocumentoTipo efd(String texto) {
        int fim = texto.indexOf('\n');
        String[] f = (fim < 0 ? texto : texto.substring(0, fim)).trim().split("\\|", -1);
        if (f.length < 8 || !"0000".equals(f[1]) || !f[2].matches("\\d{3}")) {
            return null;
        }
        if (f[4].matches("\\d{8}") && f[5].matches("\\d{8}")) {
            return DocumentoTipo.EFD_ICMS_IPI;
        }
        return f[6].matches("\\d{8}") && f[7].matches("\\d{8}") ? DocumentoTipo.EFD_CONTRIBUICOES : null;
    }
}
