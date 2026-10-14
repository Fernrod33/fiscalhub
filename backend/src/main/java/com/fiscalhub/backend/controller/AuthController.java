package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.dto.JwtResponse;
import com.fiscalhub.backend.dto.LoginRequest;
import com.fiscalhub.backend.dto.MessageResponse;
import com.fiscalhub.backend.dto.SignupRequest;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.security.JwtUtils;
import com.fiscalhub.backend.service.UsuarioService;
import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import com.fiscalhub.backend.auditoria.Auditavel;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;

    private final UsuarioService usuarioService;

    private final JwtUtils jwtUtils;

    AuthController(AuthenticationManager authenticationManager, UsuarioService usuarioService, JwtUtils jwtUtils) {
        this.authenticationManager = authenticationManager;
        this.usuarioService = usuarioService;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/login")
    @Auditavel(value = AcaoAuditoria.LOGIN, registrarFalha = true)
    public ResponseEntity<JwtResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getSenha()));

        Usuario usuario = usuarioService.findByEmail(authentication.getName());
        return ResponseEntity.ok(JwtResponse.de(jwtUtils.gerarToken(usuario), usuario));
    }

    @PostMapping("/signup")
    public ResponseEntity<MessageResponse> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        usuarioService.registrarEscritorio(signUpRequest);

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }
}
