package com.fiscalhub.backend.security;

import com.fiscalhub.backend.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    public JwtAuthenticationFilter(JwtUtils jwtUtils, UsuarioRepository usuarioRepository, CookieSessao cookieSessao) {
        this.jwtUtils = jwtUtils;
        this.usuarioRepository = usuarioRepository;
        this.cookieSessao = cookieSessao;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<UsuarioAutenticado> autenticado = cookieSessao.ler(request).flatMap(jwtUtils::autenticar);
        if (autenticado.isPresent()) {
            UsuarioAutenticado u = autenticado.get();
            // ponytail: uma leitura por PK a cada requisição; cache curto quando a latência do filtro JWT for relevante
            Optional<UsuarioRepository.EstadoToken> estado = usuarioRepository.findEstadoTokenById(u.id())
                    .filter(e -> e.getAtivo() && e.getVersaoToken() == u.versaoToken());
            if (estado.isPresent()) {
                if (estado.get().getTrocarSenhaNoProximoLogin() && !liberadaDuranteTrocaDeSenha(request)) {
                    RespostaProblema.escrever(response, HttpServletResponse.SC_FORBIDDEN, RespostaProblema.SENHA_TROCA_OBRIGATORIA,
                            "Troca de senha obrigatória", "Troque a senha provisória antes de continuar.");
                    return;
                }
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        u, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.papel()))));
            }
        }
        filterChain.doFilter(request, response);
    }

    /** Com senha provisória, só dá para ver o próprio cadastro e trocar a senha. */
    private static boolean liberadaDuranteTrocaDeSenha(HttpServletRequest request) {
        String rota = request.getRequestURI().substring(request.getContextPath().length());
        return ("GET".equals(request.getMethod()) && rota.equals("/api/usuarios/me"))
                || ("PUT".equals(request.getMethod()) && rota.equals("/api/usuarios/me/senha"));
    }
}
