package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.dto.DocumentoFiscalRequestDTO;
import com.fiscalhub.backend.dto.ArquivoDocumento;
import com.fiscalhub.backend.dto.DocumentoFiscalResponseDTO;
import com.fiscalhub.backend.dto.StatusDocumentoDTO;
import com.fiscalhub.backend.security.ConsultorOuAdmin;
import com.fiscalhub.backend.service.DocumentoFiscalService;
import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import com.fiscalhub.backend.auditoria.Auditavel;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import java.util.List;

@RestController
@RequestMapping("/api/clientes/{clienteId}/documentos")
public class DocumentoFiscalController {

    private final DocumentoFiscalService documentoFiscalService;

    public DocumentoFiscalController(DocumentoFiscalService documentoFiscalService) {
        this.documentoFiscalService = documentoFiscalService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Auditavel(AcaoAuditoria.DOCUMENTO_IMPORTAR)
    public ResponseEntity<DocumentoFiscalResponseDTO> importarDocumento(
            @PathVariable Long clienteId,
            @RequestPart("arquivo") MultipartFile arquivo,
            @RequestPart("documento") @Valid DocumentoFiscalRequestDTO documento,
            @AuthenticationPrincipal UsuarioAutenticado ua) {
        DocumentoFiscalResponseDTO saved = documentoFiscalService.importar(clienteId, arquivo, documento, ua);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public ResponseEntity<List<DocumentoFiscalResponseDTO>> listarDocumentos(@PathVariable Long clienteId,
                                                                          @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.ok(documentoFiscalService.listarPorCliente(clienteId, ua));
    }

    @GetMapping("/{documentoId}")
    public ResponseEntity<DocumentoFiscalResponseDTO> consultarDocumento(@PathVariable Long clienteId,
                                                                       @PathVariable Long documentoId,
                                                                       @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.ok(documentoFiscalService.consultarPorId(clienteId, documentoId, ua));
    }

    @GetMapping("/{documentoId}/arquivo")
    @Auditavel(value = AcaoAuditoria.DOCUMENTO_BAIXAR, idParam = "documentoId")
    public ResponseEntity<StreamingResponseBody> downloadArquivo(@PathVariable Long clienteId,
                                                                 @PathVariable Long documentoId,
                                                                 @AuthenticationPrincipal UsuarioAutenticado ua) {
        ArquivoDocumento arquivo = documentoFiscalService.abrirArquivo(clienteId, documentoId, ua);
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

    @GetMapping("/{documentoId}/status")
    public StatusDocumentoDTO status(@PathVariable Long clienteId, @PathVariable Long documentoId,
                                     @AuthenticationPrincipal UsuarioAutenticado ua) {
        return documentoFiscalService.status(clienteId, documentoId, ua);
    }

    @DeleteMapping("/{documentoId}")
    @ConsultorOuAdmin
    @Auditavel(value = AcaoAuditoria.DOCUMENTO_EXCLUIR, idParam = "documentoId")
    public ResponseEntity<Void> excluir(@PathVariable Long clienteId, @PathVariable Long documentoId,
                                        @AuthenticationPrincipal UsuarioAutenticado ua) {
        documentoFiscalService.excluir(clienteId, documentoId, ua);
        return ResponseEntity.noContent().build();
    }
}
