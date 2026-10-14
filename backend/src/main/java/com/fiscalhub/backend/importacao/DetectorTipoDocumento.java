package com.fiscalhub.backend.importacao;

import com.fiscalhub.backend.entity.enums.DocumentoTipo;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Tipo do documento pelo conteúdo (a extensão e o tipo informado no upload são só pistas).
 * SPED: a primeira linha |0000|...| identifica a escrituração. Para reconhecer um novo SPED (EFD-Contribuições,
 * EFD ICMS/IPI na Fase 2), acrescente a assinatura em {@link #ASSINATURAS_SPED}.
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
        if (".xlsx".equals(extensao) && inicio.length >= 4 && Arrays.equals(inicio, 0, 4, ZIP, 0, 4)) {
            return DocumentoTipo.BALANCETE;
        }
        if (".csv".equals(extensao) && !texto.startsWith("|0000|") && !texto.startsWith("%PDF")) {
            return DocumentoTipo.BALANCETE;
        }
        return DocumentoTipo.OUTRO; // PDF, SPED ainda não reconhecido ou conteúdo desconhecido
    }
}
