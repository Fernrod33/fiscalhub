package com.fiscalhub.backend.exception;

/*
    Tratamento de recurso duplicado.
*/

public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) { super(message); }
}