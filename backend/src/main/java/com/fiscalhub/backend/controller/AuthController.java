package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.dto.LoginRequest;
import com.fiscalhub.backend.dto.RespostaLogin;
import com.fiscalhub.backend.dto.SegundoFatorLogin;
import com.fiscalhub.backend.auditoria.DetalheAuditoria;
import com.fiscalhub.backend.security.BloqueioLogin;
import com.fiscalhub.backend.security.SegurancaContaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import com.fiscalhub.backend.dto.MessageResponse;
import com.fiscalhub.backend.dto.SignupRequest;
import com.fiscalhub.backend.dto.UsuarioResponseDTO;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.security.CookieSessao;
import com.fiscalhub.backend.security.JwtUtils;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.UsuarioService;
import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import com.fiscalhub.backend.auditoria.Auditavel;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UsuarioService usuarioService;
    private final JwtUtils jwtUtils;
    private final CookieSessao cookieSessao;
    private final BloqueioLogin bloqueio;
    private final SegurancaContaService seguranca;

    AuthController(AuthenticationManager authenticationManager, UsuarioService usuarioService, JwtUtils jwtUtils,
                   CookieSessao cookieSessao, BloqueioLogin bloqueio, SegurancaContaService seguranca) {
        this.authenticationManager = authenticationManager;
        this.usuarioService = usuarioService;
        this.jwtUtils = jwtUtils;
        this.cookieSessao = cookieSessao;
        this.bloqueio = bloqueio;
        this.seguranca = seguranca;
    }

    /**
     * Senha conferida (com o bloqueio progressivo por conta e IP). Sem o 2º fator: abre a sessão. Com ele: devolve só o
     * desafio, e a conta só sai da contagem de falhas quando o código também for aceito.
     */
    @PostMapping("/login")
    @Auditavel(value = AcaoAuditoria.LOGIN, registrarFalha = true)
    public RespostaLogin authenticateUser(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request,
                                          HttpServletResponse response) {
        bloqueio.verificar(loginRequest.getEmail(), request.getRemoteAddr());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getSenha()));
        } catch (AuthenticationException e) {
            bloqueio.registrarFalha(loginRequest.getEmail(), request.getRemoteAddr());
            throw e;
        }
        Usuario usuario = usuarioService.findByEmail(authentication.getName());
        if (usuario.getTotpAtivo()) {
            return new RespostaLogin(null, jwtUtils.gerarDesafio(usuario));
        }
        bloqueio.registrarSucesso(usuario.getEmail());
        cookieSessao.emitir(response, seguranca.abrirSessao(usuario, request));
        return new RespostaLogin(UsuarioResponseDTO.fromEntity(usuario), null);
    }

    @PostMapping("/login/segundo-fator")
    @Auditavel(value = AcaoAuditoria.LOGIN_SEGUNDO_FATOR, registrarFalha = true)
    public RespostaLogin segundoFator(@Valid @RequestBody SegundoFatorLogin corpo, HttpServletRequest request,
                                      HttpServletResponse response) {
        long[] desafio = jwtUtils.lerDesafio(corpo.desafio())
                .orElseThrow(() -> new BadCredentialsException("Desafio expirado: entre com a senha de novo."));
        Usuario usuario = usuarioService.findById(desafio[0]);
        if (!usuario.getAtivo() || usuario.getVersaoToken() != desafio[1]) {
            throw new BadCredentialsException("Desafio expirado: entre com a senha de novo.");
        }
        bloqueio.verificar(usuario.getEmail(), request.getRemoteAddr());
        if (!seguranca.conferirSegundoFator(usuario, corpo.codigo())) {
            bloqueio.registrarFalha(usuario.getEmail(), request.getRemoteAddr());
            throw new CodigoInvalido(usuario.getId());
        }
        bloqueio.registrarSucesso(usuario.getEmail());
        cookieSessao.emitir(response, seguranca.abrirSessao(usuario, request));
        return new RespostaLogin(UsuarioResponseDTO.fromEntity(usuario), null);
    }

    /** Código errado no 2º fator: 401 e, na auditoria, de quem era o desafio. */
    static final class CodigoInvalido extends BadCredentialsException implements DetalheAuditoria {
        private final long usuarioId;

        CodigoInvalido(long usuarioId) {
            super("Código inválido.");
            this.usuarioId = usuarioId;
        }

        @Override
        public java.util.Map<String, Object> detalhesAuditoria() {
            return java.util.Map.of("usuarioId", usuarioId, "motivo", "CODIGO_INVALIDO");
        }
    }

    /** Encerra só a sessão deste dispositivo e apaga o cookie (as outras sessões, na tela Segurança). */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UsuarioAutenticado ua, HttpServletResponse response) {
        if (ua != null) {
            seguranca.encerrarAtual(ua);
        }
        cookieSessao.limpar(response);
        return ResponseEntity.noContent().build();
    }

    /** Emite o cookie XSRF-TOKEN (o frontend chama antes da primeira alteração, se ainda não tiver o cookie). */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken(); // o token é diferido: carregá-lo grava o cookie
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/signup")
    public ResponseEntity<MessageResponse> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        usuarioService.registrarEscritorio(signUpRequest);

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }
}
