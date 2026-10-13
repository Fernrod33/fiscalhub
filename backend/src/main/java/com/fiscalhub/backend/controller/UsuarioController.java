package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.dto.UsuarioResponseDTO;
import com.fiscalhub.backend.dto.UsuarioUpdateDTO;
import com.fiscalhub.backend.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Cada usuário só enxerga e altera o próprio cadastro. */
@RestController
@RequestMapping("/api/usuarios/me")
public class UsuarioController {

    private final UsuarioService usuarioService;

    UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public ResponseEntity<UsuarioResponseDTO> getMe(Authentication authentication) {
        return ResponseEntity.ok(UsuarioResponseDTO.fromEntity(usuarioService.findByEmail(authentication.getName())));
    }

    @PutMapping
    public ResponseEntity<UsuarioResponseDTO> updateMe(@Valid @RequestBody UsuarioUpdateDTO dto,
                                                       Authentication authentication) {
        return ResponseEntity.ok(UsuarioResponseDTO.fromEntity(usuarioService.update(authentication.getName(), dto)));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteMe(Authentication authentication) {
        usuarioService.delete(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
