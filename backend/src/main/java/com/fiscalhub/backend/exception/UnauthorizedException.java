package com.fiscalhub.backend.exception;

/*
    Tratamento de acesso não autorizado.
*/

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException() { super(); }
    public UnauthorizedException(String message) { super(message); }
    public UnauthorizedException(String message, Throwable cause) { super(message, cause); }
}