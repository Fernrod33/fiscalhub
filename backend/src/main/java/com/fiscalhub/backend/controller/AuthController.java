package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.dto.JwtResponse;
import com.fiscalhub.backend.dto.LoginRequest;
import com.fiscalhub.backend.dto.MessageResponse;
import com.fiscalhub.backend.dto.SignupRequest;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.security.JwtUtils;
import com.fiscalhub.backend.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getSenha()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        Usuario usuario = usuarioService.findByEmail(loginRequest.getEmail());

        return ResponseEntity.ok(new JwtResponse(jwt, usuario.getId(), usuario.getEmail(), usuario.getNome()));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        if (usuarioService.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!"));
        }

        Usuario usuario = new Usuario();
        usuario.setNome(signUpRequest.getNome());
        usuario.setEmail(signUpRequest.getEmail());
        usuario.setSenha(signUpRequest.getSenha());
        usuario.setCpf(signUpRequest.getCpf());
        usuario.setTelefone(signUpRequest.getTelefone());
        usuario.setAtivo(true);

        usuarioService.create(usuario);

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }
}
