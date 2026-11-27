package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import com.fiscalhub.backend.auditoria.Auditavel;
import com.fiscalhub.backend.contabil.planilha.PlanilhaService;
import com.fiscalhub.backend.security.ConsultorOuAdmin;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Assistente do balancete em planilha (colunas, período e de-para) e busca no plano referencial. Mapear e associar
 * reprocessam o documento e mudam os números: ADMIN e CONSULTOR, com auditoria. O ASSISTENTE importa e consulta.
 */
@RestController
public class PlanilhaController {

    private final PlanilhaService service;

    public PlanilhaController(PlanilhaService service) {
        this.service = service;
    }

    @GetMapping("/api/documentos/{documentoId}/planilha")
    public PlanilhaService.Previa previa(@PathVariable long documentoId, @RequestParam(required = false) String aba,
                                         @AuthenticationPrincipal UsuarioAutenticado ua) {
        return service.previa(documentoId, aba, ua);
    }

    @ConsultorOuAdmin
    @Auditavel(value = AcaoAuditoria.PLANILHA_MAPEAR_COLUNAS, idParam = "documentoId")
    @PostMapping("/api/documentos/{documentoId}/planilha/mapeamento")
    public ResponseEntity<Void> mapear(@PathVariable long documentoId, @Valid @RequestBody PlanilhaService.PedidoMapeamento pedido,
                                       @AuthenticationPrincipal UsuarioAutenticado ua) {
        service.mapear(documentoId, pedido, ua);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/api/documentos/{documentoId}/planilha/depara")
    public PlanilhaService.Depara depara(@PathVariable long documentoId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return service.depara(documentoId, ua);
    }

    /** Devolve {"contasAlteradas": n}, que também vai para os detalhes da auditoria. */
    @ConsultorOuAdmin
    @Auditavel(value = AcaoAuditoria.PLANILHA_SALVAR_DEPARA, idParam = "documentoId")
    @PutMapping("/api/documentos/{documentoId}/planilha/depara")
    public ResponseEntity<Map<String, Integer>> associar(@PathVariable long documentoId,
                                                         @Valid @RequestBody List<PlanilhaService.Associacao> associacoes,
                                                         @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.accepted().body(Map.of("contasAlteradas", service.associar(documentoId, associacoes, ua)));
    }

    /** Contas referenciais analíticas do plano (1 Lucro Real, 2 Lucro Presumido) vigente no exercício. */
    @GetMapping("/api/referencial/contas")
    public List<PlanilhaService.ContaReferencial> buscar(@RequestParam String plano, @RequestParam int exercicio,
                                                         @RequestParam(required = false) String busca) {
        return service.buscarReferencial(plano, exercicio, busca);
    }
}
