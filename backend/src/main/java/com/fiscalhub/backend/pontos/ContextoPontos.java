package com.fiscalhub.backend.pontos;

import com.fiscalhub.backend.capacidade.CapacidadeService;
import com.fiscalhub.backend.capacidade.DfcDiretaService;
import com.fiscalhub.backend.contabil.DemonstracaoService;
import com.fiscalhub.backend.fiscal.ApuracaoService;
import com.fiscalhub.backend.fiscal.ConferenciaEcdEcf;
import com.fiscalhub.backend.parcelamento.SimulacaoService;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.situacao.ConciliacaoService;
import com.fiscalhub.backend.situacao.SituacaoFiscalService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * O que as regras consultam de um cliente, com cache por avaliação (várias regras usam a conciliação, os saldos...).
 * Só repassa aos serviços donos dos dados: nenhuma regra calcula por conta própria. O acesso ao cliente já foi checado
 * por quem criou o contexto.
 */
public final class ContextoPontos {

    /** Os serviços consultados; um bean só, para o contexto não precisar de dez parâmetros. */
    @Component
    public record Servicos(ConciliacaoService conciliacao, SituacaoFiscalService situacao, ApuracaoService apuracoes,
                           CapacidadeService capacidade, SimulacaoService simulacoes, DemonstracaoService demonstracoes,
                           DfcDiretaService dfc, ConferenciaEcdEcf conferencia) {
    }

    private final long clienteId;
    private final UsuarioAutenticado ua;
    private final Servicos s;
    private final Map<String, Object> cache = new HashMap<>();

    ContextoPontos(long clienteId, UsuarioAutenticado ua, Servicos s) {
        this.clienteId = clienteId;
        this.ua = ua;
        this.s = s;
    }

    public long clienteId() {
        return clienteId;
    }

    public UsuarioAutenticado usuario() {
        return ua;
    }

    public Servicos servicos() {
        return s;
    }

    @SuppressWarnings("unchecked")
    private <T> T cache(String chave, Supplier<T> f) {
        return (T) cache.computeIfAbsent(chave, k -> f.get());
    }

    public ConciliacaoService.Conciliacao conciliacao() {
        return cache("conciliacao", () -> s.conciliacao().conciliar(clienteId, null, null, null, ua));
    }

    public List<SituacaoFiscalService.DebitoConsolidado> debitos() {
        return cache("debitos", () -> s.situacao().debitos(clienteId, null, null, ua));
    }

    public List<SituacaoFiscalService.PendenciaFiscal> pendencias() {
        return cache("pendencias", () -> s.situacao().pendencias(clienteId, ua));
    }

    public List<SituacaoFiscalService.ParcelamentoFiscal> parcelamentos() {
        return cache("parcelamentos", () -> s.situacao().parcelamentos(clienteId, ua));
    }

    public List<ApuracaoService.SaldoFiscal> saldos() {
        return cache("saldos", () -> s.apuracoes().saldos(clienteId, ua));
    }

    public List<ApuracaoService.Apuracao> apuracoes() {
        return cache("apuracoes", () -> s.apuracoes().apuracoes(clienteId, null, null, null, ua));
    }

    public CapacidadeService.ResumoCapacidade capacidade() {
        return cache("capacidade", () -> s.capacidade().resumo(clienteId, ua));
    }

    public List<DemonstracaoService.Escrituracao> escrituracoes() {
        return cache("escrituracoes", () -> s.demonstracoes().escrituracoes(clienteId, ua));
    }
}
