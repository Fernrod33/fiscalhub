package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import com.fiscalhub.backend.auditoria.Auditavel;
import com.fiscalhub.backend.dto.DocumentoFiscalRequestDTO;
import com.fiscalhub.backend.dto.DocumentoFiscalResponseDTO;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.DocumentoFiscalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Upload dentro de um cliente (o CNPJ do arquivo tem que ser o dele) e a lista do cliente. O resto fica em /api/documentos. */
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
            @RequestPart(value = "documento", required = false) @Valid DocumentoFiscalRequestDTO documento,
            @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentoFiscalService.importar(clienteId, arquivo, documento, ua));
    }

    @GetMapping
    public List<DocumentoFiscalResponseDTO> listarDocumentos(@PathVariable Long clienteId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return documentoFiscalService.listarPorCliente(clienteId, ua);
    }
}
