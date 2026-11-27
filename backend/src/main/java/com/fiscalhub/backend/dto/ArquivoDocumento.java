package com.fiscalhub.backend.dto;

import java.io.InputStream;

/** Arquivo aberto para download; quem consome fecha o stream. */
public record ArquivoDocumento(String nome, String mimeType, long tamanho, InputStream conteudo) {
}
