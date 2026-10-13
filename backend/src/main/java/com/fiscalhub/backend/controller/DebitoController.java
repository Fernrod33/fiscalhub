package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.dto.DebitoResponseDTO;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.DebitoService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Só leitura: débitos são gerados pela importação de arquivos. */
@RestController
public class DebitoController {

    private final DebitoService debitoService;

    DebitoController(DebitoService debitoService) {
        this.debitoService = debitoService;
    }

    @GetMapping("/api/clientes/{clienteId}/debitos")
    public List<DebitoResponseDTO> listar(@PathVariable Long clienteId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return debitoService.listarPorCliente(clienteId, ua);
    }
}
