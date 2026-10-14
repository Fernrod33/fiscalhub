package com.fiscalhub.backend.auditoria;

import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final UsuarioRepository usuarioRepository;

    AuditoriaController(AuditoriaEventoRepository repository, UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/api/auditoria")
    @PreAuthorize("hasRole('ADMIN')")
    public PagedModel<AuditoriaEventoDTO> listar(@AuthenticationPrincipal UsuarioAutenticado ua,
                                                 @PageableDefault(size = 50) Pageable pageable) {
        Page<AuditoriaEvento> eventos = repository.findByEscritorioIdOrderByDataHoraDescIdDesc(ua.escritorioId(), pageable);
        // nomes da página numa consulta só (a trilha guarda o id; o usuário pode mudar de nome depois)
        Set<Long> ids = eventos.stream().map(AuditoriaEvento::getUsuarioId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> nomes = usuarioRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNome));
        return new PagedModel<>(eventos.map(e -> AuditoriaEventoDTO.de(e, nomes.get(e.getUsuarioId()))));
    }
}
