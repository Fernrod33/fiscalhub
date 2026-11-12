package com.fiscalhub.backend.security;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Política de senha (ASVS 2.1): de 12 a 64 caracteres, no máximo 72 bytes (limite do BCrypt) e fora da lista local de
 * senhas vazadas comuns ({@code resources/seguranca/senhas-vazadas.txt}, sem consulta a serviço externo). Sem regra de
 * composição (maiúscula, símbolo): o comprimento e a lista valem mais (NIST 800-63B).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SenhaForte.Validador.class)
public @interface SenhaForte {

    String message() default "Use de 12 a 64 caracteres, fora da lista de senhas vazadas comuns.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int MINIMO = 12;
    int MAXIMO = 64;

    class Validador implements ConstraintValidator<SenhaForte, String> {

        private static final Set<String> VAZADAS = carregar();

        private static Set<String> carregar() {
            var in = SenhaForte.class.getClassLoader().getResourceAsStream("seguranca/senhas-vazadas.txt");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return r.lines().filter(l -> !l.startsWith("#") && !l.isBlank()).collect(Collectors.toUnmodifiableSet());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /** O motivo da recusa (para a mensagem) ou nulo se a senha serve. */
        public static String problema(String senha) {
            if (senha == null || senha.codePointCount(0, senha.length()) < MINIMO) {
                return "A senha precisa ter pelo menos " + MINIMO + " caracteres.";
            }
            if (senha.codePointCount(0, senha.length()) > MAXIMO || senha.getBytes(StandardCharsets.UTF_8).length > 72) {
                return "A senha pode ter no máximo " + MAXIMO + " caracteres.";
            }
            if (VAZADAS.contains(senha.toLowerCase(Locale.ROOT))) {
                return "Esta senha aparece em listas de senhas vazadas. Escolha outra.";
            }
            return null;
        }

        @Override
        public boolean isValid(String senha, ConstraintValidatorContext ctx) {
            String problema = problema(senha);
            if (problema == null) {
                return true;
            }
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate(problema).addConstraintViolation();
            return false;
        }
    }
}
