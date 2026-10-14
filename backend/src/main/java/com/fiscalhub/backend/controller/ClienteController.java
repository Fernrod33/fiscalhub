package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.dto.ClienteRequestDTO;
import com.fiscalhub.backend.dto.ClienteResponseDTO;
import com.fiscalhub.backend.service.ClienteService;
import com.fiscalhub.backend.security.ConsultorOuAdmin;
import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import com.fiscalhub.backend.auditoria.Auditavel;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public ResponseEntity<List<ClienteResponseDTO>> listarClientes(@AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.ok(clienteService.listar(ua));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponseDTO> getClienteById(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.ok(clienteService.buscar(id, ua));
    }

    @PostMapping
    @ConsultorOuAdmin
    @Auditavel(AcaoAuditoria.CLIENTE_CRIAR)
    public ResponseEntity<ClienteResponseDTO> createCliente(@Valid @RequestBody ClienteRequestDTO dto,
                                                             @AuthenticationPrincipal UsuarioAutenticado ua) {
        ClienteResponseDTO created = clienteService.create(dto, ua);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @ConsultorOuAdmin
    @Auditavel(AcaoAuditoria.CLIENTE_EDITAR)
    public ResponseEntity<ClienteResponseDTO> updateCliente(@PathVariable Long id,
                                                             @Valid @RequestBody ClienteRequestDTO dto,
                                                             @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.ok(clienteService.update(id, dto, ua));
    }

    @DeleteMapping("/{id}")
    @ConsultorOuAdmin
    @Auditavel(value = AcaoAuditoria.CLIENTE_EXCLUIR, idParam = "id")
    public ResponseEntity<Void> deleteCliente(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado ua) {
        clienteService.delete(id, ua);
        return ResponseEntity.noContent().build();
    }
}
