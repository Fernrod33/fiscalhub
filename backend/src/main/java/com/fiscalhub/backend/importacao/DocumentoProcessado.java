package com.fiscalhub.backend.importacao;

/** Publicado quando um documento termina de ser processado (com ou sem alertas), já fora da transação. */
public record DocumentoProcessado(long documentoId) {
}
