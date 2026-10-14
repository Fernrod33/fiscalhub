package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.contabil.DemonstracaoService;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Demonstrações do cliente (publicadas e referenciais), escriturações importadas e a origem de cada valor. */
@RestController
@RequestMapping("/api/clientes/{clienteId}")
public class DemonstracaoController {

    private final DemonstracaoService service;

    public DemonstracaoController(DemonstracaoService service) {
        this.service = service;
    }

    @GetMapping("/escrituracoes")
    public List<DemonstracaoService.Escrituracao> escrituracoes(@PathVariable long clienteId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return service.escrituracoes(clienteId, ua);
    }

    /** Comparativo lado a lado: exercicios vazio = todos os disponíveis. */
    @GetMapping("/demonstracoes")
    public DemonstracaoService.Comparativo comparativo(@PathVariable long clienteId,
                                                      @RequestParam(defaultValue = "BP") String tipo,
                                                      @RequestParam(defaultValue = "REFERENCIAL") String origem,
                                                      @RequestParam(required = false) List<Integer> exercicios,
                                                      @AuthenticationPrincipal UsuarioAutenticado ua) {
        return service.comparativo(clienteId, tipo, origem, exercicios, ua);
    }

    @GetMapping("/demonstracoes/linhas/{linhaId}/origem")
    public DemonstracaoService.Origem origem(@PathVariable long clienteId, @PathVariable long linhaId,
                                             @AuthenticationPrincipal UsuarioAutenticado ua) {
        return service.origem(clienteId, linhaId, ua);
    }
}
