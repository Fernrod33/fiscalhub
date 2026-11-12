package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import com.fiscalhub.backend.auditoria.Auditavel;
import com.fiscalhub.backend.security.SegurancaContaService;
import com.fiscalhub.backend.security.SegurancaContaService.Ativacao;
import com.fiscalhub.backend.security.SegurancaContaService.CodigosRecuperacao;
import com.fiscalhub.backend.security.SegurancaContaService.Sessao;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Segurança da conta: sessões por dispositivo e segundo fator (o próprio usuário); exigência do 2º fator (ADMIN). */
@RestController
public class SegurancaController {

    public record Codigo(@NotBlank @Size(max = 20) String codigo) {
    }

    public record Senha(@NotBlank @Size(max = 200) String senha) {
    }

    /** Segurança e privacidade do escritório: exigência do 2º fator e o contato do encarregado (DPO). */
    public record ConfiguracaoSeguranca(@NotNull Boolean exigirSegundoFator, @Size(max = 150) String encarregadoNome,
                                        @jakarta.validation.constraints.Email @Size(max = 150) String encarregadoEmail) {
    }

    private final SegurancaContaService seguranca;

    SegurancaController(SegurancaContaService seguranca) {
        this.seguranca = seguranca;
    }

    @GetMapping("/api/usuarios/me/sessoes")
    public List<Sessao> sessoes(@AuthenticationPrincipal UsuarioAutenticado ua) {
        return seguranca.sessoes(ua);
    }

    @DeleteMapping("/api/usuarios/me/sessoes/{id}")
    @Auditavel(AcaoAuditoria.SESSAO_ENCERRAR)
    public ResponseEntity<Void> encerrar(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado ua) {
        seguranca.encerrar(ua, id);
        return ResponseEntity.noContent().build();
    }

    /** Encerra todas as sessões menos a atual ("sair dos outros dispositivos"). */
    @DeleteMapping("/api/usuarios/me/sessoes")
    @Auditavel(AcaoAuditoria.SESSAO_ENCERRAR)
    public Map<String, Object> encerrarOutras(@AuthenticationPrincipal UsuarioAutenticado ua) {
        return Map.of("encerradas", seguranca.encerrarOutras(ua));
    }

    @PostMapping("/api/usuarios/me/segundo-fator/iniciar")
    public Ativacao iniciar(@AuthenticationPrincipal UsuarioAutenticado ua) {
        return seguranca.iniciarSegundoFator(ua);
    }

    @PostMapping("/api/usuarios/me/segundo-fator/ativar")
    @Auditavel(AcaoAuditoria.SEGUNDO_FATOR_ATIVAR)
    public CodigosRecuperacao ativar(@Valid @RequestBody Codigo corpo, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return seguranca.ativarSegundoFator(ua, corpo.codigo());
    }

    @PostMapping("/api/usuarios/me/segundo-fator/desativar")
    @Auditavel(AcaoAuditoria.SEGUNDO_FATOR_DESATIVAR)
    public ResponseEntity<Void> desativar(@Valid @RequestBody Senha corpo, @AuthenticationPrincipal UsuarioAutenticado ua) {
        seguranca.desativarSegundoFator(ua, corpo.senha());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/usuarios/me/segundo-fator/codigos")
    @Auditavel(AcaoAuditoria.SEGUNDO_FATOR_CODIGOS)
    public CodigosRecuperacao regenerarCodigos(@Valid @RequestBody Senha corpo, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return seguranca.regenerarCodigos(ua, corpo.senha());
    }

    @GetMapping("/api/escritorio/seguranca")
    public ConfiguracaoSeguranca configuracao(@AuthenticationPrincipal UsuarioAutenticado ua) {
        return seguranca.configuracao(ua);
    }

    @PutMapping("/api/escritorio/seguranca")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditavel(AcaoAuditoria.ESCRITORIO_SEGURANCA)
    public ConfiguracaoSeguranca alterar(@Valid @RequestBody ConfiguracaoSeguranca corpo, @AuthenticationPrincipal UsuarioAutenticado ua) {
        seguranca.alterarConfiguracao(ua, corpo);
        return seguranca.configuracao(ua);
    }
}
