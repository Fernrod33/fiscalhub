package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.dto.EmpresaRequestDTO;
import com.fiscalhub.backend.dto.EmpresaResponseDTO;
import com.fiscalhub.backend.service.EmpresaService;
import com.fiscalhub.backend.security.ConsultorOuAdmin;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/empresas")
public class EmpresaController {

    private final EmpresaService empresaService;

    EmpresaController(EmpresaService empresaService) {
        this.empresaService = empresaService;
    }

    @GetMapping("/minhas")
    public ResponseEntity<List<EmpresaResponseDTO>> getMinhasEmpresas(Authentication authentication) {
        return ResponseEntity.ok(empresaService.findAllByOwner(authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmpresaResponseDTO> getEmpresaById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(empresaService.findByIdAndOwner(id, authentication.getName()));
    }

    @PostMapping
    @ConsultorOuAdmin
    public ResponseEntity<EmpresaResponseDTO> createEmpresa(@Valid @RequestBody EmpresaRequestDTO dto,
                                                             Authentication authentication) {
        EmpresaResponseDTO created = empresaService.create(dto, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @ConsultorOuAdmin
    public ResponseEntity<EmpresaResponseDTO> updateEmpresa(@PathVariable Long id,
                                                             @Valid @RequestBody EmpresaRequestDTO dto,
                                                             Authentication authentication) {
        return ResponseEntity.ok(empresaService.update(id, dto, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    @ConsultorOuAdmin
    public ResponseEntity<?> deleteEmpresa(@PathVariable Long id, Authentication authentication) {
        empresaService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
