package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.entity.Empresa;
import com.fiscalhub.backend.service.EmpresaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/empresas")
@CrossOrigin(origins = "*", maxAge = 3600)
public class EmpresaController {

    @Autowired
    private EmpresaService empresaService;

    @GetMapping
    public ResponseEntity<List<Empresa>> getAllEmpresas() {
        List<Empresa> empresas = empresaService.findAll();
        return ResponseEntity.ok(empresas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Empresa> getEmpresaById(@PathVariable Long id) {
        Empresa empresa = empresaService.findById(id);
        return ResponseEntity.ok(empresa);
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<Empresa>> getEmpresasByUsuario(@PathVariable Long usuarioId) {
        List<Empresa> empresas = empresaService.findByUsuarioId(usuarioId);
        return ResponseEntity.ok(empresas);
    }

    @PostMapping
    public ResponseEntity<Empresa> createEmpresa(@Valid @RequestBody Empresa empresa, 
                                                   @RequestParam Long usuarioId) {
        Empresa createdEmpresa = empresaService.create(empresa, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdEmpresa);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Empresa> updateEmpresa(@PathVariable Long id, 
                                                   @Valid @RequestBody Empresa empresa) {
        Empresa updatedEmpresa = empresaService.update(id, empresa);
        return ResponseEntity.ok(updatedEmpresa);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEmpresa(@PathVariable Long id) {
        empresaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
