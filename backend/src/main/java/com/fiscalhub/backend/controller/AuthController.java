package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.dto.LoginRequest;
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

    AuthController(AuthenticationManager authenticationManager, UsuarioService usuarioService, JwtUtils jwtUtils,
                   CookieSessao cookieSessao) {
        this.authenticationManager = authenticationManager;
        this.usuarioService = usuarioService;
        this.jwtUtils = jwtUtils;
        this.cookieSessao = cookieSessao;
    }

    @PostMapping("/login")
    @Auditavel(value = AcaoAuditoria.LOGIN, registrarFalha = true)
    public UsuarioResponseDTO authenticateUser(@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getSenha()));

        Usuario usuario = usuarioService.findByEmail(authentication.getName());
        cookieSessao.emitir(response, jwtUtils.gerarToken(usuario));
        return UsuarioResponseDTO.fromEntity(usuario);
    }

    /**
     * Apaga o cookie e invalida o JWT no servidor (versão do token). Sem sessão válida, só apaga o cookie.
     * ponytail: a versão do token é por usuário, então o logout encerra as sessões de todos os dispositivos; trocar por
     * sessão por dispositivo (id da sessão no JWT + tabela de sessões revogadas) se houver pedido de logout por dispositivo.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UsuarioAutenticado ua, HttpServletResponse response) {
        if (ua != null) {
            usuarioService.encerrarSessao(ua);
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
