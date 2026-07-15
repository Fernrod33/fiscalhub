package com.fiscalhub.backend.exception;

/*
    Tratamento de regra de negócio violada.
*/
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }

    public BusinessRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
