package com.fiscalhub.backend.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Erros no formato ProblemDetail (RFC 9457). Exceções do Spring MVC (rota inexistente, método não suportado,
 * corpo ilegível...) são tratadas pela classe base; aqui ficam só as da aplicação.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail naoEncontrado(ResourceNotFoundException ex) {
        return problema(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail regraDeNegocio(BusinessRuleException ex) {
        return problema(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail violacaoDeRestricao(ConstraintViolationException ex) {
        return problema(HttpStatus.BAD_REQUEST, "Dados inválidos.");
    }

    @ExceptionHandler(DuplicateResourceException.class)
    ProblemDetail duplicado(DuplicateResourceException ex) {
        ProblemDetail pd = problema(HttpStatus.CONFLICT, ex.getMessage());
        if (ex.getRecursoExistenteId() != null) {
            pd.setProperty("recursoExistenteId", ex.getRecursoExistenteId());
        }
        return pd;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridade(DataIntegrityViolationException ex) {
        return problema(HttpStatus.CONFLICT, "Operação conflita com dados existentes.");
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail autenticacao(AuthenticationException ex) {
        return problema(HttpStatus.UNAUTHORIZED, "Credenciais inválidas.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail acessoNegado(AccessDeniedException ex) {
        return problema(HttpStatus.FORBIDDEN, "Seu papel não permite esta operação.");
    }

    /** Nunca devolve a mensagem interna: registra com um id de correlação e devolve só o id. */
    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception ex) {
        String correlacao = UUID.randomUUID().toString();
        log.error("Erro inesperado [correlacao={}]", correlacao, ex);
        ProblemDetail pd = problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno. Informe o código " + correlacao + " ao suporte.");
        pd.setProperty("correlacao", correlacao);
        return pd;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = ex.getBody();
        pd.setDetail("Dados inválidos.");
        pd.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, e -> String.valueOf(e.getDefaultMessage()), (a, b) -> a)));
        return handleExceptionInternal(ex, pd, headers, status, request);
    }

    private static ProblemDetail problema(HttpStatus status, String detalhe) {
        return ProblemDetail.forStatusAndDetail(status, detalhe);
    }
}
