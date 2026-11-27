package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.fiscal.ApuracaoService;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** O que o cliente apurou (ECF, EFD-Contribuições, EFD ICMS/IPI): apurações, detalhe, saldos fiscais e alertas. */
@RestController
@RequestMapping("/api/clientes/{clienteId}")
public class ApuracaoController {

    private final ApuracaoService service;

    public ApuracaoController(ApuracaoService service) {
        this.service = service;
    }

    @GetMapping("/apuracoes")
    public List<ApuracaoService.Apuracao> apuracoes(@PathVariable long clienteId,
                                                    @RequestParam(required = false) TipoTributo tributo,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                                    @AuthenticationPrincipal UsuarioAutenticado ua) {
        return service.apuracoes(clienteId, tributo, de, ate, ua);
    }

    @GetMapping("/apuracoes/{apuracaoId}/detalhe")
    public ApuracaoService.Detalhamento detalhe(@PathVariable long clienteId, @PathVariable long apuracaoId,
                                                @AuthenticationPrincipal UsuarioAutenticado ua) {
        return service.detalhe(clienteId, apuracaoId, ua);
    }

    @GetMapping("/saldos-fiscais")
    public List<ApuracaoService.SaldoFiscal> saldos(@PathVariable long clienteId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return service.saldos(clienteId, ua);
    }

    /** Fórmulas que não fecham, contagem e conferências entre arquivos (ECD × ECF). */
    @GetMapping("/apuracoes/alertas")
    public List<ApuracaoService.Alerta> alertas(@PathVariable long clienteId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return service.alertas(clienteId, ua);
    }
}
