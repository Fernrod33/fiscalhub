package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.entity.Declaracao;
import com.fiscalhub.backend.service.DeclaracaoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/declaracoes")
public class DeclaracaoController {

    private final DeclaracaoService declaracaoService;

    DeclaracaoController(DeclaracaoService declaracaoService) {
        this.declaracaoService = declaracaoService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Declaracao> getDeclaracaoById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(declaracaoService.findById(id, authentication.getName()));
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<Declaracao>> getDeclaracoesByEmpresa(@PathVariable Long empresaId,
                                                                    Authentication authentication) {
        return ResponseEntity.ok(declaracaoService.findByEmpresaId(empresaId, authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<Declaracao> createDeclaracao(@Valid @RequestBody Declaracao declaracao,
                                                       @RequestParam Long empresaId,
                                                       Authentication authentication) {
        Declaracao created = declaracaoService.create(declaracao, empresaId, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Declaracao> updateDeclaracao(@PathVariable Long id,
                                                       @Valid @RequestBody Declaracao declaracao,
                                                       Authentication authentication) {
        return ResponseEntity.ok(declaracaoService.update(id, declaracao, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDeclaracao(@PathVariable Long id, Authentication authentication) {
        declaracaoService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
