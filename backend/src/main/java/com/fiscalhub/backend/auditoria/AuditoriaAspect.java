package com.fiscalhub.backend.auditoria;

import com.fiscalhub.backend.dto.LoginRequest;
import com.fiscalhub.backend.repository.UsuarioRepository;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Ponto único de gravação da auditoria: intercepta métodos com @Auditavel. */
@Aspect
@Component
public class AuditoriaAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaAspect.class);

    private final AuditoriaEventoRepository repository;
    private final UsuarioRepository usuarioRepository;

    public AuditoriaAspect(AuditoriaEventoRepository repository, UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
    }

    @Around("@annotation(auditavel)")
    public Object auditar(ProceedingJoinPoint pjp, Auditavel auditavel) throws Throwable {
        Object resultado;
        try {
            resultado = pjp.proceed();
        } catch (Throwable t) {
            if (auditavel.registrarFalha()) {
                gravar(pjp, auditavel, null, t);
            }
            throw t;
        }
        gravar(pjp, auditavel, resultado, null);
        return resultado;
    }

    private void gravar(ProceedingJoinPoint pjp, Auditavel auditavel, Object resultado, Throwable falha) {
        try {
            Map<String, Object> args = argumentos(pjp);
            AuditoriaEvento evento = new AuditoriaEvento();
            evento.setAcao(auditavel.value());
            evento.setRecursoTipo(auditavel.value().recurso());
            evento.setRecursoId(auditavel.idParam().isEmpty() ? idDoRetorno(resultado, auditavel.idCampo()) : (Long) args.get(auditavel.idParam()));
            evento.setIp(ip());
            Map<String, Object> detalhes = new HashMap<>();
            detalhes.put("resultado", falha == null ? "SUCESSO" : "FALHA");
            if (args.get("clienteId") instanceof Long clienteId) {
                detalhes.put("clienteId", clienteId);
            }
            if (corpo(resultado) instanceof Map<?, ?> resumo) { // resumo devolvido pela operação (ex.: contas alteradas)
                resumo.forEach((k, v) -> detalhes.put(String.valueOf(k), v));
            }
            identificarQuem(evento, resultado, args, detalhes);
            evento.setDetalhes(detalhes);
            repository.save(evento);
        } catch (RuntimeException e) {
            // auditoria não derruba a operação, mas a falha fica no log
            log.error("Falha ao gravar auditoria de {}", auditavel.value(), e);
        }
    }

    private void identificarQuem(AuditoriaEvento evento, Object resultado, Map<String, Object> args, Map<String, Object> detalhes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado ua) {
            evento.setUsuarioId(ua.id());
            evento.setEscritorioId(ua.escritorioId());
        } else {
            // login (ainda sem principal): identifica pelo e-mail; na falha, guarda só o e-mail tentado
            args.values().stream().filter(LoginRequest.class::isInstance).map(LoginRequest.class::cast).findFirst()
                    .ifPresent(login -> {
                        if (resultado == null) {
                            detalhes.put("email", login.getEmail());
                        }
                        usuarioRepository.findByEmail(login.getEmail()).ifPresent(u -> {
                            evento.setUsuarioId(u.getId());
                            evento.setEscritorioId(u.getEscritorio().getId());
                            if (resultado != null) {
                                evento.setRecursoId(u.getId());
                            }
                        });
                    });
        }
    }

    private static Map<String, Object> argumentos(ProceedingJoinPoint pjp) {
        String[] nomes = ((MethodSignature) pjp.getSignature()).getParameterNames();
        Map<String, Object> args = new HashMap<>();
        for (int i = 0; i < nomes.length; i++) {
            if (pjp.getArgs()[i] != null) {
                args.put(nomes[i], pjp.getArgs()[i]);
            }
        }
        return args;
    }

    private static Object corpo(Object resultado) {
        return resultado instanceof ResponseEntity<?> r ? r.getBody() : resultado;
    }

    /** Campo de id do DTO devolvido (getter ou accessor de record), se houver. */
    private static Long idDoRetorno(Object resultado, String campo) {
        Object corpo = corpo(resultado);
        if (corpo == null) {
            return null;
        }
        String getter = "get" + Character.toUpperCase(campo.charAt(0)) + campo.substring(1);
        for (String nome : new String[]{getter, campo}) {
            try {
                Method m = corpo.getClass().getMethod(nome);
                return m.invoke(corpo) instanceof Long id ? id : null;
            } catch (ReflectiveOperationException ignorado) {
                // tenta o próximo nome
            }
        }
        return null;
    }

    private static String ip() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest().getRemoteAddr() : null;
    }
}
