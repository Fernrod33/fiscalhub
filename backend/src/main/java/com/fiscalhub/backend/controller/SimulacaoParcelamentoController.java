package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.dto.SimulacaoRequestDTO;
import com.fiscalhub.backend.dto.SimulacaoResultDTO;
import com.fiscalhub.backend.entity.SimulacaoParcelamento;
import com.fiscalhub.backend.service.SimulacaoParcelamentoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    public ResponseEntity<SimulacaoParcelamento> getSimulacaoById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(simulacaoParcelamentoService.findById(id, authentication.getName()));
    }

    @GetMapping("/declaracao/{declaracaoId}")
    public ResponseEntity<List<SimulacaoParcelamento>> getByDeclaracao(@PathVariable Long declaracaoId,
                                                                       Authentication authentication) {
        return ResponseEntity.ok(simulacaoParcelamentoService.findByDeclaracaoId(declaracaoId, authentication.getName()));
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
                                                       Authentication authentication) {
        return ResponseEntity.ok(simulacaoParcelamentoService.calcular(request, authentication.getName()));
    }

    /**
     * Calcula e persiste a simulação no banco de dados, incluindo tabela de parcelas.
     *
     * POST /api/simulacoes/calcular/salvar
     */
    @PostMapping("/calcular/salvar")
    public ResponseEntity<SimulacaoResultDTO> calcularESalvar(@Valid @RequestBody SimulacaoRequestDTO request,
                                                              Authentication authentication) {
        SimulacaoResultDTO result = simulacaoParcelamentoService.calcularESalvar(request, authentication.getName());
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
    public ResponseEntity<SimulacaoParcelamento> aprovar(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(simulacaoParcelamentoService.aprovar(id, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSimulacao(@PathVariable Long id, Authentication authentication) {
        simulacaoParcelamentoService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
