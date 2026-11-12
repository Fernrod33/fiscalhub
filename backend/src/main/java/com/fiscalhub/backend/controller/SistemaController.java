package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.operacao.AlertasSistema;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Só para o ADMIN do sistema (fiscalhub.admin-sistema); os demais recebem 403. */
@RestController
@RequestMapping("/api/sistema")
public class SistemaController {

    private final AlertasSistema alertas;

    SistemaController(AlertasSistema alertas) {
        this.alertas = alertas;
    }

    @GetMapping("/alertas")
    public List<AlertasSistema.AlertaSistema> alertas(@AuthenticationPrincipal UsuarioAutenticado ua) {
        exigir(ua);
        return alertas.alertas(LocalDate.now());
    }

    @GetMapping("/layouts-nao-reconhecidos")
    public List<AlertasSistema.LayoutNaoReconhecido> layouts(@AuthenticationPrincipal UsuarioAutenticado ua) {
        exigir(ua);
        return alertas.layoutsSemLeitura();
    }

    private void exigir(UsuarioAutenticado ua) {
        if (!alertas.adminSistema(ua)) {
            throw new AccessDeniedException("Só o administrador do sistema.");
        }
    }
}
