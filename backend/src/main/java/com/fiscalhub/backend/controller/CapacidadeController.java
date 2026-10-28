package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import com.fiscalhub.backend.auditoria.Auditavel;
import com.fiscalhub.backend.capacidade.CapacidadeService;
import com.fiscalhub.backend.capacidade.DfcDiretaService;
import com.fiscalhub.backend.capacidade.MetodologiaCapacidade.Simulacao;
import com.fiscalhub.backend.capacidade.ParametrosCapacidade;
import com.fiscalhub.backend.capacidade.RegrasCapacidade;
import com.fiscalhub.backend.capacidade.ResultadoCapacidade;
import com.fiscalhub.backend.security.ConsultorOuAdmin;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * Capacidade de pagamento (CAPAG oficial e estimada, classificação do PR, análise gerencial), DFC direta, parâmetros das
 * estimativas e de-para da DFC. Ler: todos do escritório; alterar parâmetros e de-para: ADMIN ou CONSULTOR (auditado).
 * Cliente de outro escritório: 404.
 */
@RestController
@RequestMapping("/api")
public class CapacidadeController {

    public record PedidoSimulacao(Set<String> quitar, Set<String> parcelar) {
    }

    public record ItemDePara(@NotBlank String prefixo, @NotBlank String categoria) {
    }

    private final CapacidadeService capacidade;
    private final DfcDiretaService dfc;
    private final ParametrosCapacidade parametros;
    private final RegrasCapacidade regras;

    public CapacidadeController(CapacidadeService capacidade, DfcDiretaService dfc, ParametrosCapacidade parametros, RegrasCapacidade regras) {
        this.capacidade = capacidade;
        this.dfc = dfc;
        this.parametros = parametros;
        this.regras = regras;
    }

    @GetMapping("/clientes/{clienteId}/capacidade")
    public CapacidadeService.Capacidade capacidade(@PathVariable long clienteId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return capacidade.capacidade(clienteId, ua);
    }

    @GetMapping("/clientes/{clienteId}/capacidade/resumo")
    public CapacidadeService.ResumoCapacidade resumo(@PathVariable long clienteId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return capacidade.resumo(clienteId, ua);
    }

    /** Simulação de ações no PR: nada é gravado (só leitura, todos os papéis). */
    @PostMapping("/clientes/{clienteId}/capacidade/pr/simulacao")
    public ResultadoCapacidade simularPr(@PathVariable long clienteId, @RequestBody PedidoSimulacao pedido,
                                         @AuthenticationPrincipal UsuarioAutenticado ua) {
        return capacidade.simularPr(clienteId, new Simulacao(pedido.quitar() == null ? Set.of() : pedido.quitar(),
                pedido.parcelar() == null ? Set.of() : pedido.parcelar()), ua);
    }

    @GetMapping("/clientes/{clienteId}/dfc/exercicios")
    public List<Integer> exerciciosDfc(@PathVariable long clienteId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return dfc.exercicios(clienteId, ua);
    }

    @GetMapping("/clientes/{clienteId}/dfc")
    public DfcDiretaService.Dfc dfc(@PathVariable long clienteId, @RequestParam int exercicio, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return dfc.dfc(clienteId, exercicio, ua);
    }

    @GetMapping("/escritorio/parametros-capacidade")
    public ParametrosCapacidade.ParametrosEscritorio parametros(@AuthenticationPrincipal UsuarioAutenticado ua) {
        return parametros.consultar(ua.escritorioId());
    }

    @PutMapping("/escritorio/parametros-capacidade")
    @ConsultorOuAdmin
    @Auditavel(AcaoAuditoria.PARAMETROS_CAPACIDADE_ALTERAR)
    public ParametrosCapacidade.ParametrosEscritorio alterarParametros(@Valid @RequestBody ParametrosCapacidade.ParametrosEscritorio corpo,
                                                             @AuthenticationPrincipal UsuarioAutenticado ua) {
        return parametros.alterar(corpo, ua);
    }

    @GetMapping("/escritorio/dfc-depara")
    public List<DfcDiretaService.DeParaItem> depara(@AuthenticationPrincipal UsuarioAutenticado ua) {
        return dfc.deparaDoEscritorio(ua);
    }

    @PutMapping("/escritorio/dfc-depara")
    @ConsultorOuAdmin
    @Auditavel(AcaoAuditoria.DFC_DEPARA_ALTERAR)
    public List<DfcDiretaService.DeParaItem> alterarDepara(@Valid @RequestBody ItemDePara item, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return dfc.alterarDepara(item.prefixo(), item.categoria(), ua);
    }

    @DeleteMapping("/escritorio/dfc-depara")
    @ConsultorOuAdmin
    @Auditavel(AcaoAuditoria.DFC_DEPARA_ALTERAR)
    public List<DfcDiretaService.DeParaItem> removerDepara(@RequestParam String prefixo, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return dfc.removerDepara(prefixo, ua);
    }

    /** As regras das normas, com vigência e fonte (para a tela e para conferência). */
    @GetMapping("/capacidade/regras")
    public List<RegrasCapacidade.RegraCapacidade> regras() {
        return regras.todas();
    }
}
