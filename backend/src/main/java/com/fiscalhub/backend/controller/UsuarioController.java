package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.dto.AlteracaoPapelDTO;
import com.fiscalhub.backend.dto.NovoUsuarioDTO;
import com.fiscalhub.backend.dto.TrocaSenhaDTO;
import com.fiscalhub.backend.dto.UsuarioResponseDTO;
import com.fiscalhub.backend.dto.UsuarioUpdateDTO;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.UsuarioService;
import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import com.fiscalhub.backend.auditoria.Auditavel;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** /me: o próprio cadastro. Demais rotas: gestão dos usuários do escritório, só ADMIN. */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/me")
    public UsuarioResponseDTO getMe(@AuthenticationPrincipal UsuarioAutenticado ua) {
        return UsuarioResponseDTO.fromEntity(usuarioService.findById(ua.id()));
    }

    @PutMapping("/me")
    public UsuarioResponseDTO updateMe(@Valid @RequestBody UsuarioUpdateDTO dto, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return UsuarioResponseDTO.fromEntity(usuarioService.update(ua, dto));
    }

    @PutMapping("/me/senha")
    public ResponseEntity<Void> trocarSenha(@Valid @RequestBody TrocaSenhaDTO dto, @AuthenticationPrincipal UsuarioAutenticado ua) {
        usuarioService.trocarSenha(ua, dto);
        return ResponseEntity.noContent().build();
    }

    /** Saída do próprio usuário: desativação (o último ADMIN ativo não pode sair). */
    @DeleteMapping("/me")
    @Auditavel(AcaoAuditoria.USUARIO_DESATIVAR)
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal UsuarioAutenticado ua) {
        usuarioService.desativar(ua, ua.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UsuarioResponseDTO> listar(@AuthenticationPrincipal UsuarioAutenticado ua) {
        return usuarioService.listarDoEscritorio(ua).stream().map(UsuarioResponseDTO::fromEntity).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Auditavel(AcaoAuditoria.USUARIO_CRIAR)
    public ResponseEntity<UsuarioResponseDTO> criar(@Valid @RequestBody NovoUsuarioDTO dto, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponseDTO.fromEntity(usuarioService.criarNoEscritorio(ua, dto)));
    }

    @PatchMapping("/{id}/papel")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditavel(AcaoAuditoria.USUARIO_ALTERAR_PAPEL)
    public UsuarioResponseDTO alterarPapel(@PathVariable Long id, @Valid @RequestBody AlteracaoPapelDTO dto,
                                           @AuthenticationPrincipal UsuarioAutenticado ua) {
        return UsuarioResponseDTO.fromEntity(usuarioService.alterarPapel(ua, id, dto.papel()));
    }

    @PatchMapping("/{id}/desativar")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditavel(AcaoAuditoria.USUARIO_DESATIVAR)
    public UsuarioResponseDTO desativar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return UsuarioResponseDTO.fromEntity(usuarioService.desativar(ua, id));
    }
}
