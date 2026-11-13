package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.lgpd.LgpdService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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
    private final LgpdService lgpd;

    ClienteController(ClienteService clienteService, LgpdService lgpd) {
        this.clienteService = clienteService;
        this.lgpd = lgpd;
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

    public record PedidoExclusao(String confirmacao) {
    }

    /**
     * Exclusão (LGPD): só ADMIN, com a segunda confirmação (o CNPJ digitado). Os dados ficam até o fim do prazo de
     * arrependimento (fiscalhub.lgpd.prazo-exclusao-dias); depois o job remove tudo, inclusive os arquivos.
     */
    @PostMapping("/{id}/exclusao")
    @com.fiscalhub.backend.security.ExigeReautenticacao
    @PreAuthorize("hasRole('ADMIN')")
    @Auditavel(value = AcaoAuditoria.CLIENTE_EXCLUSAO_SOLICITAR, idParam = "id")
    public ClienteResponseDTO solicitarExclusao(@PathVariable Long id, @RequestBody PedidoExclusao pedido,
                                                @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ClienteResponseDTO.fromEntity(lgpd.solicitarExclusao(id, pedido.confirmacao(), ua));
    }

    @DeleteMapping("/{id}/exclusao")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditavel(value = AcaoAuditoria.CLIENTE_EXCLUSAO_CANCELAR, idParam = "id")
    public ClienteResponseDTO cancelarExclusao(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ClienteResponseDTO.fromEntity(lgpd.cancelarExclusao(id, ua));
    }

    /** Portabilidade: ZIP com os arquivos originais, os relatórios emitidos e os dados em JSON. Só ADMIN. */
    @GetMapping("/{id}/exportacao")
    @com.fiscalhub.backend.security.ExigeReautenticacao
    @PreAuthorize("hasRole('ADMIN')")
    @Auditavel(value = AcaoAuditoria.CLIENTE_EXPORTAR, idParam = "id")
    public ResponseEntity<StreamingResponseBody> exportar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado ua) {
        String nome = lgpd.nomeExportacao(id, ua);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .body(saida -> lgpd.exportar(id, ua, saida));
    }
}
