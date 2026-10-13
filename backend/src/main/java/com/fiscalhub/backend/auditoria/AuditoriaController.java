package com.fiscalhub.backend.auditoria;

import com.fiscalhub.backend.security.UsuarioAutenticado;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consulta da trilha de auditoria: só ADMIN, só o próprio escritório. */
@RestController
public class AuditoriaController {

    private final AuditoriaEventoRepository repository;

    AuditoriaController(AuditoriaEventoRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/auditoria")
    @PreAuthorize("hasRole('ADMIN')")
    public PagedModel<AuditoriaEventoDTO> listar(@AuthenticationPrincipal UsuarioAutenticado ua,
                                                 @PageableDefault(size = 50) Pageable pageable) {
        return new PagedModel<>(repository.findByEscritorioIdOrderByDataHoraDescIdDesc(ua.escritorioId(), pageable)
                .map(AuditoriaEventoDTO::fromEntity));
    }
}
