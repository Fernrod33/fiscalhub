package com.fiscalhub.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ação destrutiva (excluir cliente, desativar usuário, mudar papel, exportar os dados de um cliente): exige o cabeçalho
 * X-Reautenticacao com um token de POST /api/usuarios/me/reautenticacao (senha e, com o 2º fator ativo, o código), da
 * mesma sessão e com menos de fiscalhub.sessao.reautenticacao-minutos. Sem ele: 403 /problemas/reautenticacao-necessaria.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExigeReautenticacao {

    String CABECALHO = "X-Reautenticacao";

    /** A recusa; o GlobalExceptionHandler a transforma em 403 com o type próprio. */
    final class Necessaria extends RuntimeException {
        public Necessaria() {
            super("Confirme a sua senha (e o código do aplicativo, se ativo) para continuar.");
        }
    }

    @Configuration
    class Verificacao implements WebMvcConfigurer, HandlerInterceptor {

        private final JwtUtils jwt;

        Verificacao(JwtUtils jwt) {
            this.jwt = jwt;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(this).addPathPatterns("/api/**");
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            // o despacho ASYNC (download em fluxo) continua uma requisição que já passou por aqui
            if (request.getDispatcherType() != jakarta.servlet.DispatcherType.ASYNC
                    && handler instanceof HandlerMethod m && m.hasMethodAnnotation(ExigeReautenticacao.class)) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                UsuarioAutenticado ua = auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u ? u : null;
                if (!jwt.reautenticacaoValida(request.getHeader(CABECALHO), ua)) {
                    throw new Necessaria();
                }
            }
            return true;
        }
    }
}
