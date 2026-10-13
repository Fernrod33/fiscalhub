package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.dto.SimulacaoRequestDTO;
import com.fiscalhub.backend.dto.SimulacaoResultDTO;
import com.fiscalhub.backend.entity.SimulacaoParcelamento;
import com.fiscalhub.backend.service.SimulacaoParcelamentoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/simulacoes")
@CrossOrigin(origins = "*", maxAge = 3600)
public class SimulacaoParcelamentoController {

    private final SimulacaoParcelamentoService simulacaoParcelamentoService;

    SimulacaoParcelamentoController(SimulacaoParcelamentoService simulacaoParcelamentoService) {
        this.simulacaoParcelamentoService = simulacaoParcelamentoService;
    }

    // ---------------------------------------------------------------
    // Leitura de simulações salvas
    // ---------------------------------------------------------------

    @GetMapping
    public ResponseEntity<List<SimulacaoParcelamento>> getAllSimulacoes() {
        return ResponseEntity.ok(simulacaoParcelamentoService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SimulacaoParcelamento> getSimulacaoById(@PathVariable Long id) {
        return ResponseEntity.ok(simulacaoParcelamentoService.findById(id));
    }

    @GetMapping("/declaracao/{declaracaoId}")
    public ResponseEntity<List<SimulacaoParcelamento>> getByDeclaracao(@PathVariable Long declaracaoId) {
        return ResponseEntity.ok(simulacaoParcelamentoService.findByDeclaracaoId(declaracaoId));
    }

    @GetMapping("/aprovadas/{aprovada}")
    public ResponseEntity<List<SimulacaoParcelamento>> getByAprovada(@PathVariable Boolean aprovada) {
        return ResponseEntity.ok(simulacaoParcelamentoService.findByAprovada(aprovada));
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
    public ResponseEntity<SimulacaoResultDTO> calcular(@Valid @RequestBody SimulacaoRequestDTO request) {
        SimulacaoResultDTO result = simulacaoParcelamentoService.calcular(request);
        return ResponseEntity.ok(result);
    }

    /**
     * Calcula e persiste a simulação no banco de dados, incluindo tabela de parcelas.
     *
     * POST /api/simulacoes/calcular/salvar
     */
    @PostMapping("/calcular/salvar")
    public ResponseEntity<SimulacaoResultDTO> calcularESalvar(@Valid @RequestBody SimulacaoRequestDTO request) {
        SimulacaoResultDTO result = simulacaoParcelamentoService.calcularESalvar(request);
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
    public ResponseEntity<SimulacaoParcelamento> aprovar(@PathVariable Long id) {
        return ResponseEntity.ok(simulacaoParcelamentoService.aprovar(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSimulacao(@PathVariable Long id) {
        simulacaoParcelamentoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
