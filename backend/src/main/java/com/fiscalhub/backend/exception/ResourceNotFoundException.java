package com.fiscalhub.backend.exception;

/*
    Tratamento de recurso não encontrado.
*/

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
