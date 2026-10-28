package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import com.fiscalhub.backend.auditoria.Auditavel;
import com.fiscalhub.backend.relatorio.IdentidadeEscritorioService;
import com.fiscalhub.backend.relatorio.IdentidadeEscritorioService.Identidade;
import com.fiscalhub.backend.relatorio.IdentidadeEscritorioService.IdentidadeRequest;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Identidade do escritório nos relatórios: todos leem (a prévia usa), só o ADMIN altera; alterações auditadas. */
@RestController
@RequestMapping("/api/escritorio/identidade")
public class EscritorioController {

    private final IdentidadeEscritorioService identidade;

    public EscritorioController(IdentidadeEscritorioService identidade) {
        this.identidade = identidade;
    }

    @GetMapping
    public Identidade consultar(@AuthenticationPrincipal UsuarioAutenticado ua) {
        return identidade.consultar(ua);
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Auditavel(value = AcaoAuditoria.IDENTIDADE_ALTERAR, idCampo = "escritorioId")
    public Identidade alterar(@Valid @RequestBody IdentidadeRequest corpo, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return identidade.alterar(corpo, ua);
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Auditavel(value = AcaoAuditoria.IDENTIDADE_LOGO, idCampo = "escritorioId")
    public Identidade logo(@RequestPart("arquivo") MultipartFile arquivo, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return identidade.gravarLogo(arquivo, ua);
    }

    /** Sempre PNG (regravado no upload); nosniff vem do Spring Security. */
    @GetMapping(value = "/logo", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> baixarLogo(@AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).contentType(MediaType.IMAGE_PNG).body(identidade.logo(ua));
    }
}
