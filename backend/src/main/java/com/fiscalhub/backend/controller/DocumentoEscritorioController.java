package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.dto.DocumentoFiscalResponseDTO;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.DocumentoFiscalService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Documentos de todos os clientes do escritório do usuário (painel e central de importação). */
@RestController
@RequestMapping("/api/documentos")
public class DocumentoEscritorioController {

    private final DocumentoFiscalService documentoFiscalService;

    public DocumentoEscritorioController(DocumentoFiscalService documentoFiscalService) {
        this.documentoFiscalService = documentoFiscalService;
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
}
