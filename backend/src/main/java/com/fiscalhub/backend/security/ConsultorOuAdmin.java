package com.fiscalhub.backend.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Operações que alteram ou excluem dados de clientes: ASSISTENTE só lê e importa. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN', 'CONSULTOR')")
public @interface ConsultorOuAdmin {
}
