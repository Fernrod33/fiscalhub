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
                // uma pendência por vez, nesta ordem (a mesma do guarda do frontend): só a rota dela fica liberada
                UsuarioRepository.EstadoToken e = estado.get();
                String[] pendencia = e.getTrocarSenhaNoProximoLogin()
                        ? new String[]{"/api/usuarios/me/senha", RespostaProblema.SENHA_TROCA_OBRIGATORIA, "Troca de senha obrigatória",
                                "Troque a senha provisória antes de continuar."}
                        : e.getSegundoFatorPendente()
                        ? new String[]{"/api/usuarios/me/segundo-fator", RespostaProblema.SEGUNDO_FATOR_OBRIGATORIO, "Segundo fator obrigatório",
                                "O escritório exige o segundo fator: ative-o antes de continuar."}
                        : !com.fiscalhub.backend.lgpd.Termos.VERSAO.equals(e.getTermosVersao())
                        ? new String[]{"/api/usuarios/me/termos", RespostaProblema.TERMOS_PENDENTES, "Aceite dos termos pendente",
                                "Leia e aceite os Termos de Uso e a Política de Privacidade para continuar."}
                        : null;
                if (pendencia != null && !liberada(request, pendencia[0])) {
                    RespostaProblema.escrever(response, HttpServletResponse.SC_FORBIDDEN, pendencia[1], pendencia[2], pendencia[3]);
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
