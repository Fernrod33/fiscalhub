package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import com.fiscalhub.backend.auditoria.Auditavel;
import com.fiscalhub.backend.pontos.PontosAtencaoService;
import com.fiscalhub.backend.pontos.PontosAtencaoService.*;
import com.fiscalhub.backend.pontos.RegraPontoAtencao.Categoria;
import com.fiscalhub.backend.pontos.RegraPontoAtencao.Severidade;
import com.fiscalhub.backend.security.ConsultorOuAdmin;
import com.fiscalhub.backend.security.LimiteFrequencia;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Pontos de atenção. Ler: todos do escritório; mudar o estado: ADMIN ou CONSULTOR (auditado); parâmetros das regras:
 * ADMIN (auditado). Reavaliar tem limite por usuário. Cliente de outro escritório: 404.
 */
@RestController
@RequestMapping("/api")
public class PontosAtencaoController {

    public record PedidoEstado(@NotBlank String estado, @Size(max = 1000) String comentario) {
    }

    private final PontosAtencaoService pontos;
    private final LimiteFrequencia limite;
    private final int reavaliacoesPorMinuto;

    public PontosAtencaoController(PontosAtencaoService pontos, LimiteFrequencia limite,
                                   @Value("${fiscalhub.pontos.reavaliacoes-por-minuto:10}") int reavaliacoesPorMinuto) {
        this.pontos = pontos;
        this.limite = limite;
        this.reavaliacoesPorMinuto = reavaliacoesPorMinuto;
    }

    @GetMapping("/clientes/{clienteId}/pontos-atencao")
    public List<PontoAtencao> listar(@PathVariable long clienteId, @RequestParam(required = false) Severidade severidade,
                                     @RequestParam(required = false) Categoria categoria, @RequestParam(required = false) String estado,
                                     @AuthenticationPrincipal UsuarioAutenticado ua) {
        return pontos.listar(clienteId, severidade, categoria, estado, ua);
    }

    @GetMapping("/clientes/{clienteId}/pontos-atencao/nao-avaliadas")
    public List<RegraNaoAvaliada> naoAvaliadas(@PathVariable long clienteId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return pontos.naoAvaliadas(clienteId, ua);
    }

    @PostMapping("/clientes/{clienteId}/pontos-atencao/reavaliar")
    public ResultadoAvaliacao reavaliar(@PathVariable long clienteId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        limite.verificar("reavaliações de pontos de atenção", ua.id(), reavaliacoesPorMinuto);
        return pontos.reavaliar(clienteId, ua);
    }

    @GetMapping("/clientes/{clienteId}/pontos-atencao/{pid}")
    public PontoAtencao detalhe(@PathVariable long clienteId, @PathVariable long pid, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return pontos.detalhe(clienteId, pid, ua);
    }

    @PatchMapping("/clientes/{clienteId}/pontos-atencao/{pid}/estado")
    @ConsultorOuAdmin
    @Auditavel(value = AcaoAuditoria.PONTO_ATENCAO_ESTADO, idParam = "pid")
    public PontoAtencao estado(@PathVariable long clienteId, @PathVariable long pid, @Valid @RequestBody PedidoEstado pedido,
                               @AuthenticationPrincipal UsuarioAutenticado ua) {
        return pontos.mudarEstado(clienteId, pid, pedido.estado(), pedido.comentario(), ua);
    }

    @GetMapping("/escritorio/pontos-atencao/resumo")
    public Painel painel(@AuthenticationPrincipal UsuarioAutenticado ua) {
        return pontos.painel(ua);
    }

    @GetMapping("/escritorio/pontos-atencao/regras")
    public List<ConfiguracaoRegra> regras(@AuthenticationPrincipal UsuarioAutenticado ua) {
        return pontos.configuracoes(ua);
    }

    @PutMapping("/escritorio/pontos-atencao/regras/{codigo}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditavel(AcaoAuditoria.PARAMETROS_PONTOS_ALTERAR)
    public ConfiguracaoRegra alterarRegra(@PathVariable String codigo, @RequestBody AlteracaoRegra alteracao,
                                          @AuthenticationPrincipal UsuarioAutenticado ua) {
        return pontos.alterar(codigo, alteracao, ua);
    }
}
