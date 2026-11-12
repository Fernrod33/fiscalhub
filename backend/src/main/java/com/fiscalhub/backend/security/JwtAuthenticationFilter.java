package com.fiscalhub.backend.security;

import com.fiscalhub.backend.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UsuarioRepository usuarioRepository;
    private final CookieSessao cookieSessao;
    private final SegurancaContaService seguranca;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, UsuarioRepository usuarioRepository, CookieSessao cookieSessao,
                                   @Lazy SegurancaContaService seguranca) {
        this.jwtUtils = jwtUtils;
        this.usuarioRepository = usuarioRepository;
        this.cookieSessao = cookieSessao;
        this.seguranca = seguranca;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<UsuarioAutenticado> autenticado = cookieSessao.ler(request).flatMap(jwtUtils::autenticar);
        if (autenticado.isPresent()) {
            UsuarioAutenticado u = autenticado.get();
            // ponytail: uma leitura por PK a cada requisição; cache curto quando a latência do filtro JWT for relevante
            Optional<UsuarioRepository.EstadoToken> estado = usuarioRepository.estadoToken(u.id(), u.sessaoId())
                    .filter(e -> e.getAtivo() && e.getVersaoToken() == u.versaoToken() && e.getSessaoAberta());
            if (estado.isPresent()) {
                if (estado.get().getTrocarSenhaNoProximoLogin() && !liberada(request, "/api/usuarios/me/senha")) {
                    RespostaProblema.escrever(response, HttpServletResponse.SC_FORBIDDEN, RespostaProblema.SENHA_TROCA_OBRIGATORIA,
                            "Troca de senha obrigatória", "Troque a senha provisória antes de continuar.");
                    return;
                }
                if (estado.get().getSegundoFatorPendente() && !liberada(request, "/api/usuarios/me/segundo-fator")) {
                    RespostaProblema.escrever(response, HttpServletResponse.SC_FORBIDDEN, RespostaProblema.SEGUNDO_FATOR_OBRIGATORIO,
                            "Segundo fator obrigatório", "O escritório exige o segundo fator: ative-o antes de continuar.");
                    return;
                }
                seguranca.registrarUso(u.sessaoId());
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        u, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.papel()))));
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Com a senha provisória ou o 2º fator pendente, só dá para ver o próprio cadastro, sair e resolver a pendência
     * (a rota dela e as abaixo dela).
     */
    private static boolean liberada(HttpServletRequest request, String pendencia) {
        String rota = request.getRequestURI().substring(request.getContextPath().length());
        return ("GET".equals(request.getMethod()) && rota.equals("/api/usuarios/me"))
                || rota.equals("/api/auth/logout") || rota.equals(pendencia) || rota.startsWith(pendencia + "/");
    }
}
