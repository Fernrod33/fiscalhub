package com.fiscalhub.backend.auditoria;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marca um método de controller para auditoria (gravada só pelo AuditoriaAspect). */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditavel {

    AcaoAuditoria value();

    /** Parâmetro que traz o id do recurso; vazio = lê o campo idCampo do retorno. */
    String idParam() default "";

    /** Campo do DTO devolvido que tem o id do recurso (getter ou accessor de record). */
    String idCampo() default "id";

    /** Registra também a tentativa que falhou (ex.: login). */
    boolean registrarFalha() default false;
}
