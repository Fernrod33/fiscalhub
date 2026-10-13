package com.fiscalhub.backend.exception;

/*
    Tratamento de recurso duplicado.
*/

public class DuplicateResourceException extends RuntimeException {

    /** Id do recurso que já existe, quando útil ao cliente da API (ex.: documento duplicado). */
    private final Long recursoExistenteId;

    public DuplicateResourceException(String message) {
        this(message, null);
    }

    public DuplicateResourceException(String message, Long recursoExistenteId) {
        super(message);
        this.recursoExistenteId = recursoExistenteId;
    }

    public Long getRecursoExistenteId() {
        return recursoExistenteId;
    }
}
