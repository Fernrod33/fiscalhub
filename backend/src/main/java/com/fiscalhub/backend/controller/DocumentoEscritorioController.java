package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import com.fiscalhub.backend.auditoria.Auditavel;
import com.fiscalhub.backend.dto.ArquivoDocumento;
import com.fiscalhub.backend.dto.DocumentoFiscalRequestDTO;
import com.fiscalhub.backend.dto.DocumentoFiscalResponseDTO;
import com.fiscalhub.backend.dto.InconsistenciaDTO;
import com.fiscalhub.backend.dto.StatusDocumentoDTO;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.security.ConsultorOuAdmin;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.DocumentoFiscalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Documentos do escritório do usuário: central de importação (upload sem cliente: o cliente vem do arquivo), lista,
 * painel e as operações de cada documento. Documento de outro escritório responde 404.
 */
@RestController
@RequestMapping("/api/documentos")
public class DocumentoEscritorioController {

    /** Corpo opcional do confirmar-cliente: sem clienteId, cadastra a empresa lida do arquivo. */
    public record ConfirmacaoCliente(Long clienteId) {
    }

    private final DocumentoFiscalService documentoFiscalService;

    public DocumentoEscritorioController(DocumentoFiscalService documentoFiscalService) {
        this.documentoFiscalService = documentoFiscalService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Auditavel(AcaoAuditoria.DOCUMENTO_IMPORTAR)
    public ResponseEntity<DocumentoFiscalResponseDTO> importar(
            @RequestPart("arquivo") MultipartFile arquivo,
            @RequestPart(value = "documento", required = false) @Valid DocumentoFiscalRequestDTO documento,
            @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentoFiscalService.importar(null, arquivo, documento, ua));
    }

    @GetMapping
    public PagedModel<DocumentoFiscalResponseDTO> listar(@AuthenticationPrincipal UsuarioAutenticado ua,
                                                         @RequestParam(required = false) Long clienteId,
                                                         @RequestParam(required = false) DocumentoTipo tipo,
                                                         @RequestParam(required = false) DocumentoStatus status,
                                                         @PageableDefault(size = 20, sort = "dataImportacao",
                                                                 direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(documentoFiscalService.listarDoEscritorio(ua, clienteId, tipo, status, pageable));
    }

    @GetMapping("/resumo")
    public Map<DocumentoStatus, Long> resumo(@AuthenticationPrincipal UsuarioAutenticado ua) {
        return documentoFiscalService.resumo(ua);
    }

    @GetMapping("/{documentoId}")
    public DocumentoFiscalResponseDTO consultar(@PathVariable Long documentoId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return documentoFiscalService.consultar(documentoId, ua);
    }

    @GetMapping("/{documentoId}/status")
    public StatusDocumentoDTO status(@PathVariable Long documentoId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return documentoFiscalService.status(documentoId, ua);
    }

    @GetMapping("/{documentoId}/inconsistencias")
    public List<InconsistenciaDTO> inconsistencias(@PathVariable Long documentoId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return documentoFiscalService.inconsistencias(documentoId, ua);
    }

    @GetMapping("/{documentoId}/arquivo")
    @Auditavel(value = AcaoAuditoria.DOCUMENTO_BAIXAR, idParam = "documentoId")
    public ResponseEntity<StreamingResponseBody> baixar(@PathVariable Long documentoId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        ArquivoDocumento arquivo = documentoFiscalService.abrirArquivo(documentoId, ua);
        StreamingResponseBody corpo = saida -> {
            try (InputStream in = arquivo.conteudo()) {
                in.transferTo(saida);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(arquivo.mimeType()))
                .contentLength(arquivo.tamanho())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(arquivo.nome(), StandardCharsets.UTF_8).build().toString())
                .body(corpo);
    }

    @PostMapping("/{documentoId}/reprocessar")
    @ConsultorOuAdmin
    @Auditavel(value = AcaoAuditoria.DOCUMENTO_REPROCESSAR, idParam = "documentoId")
    public DocumentoFiscalResponseDTO reprocessar(@PathVariable Long documentoId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return documentoFiscalService.reprocessar(documentoId, ua);
    }

    @PostMapping("/{documentoId}/confirmar-cliente")
    @ConsultorOuAdmin
    @Auditavel(value = AcaoAuditoria.DOCUMENTO_CONFIRMAR_CLIENTE, idParam = "documentoId")
    public DocumentoFiscalResponseDTO confirmarCliente(@PathVariable Long documentoId,
                                                       @RequestBody(required = false) ConfirmacaoCliente corpo,
                                                       @AuthenticationPrincipal UsuarioAutenticado ua) {
        return documentoFiscalService.confirmarCliente(documentoId, corpo == null ? null : corpo.clienteId(), ua);
    }

    @DeleteMapping("/{documentoId}")
    @ConsultorOuAdmin
    @Auditavel(value = AcaoAuditoria.DOCUMENTO_EXCLUIR, idParam = "documentoId")
    public ResponseEntity<Void> excluir(@PathVariable Long documentoId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        documentoFiscalService.excluir(documentoId, ua);
        return ResponseEntity.noContent().build();
    }
}
