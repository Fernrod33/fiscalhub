package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.entity.Declaracao;
import com.fiscalhub.backend.entity.enums.StatusDeclaracao;
import com.fiscalhub.backend.service.DeclaracaoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/declaracoes")
public class DeclaracaoController {

    private final DeclaracaoService declaracaoService;

    DeclaracaoController(DeclaracaoService declaracaoService) {
        this.declaracaoService = declaracaoService;
    }

    @GetMapping
    public ResponseEntity<List<Declaracao>> getAllDeclaracoes() {
        return ResponseEntity.ok(declaracaoService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Declaracao> getDeclaracaoById(@PathVariable Long id) {
        return ResponseEntity.ok(declaracaoService.findById(id));
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<Declaracao>> getDeclaracoesByUsuario(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(declaracaoService.findByUsuarioId(usuarioId));
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<Declaracao>> getDeclaracoesByEmpresa(@PathVariable Long empresaId) {
        return ResponseEntity.ok(declaracaoService.findByEmpresaId(empresaId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Declaracao>> getDeclaracoesByStatus(@PathVariable StatusDeclaracao status) {
        return ResponseEntity.ok(declaracaoService.findByStatus(status));
    }

    @PostMapping
    public ResponseEntity<Declaracao> createDeclaracao(@Valid @RequestBody Declaracao declaracao,
                                                        @RequestParam Long usuarioId,
                                                        @RequestParam Long empresaId) {
        Declaracao created = declaracaoService.create(declaracao, usuarioId, empresaId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Declaracao> updateDeclaracao(@PathVariable Long id,
                                                        @Valid @RequestBody Declaracao declaracao) {
        return ResponseEntity.ok(declaracaoService.update(id, declaracao));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDeclaracao(@PathVariable Long id) {
        declaracaoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
