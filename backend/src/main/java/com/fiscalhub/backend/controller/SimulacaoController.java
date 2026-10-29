package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import com.fiscalhub.backend.auditoria.Auditavel;
import com.fiscalhub.backend.parcelamento.SimulacaoService;
import com.fiscalhub.backend.parcelamento.SimulacaoService.PedidoParcelamento;
import com.fiscalhub.backend.parcelamento.SimulacaoService.SimulacaoCompleta;
import com.fiscalhub.backend.parcelamento.SimulacaoService.SimulacaoSalva;
import com.fiscalhub.backend.security.ConsultorOuAdmin;
import com.fiscalhub.backend.security.LimiteFrequencia;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Simulações de parcelamento e transação de um cliente. Simular (sem gravar) e ler: todos do escritório; gravar,
 * recalcular e excluir: ADMIN ou CONSULTOR (auditado). Cliente de outro escritório: 404.
 */
@RestController
@RequestMapping("/api/clientes/{clienteId}/simulacoes")
public class SimulacaoController {

    private final SimulacaoService simulacoes;
    private final LimiteFrequencia limite;
    private final int consultasPorMinuto;

    public SimulacaoController(SimulacaoService simulacoes, LimiteFrequencia limite,
                               @Value("${fiscalhub.capacidade.consultas-por-minuto:60}") int consultasPorMinuto) {
        this.simulacoes = simulacoes;
        this.limite = limite;
        this.consultasPorMinuto = consultasPorMinuto;
    }

    /** A simulação recalcula a capacidade (relê arquivos do storage): mesmo limite das consultas de capacidade. */
    private void limitar(UsuarioAutenticado ua) {
        limite.verificar("simulações de parcelamento", ua.id(), consultasPorMinuto);
    }

    @PostMapping("/calcular")
    public SimulacaoCompleta simular(@PathVariable long clienteId, @Valid @RequestBody PedidoParcelamento pedido,
                                     @AuthenticationPrincipal UsuarioAutenticado ua) {
        limitar(ua);
        return simulacoes.simular(clienteId, pedido, ua);
    }

    @PostMapping
    @ConsultorOuAdmin
    @Auditavel(AcaoAuditoria.SIMULACAO_CRIAR)
    public SimulacaoCompleta salvar(@PathVariable long clienteId, @Valid @RequestBody PedidoParcelamento pedido,
                                    @AuthenticationPrincipal UsuarioAutenticado ua) {
        limitar(ua);
        return simulacoes.salvar(clienteId, pedido, ua);
    }

    @GetMapping
    public List<SimulacaoSalva> listar(@PathVariable long clienteId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return simulacoes.listar(clienteId, ua);
    }

    /** A simulação salva mais recente com recomendação (cartão da visão geral); 204 sem nenhuma. */
    @GetMapping("/melhor")
    public ResponseEntity<SimulacaoSalva> melhor(@PathVariable long clienteId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        SimulacaoSalva s = simulacoes.melhor(clienteId, ua);
        return s == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(s);
    }

    @GetMapping("/{id}")
    public SimulacaoCompleta detalhe(@PathVariable long clienteId, @PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return simulacoes.detalhe(clienteId, id, ua);
    }

    /** Nova versão com as regras e os índices de hoje (mesma data-base e débitos); a anterior fica no histórico. */
    @PostMapping("/{id}/recalcular")
    @ConsultorOuAdmin
    @Auditavel(AcaoAuditoria.SIMULACAO_RECALCULAR)
    public SimulacaoCompleta recalcular(@PathVariable long clienteId, @PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado ua) {
        limitar(ua);
        return simulacoes.recalcular(clienteId, id, ua);
    }

    @DeleteMapping("/{id}")
    @ConsultorOuAdmin
    @Auditavel(value = AcaoAuditoria.SIMULACAO_EXCLUIR, idParam = "id")
    public ResponseEntity<Void> excluir(@PathVariable long clienteId, @PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado ua) {
        simulacoes.excluir(clienteId, id, ua);
        return ResponseEntity.noContent().build();
    }
}
