package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.dto.SimulacaoRequestDTO;
import com.fiscalhub.backend.dto.SimulacaoResultDTO;
import com.fiscalhub.backend.entity.SimulacaoParcelamento;
import com.fiscalhub.backend.service.SimulacaoParcelamentoService;
import com.fiscalhub.backend.security.ConsultorOuAdmin;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/simulacoes")
public class SimulacaoParcelamentoController {

    private final SimulacaoParcelamentoService simulacaoParcelamentoService;

    SimulacaoParcelamentoController(SimulacaoParcelamentoService simulacaoParcelamentoService) {
        this.simulacaoParcelamentoService = simulacaoParcelamentoService;
    }

    // ---------------------------------------------------------------
    // Leitura de simulações salvas
    // ---------------------------------------------------------------

    @GetMapping("/{id}")
    public ResponseEntity<SimulacaoParcelamento> getSimulacaoById(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.ok(simulacaoParcelamentoService.findById(id, ua));
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<SimulacaoParcelamento>> getByCliente(@PathVariable Long clienteId,
                                                                       @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.ok(simulacaoParcelamentoService.findByClienteId(clienteId, ua));
    }

    // ---------------------------------------------------------------
    // Cálculo de simulações
    // ---------------------------------------------------------------

    /**
     * Calcula a simulação de parcelamento sem persistir.
     * Ideal para apresentação de resultados ao usuário antes de confirmar.
     *
     * POST /api/simulacoes/calcular
     */
    @PostMapping("/calcular")
    public ResponseEntity<SimulacaoResultDTO> calcular(@Valid @RequestBody SimulacaoRequestDTO request,
                                                       @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.ok(simulacaoParcelamentoService.calcular(request, ua));
    }

    /**
     * Calcula e persiste a simulação no banco de dados, incluindo tabela de parcelas.
     *
     * POST /api/simulacoes/calcular/salvar
     */
    @PostMapping("/calcular/salvar")
    @ConsultorOuAdmin
    public ResponseEntity<SimulacaoResultDTO> calcularESalvar(@Valid @RequestBody SimulacaoRequestDTO request,
                                                              @AuthenticationPrincipal UsuarioAutenticado ua) {
        SimulacaoResultDTO result = simulacaoParcelamentoService.calcularESalvar(request, ua);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // ---------------------------------------------------------------
    // Ações sobre simulações salvas
    // ---------------------------------------------------------------

    /**
     * Marca uma simulação como aprovada.
     *
     * PATCH /api/simulacoes/{id}/aprovar
     */
    @PatchMapping("/{id}/aprovar")
    @ConsultorOuAdmin
    public ResponseEntity<SimulacaoParcelamento> aprovar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.ok(simulacaoParcelamentoService.aprovar(id, ua));
    }

    @DeleteMapping("/{id}")
    @ConsultorOuAdmin
    public ResponseEntity<Void> deleteSimulacao(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado ua) {
        simulacaoParcelamentoService.delete(id, ua);
        return ResponseEntity.noContent().build();
    }
}
