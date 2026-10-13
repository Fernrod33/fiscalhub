package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.entity.Declaracao;
import com.fiscalhub.backend.service.DeclaracaoService;
import com.fiscalhub.backend.security.ConsultorOuAdmin;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.fiscalhub.backend.security.UsuarioAutenticado;
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
    public ResponseEntity<Declaracao> getDeclaracaoById(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.ok(declaracaoService.findById(id, ua));
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<Declaracao>> getDeclaracoesByCliente(@PathVariable Long clienteId,
                                                                    @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.ok(declaracaoService.findByClienteId(clienteId, ua));
    }

    @PostMapping
    @ConsultorOuAdmin
    public ResponseEntity<Declaracao> createDeclaracao(@Valid @RequestBody Declaracao declaracao,
                                                       @RequestParam Long clienteId,
                                                       @AuthenticationPrincipal UsuarioAutenticado ua) {
        Declaracao created = declaracaoService.create(declaracao, clienteId, ua);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @ConsultorOuAdmin
    public ResponseEntity<Declaracao> updateDeclaracao(@PathVariable Long id,
                                                       @Valid @RequestBody Declaracao declaracao,
                                                       @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.ok(declaracaoService.update(id, declaracao, ua));
    }

    @DeleteMapping("/{id}")
    @ConsultorOuAdmin
    public ResponseEntity<Void> deleteDeclaracao(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado ua) {
        declaracaoService.delete(id, ua);
        return ResponseEntity.noContent().build();
    }
}
