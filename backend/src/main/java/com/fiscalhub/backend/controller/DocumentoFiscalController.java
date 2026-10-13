package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.dto.DocumentoFiscalRequestDTO;
import com.fiscalhub.backend.dto.DocumentoFiscalResponseDTO;
import com.fiscalhub.backend.service.DocumentoFiscalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/clientes/{clienteId}/documentos")
public class DocumentoFiscalController {

    private final DocumentoFiscalService documentoFiscalService;

    public DocumentoFiscalController(DocumentoFiscalService documentoFiscalService) {
        this.documentoFiscalService = documentoFiscalService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
    public ResponseEntity<byte[]> downloadArquivo(@PathVariable Long clienteId,
                                                @PathVariable Long documentoId,
                                                @AuthenticationPrincipal UsuarioAutenticado ua) {
        DocumentoFiscalResponseDTO documento = documentoFiscalService.consultarPorId(clienteId, documentoId, ua);
        byte[] bytes = documentoFiscalService.downloadArquivo(clienteId, documentoId, ua);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(documento.getMimeType() != null ? documento.getMimeType() : "application/octet-stream"));
        headers.setContentDispositionFormData("attachment", documento.getNomeOriginalArquivo());
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
