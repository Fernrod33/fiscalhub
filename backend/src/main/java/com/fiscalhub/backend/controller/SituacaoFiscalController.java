package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.entity.enums.SituacaoDebito;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.situacao.ConciliacaoService;
import com.fiscalhub.backend.situacao.SituacaoFiscalService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

/**
 * Só leitura (tudo vem da importação): débitos, parcelamentos, pendências, capacidade de pagamento oficial, declarado,
 * pago e a conciliação apurado × declarado × pago. Isolamento por escritório nos serviços (cliente de outro = 404).
 */
@RestController
@RequestMapping("/api/clientes/{clienteId}")
public class SituacaoFiscalController {

    private final SituacaoFiscalService situacao;
    private final ConciliacaoService conciliacao;

    public SituacaoFiscalController(SituacaoFiscalService situacao, ConciliacaoService conciliacao) {
        this.situacao = situacao;
        this.conciliacao = conciliacao;
    }

    /** Um débito por dívida (as fontes juntas, com as origens); filtros opcionais por órgão e situação. */
    @GetMapping("/debitos")
    public List<SituacaoFiscalService.Debito> debitos(@PathVariable long clienteId, @RequestParam(required = false) OrgaoCredor orgao,
                                                     @RequestParam(required = false) SituacaoDebito situacao,
                                                     @AuthenticationPrincipal UsuarioAutenticado ua) {
        return this.situacao.debitos(clienteId, orgao, situacao, ua);
    }

    @GetMapping("/parcelamentos")
    public List<SituacaoFiscalService.Parcelamento> parcelamentos(@PathVariable long clienteId,
                                                                  @AuthenticationPrincipal UsuarioAutenticado ua) {
        return situacao.parcelamentos(clienteId, ua);
    }

    @GetMapping("/pendencias")
    public List<SituacaoFiscalService.Pendencia> pendencias(@PathVariable long clienteId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return situacao.pendencias(clienteId, ua);
    }

    @GetMapping("/capacidade-oficial")
    public List<SituacaoFiscalService.CapacidadeOficial> capacidade(@PathVariable long clienteId,
                                                                    @AuthenticationPrincipal UsuarioAutenticado ua) {
        return situacao.capacidades(clienteId, ua);
    }

    @GetMapping("/declaracoes")
    public List<SituacaoFiscalService.Declarado> declaracoes(@PathVariable long clienteId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return situacao.declaracoes(clienteId, ua);
    }

    @GetMapping("/pagamentos")
    public List<SituacaoFiscalService.Pagamento> pagamentos(@PathVariable long clienteId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return situacao.pagamentos(clienteId, ua);
    }

    /** de/ate: períodos de apuração (aaaa-mm), inclusive; status: um dos ConciliacaoService.Status. */
    @GetMapping("/conciliacao")
    public ConciliacaoService.Conciliacao conciliacao(@PathVariable long clienteId,
                                                     @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth de,
                                                     @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth ate,
                                                     @RequestParam(required = false) ConciliacaoService.Status status,
                                                     @AuthenticationPrincipal UsuarioAutenticado ua) {
        return conciliacao.conciliar(clienteId, de, ate, status, ua);
    }

    /** item: o id do item na conciliação (tributo, UF e período, ex.: COFINS-2026-08, ICMS_ST-PR-2026-08). */
    @GetMapping("/conciliacao/{item}")
    public ConciliacaoService.Item item(@PathVariable long clienteId, @PathVariable String item,
                                        @AuthenticationPrincipal UsuarioAutenticado ua) {
        return conciliacao.item(clienteId, item, ua);
    }
}
