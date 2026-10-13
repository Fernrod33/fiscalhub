package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.dto.DocumentoFiscalRequestDTO;
import com.fiscalhub.backend.dto.DocumentoFiscalResponseDTO;
import com.fiscalhub.backend.service.DocumentoFiscalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/empresas/{empresaId}/documentos")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DocumentoFiscalController {

    private final DocumentoFiscalService documentoFiscalService;

    public DocumentoFiscalController(DocumentoFiscalService documentoFiscalService) {
        this.documentoFiscalService = documentoFiscalService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentoFiscalResponseDTO> importarDocumento(
            @PathVariable Long empresaId,
            @RequestPart("arquivo") MultipartFile arquivo,
            @RequestPart("documento") @Valid DocumentoFiscalRequestDTO documento,
            Authentication authentication) {
        DocumentoFiscalResponseDTO saved = documentoFiscalService.importar(empresaId, arquivo, documento, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public ResponseEntity<List<DocumentoFiscalResponseDTO>> listarDocumentos(@PathVariable Long empresaId,
                                                                          Authentication authentication) {
        return ResponseEntity.ok(documentoFiscalService.listarPorEmpresa(empresaId, authentication.getName()));
    }

    @GetMapping("/{documentoId}")
    public ResponseEntity<DocumentoFiscalResponseDTO> consultarDocumento(@PathVariable Long empresaId,
                                                                       @PathVariable Long documentoId,
                                                                       Authentication authentication) {
        return ResponseEntity.ok(documentoFiscalService.consultarPorId(empresaId, documentoId, authentication.getName()));
    }

    @GetMapping("/{documentoId}/arquivo")
    public ResponseEntity<byte[]> downloadArquivo(@PathVariable Long empresaId,
                                                @PathVariable Long documentoId,
                                                Authentication authentication) {
        DocumentoFiscalResponseDTO documento = documentoFiscalService.consultarPorId(empresaId, documentoId, authentication.getName());
        byte[] bytes = documentoFiscalService.downloadArquivo(empresaId, documentoId, authentication.getName());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(documento.getMimeType() != null ? documento.getMimeType() : "application/octet-stream"));
        headers.setContentDispositionFormData("attachment", documento.getNomeOriginalArquivo());
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
