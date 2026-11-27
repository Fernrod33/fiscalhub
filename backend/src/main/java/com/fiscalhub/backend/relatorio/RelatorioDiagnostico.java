package com.fiscalhub.backend.relatorio;

import com.fiscalhub.backend.pontos.PontosAtencaoService;
import com.fiscalhub.backend.pontos.PontosAtencaoService.PontoAtencao;
import com.fiscalhub.backend.pontos.RegraPontoAtencao.Evidencia;
import com.fiscalhub.backend.pontos.RegraPontoAtencao.Severidade;
import com.fiscalhub.backend.relatorio.ModeloRelatorio.*;
import com.fiscalhub.backend.relatorio.MontagemRelatorios.Corpo;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

import static com.fiscalhub.backend.relatorio.MontagemRelatorios.colunas;
import static com.fiscalhub.backend.relatorio.MontagemRelatorios.secao;

/**
 * Diagnóstico fiscal a partir dos pontos de atenção gravados (nada é reavaliado aqui): sumário por severidade e o valor
 * envolvido, os achados abertos por severidade com fundamento, recomendação e evidências (com a origem), e, se pedido,
 * os resolvidos e os descartados com a justificativa.
 */
@Component
class RelatorioDiagnostico {

    private static final Map<Severidade, String> NOMES = Map.of(Severidade.CRITICA, "Críticos", Severidade.ALTA, "Altos",
            Severidade.MEDIA, "Médios", Severidade.INFO, "Informativos");

    private final PontosAtencaoService pontos;

    RelatorioDiagnostico(PontosAtencaoService pontos) {
        this.pontos = pontos;
    }

    Corpo diagnostico(long clienteId, boolean incluirEncerrados, UsuarioAutenticado ua) {
        List<PontoAtencao> todos = pontos.listar(clienteId, null, null, null, ua);
        List<PontoAtencao> abertos = todos.stream().filter(p -> "NOVO".equals(p.estado()) || "EM_ANALISE".equals(p.estado())).toList();
        Set<Long> documentos = new LinkedHashSet<>();
        todos.forEach(p -> p.evidencias().stream().filter(e -> e.origem() != null).forEach(e -> documentos.add(e.origem().documentoId())));

        List<Indicador> sumario = new ArrayList<>();
        for (Severidade s : Severidade.values()) {
            sumario.add(new Indicador(NOMES.get(s), String.valueOf(abertos.stream().filter(p -> p.severidade() == s).count()), Formato.INTEIRO, "abertos"));
        }
        BigDecimal valor = abertos.stream().map(PontoAtencao::valor).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        sumario.add(new Indicador("Valor envolvido", valor.toPlainString(), Formato.MOEDA, "soma dos achados abertos com valor"));

        List<Secao> secoes = new ArrayList<>();
        for (Severidade s : Severidade.values()) {
            List<PontoAtencao> daSev = abertos.stream().filter(p -> p.severidade() == s).toList();
            if (daSev.isEmpty()) continue;
            secoes.add(secao(NOMES.get(s) + " (" + daSev.size() + ")", null, s == Severidade.CRITICA, daSev,
                    colunas("Regra", "Achado", "Valor:MOEDA", "Por que importa", "O que fazer"),
                    p -> linha(p.regra(), p.titulo() + " — " + p.descricao(), cru(p.valor()), p.fundamento(), p.recomendacao()), null, null));
            List<Object[]> ev = new ArrayList<>();
            daSev.forEach(p -> p.evidencias().forEach(e -> ev.add(new Object[]{p, e})));
            secoes.add(secao("Evidências — " + NOMES.get(s).toLowerCase(), null, false, ev,
                    colunas("Regra", "Evidência", "Valor:MOEDA", "Origem"),
                    x -> linha(((PontoAtencao) x[0]).regra(), ((Evidencia) x[1]).descricao(), cru(((Evidencia) x[1]).valor()), origem((Evidencia) x[1])),
                    null, null));
        }
        if (abertos.isEmpty()) {
            secoes.add(new Secao("Achados abertos", "Nenhum ponto de atenção aberto.", false, null, null));
        }
        if (incluirEncerrados) {
            List<PontoAtencao> encerrados = todos.stream().filter(p -> "RESOLVIDO".equals(p.estado()) || "DESCARTADO".equals(p.estado())).toList();
            secoes.add(secao("Resolvidos e descartados", "Nenhum.", false, encerrados, colunas("Regra", "Achado", "Estado", "Justificativa"),
                    p -> linha(p.regra(), p.titulo(), p.estado().toLowerCase(), justificativa(clienteId, p, ua)), null, null));
        }
        List<String> notas = List.of("Regras da versão " + pontos.versaoRegras() + "; cada achado traz a versão da regra que o gerou.");
        return new Corpo(sumario, secoes, documentos, notas);
    }

    private String justificativa(long clienteId, PontoAtencao p, UsuarioAutenticado ua) {
        var h = pontos.detalhe(clienteId, p.id(), ua).historico();
        return h.isEmpty() ? "" : Objects.toString(h.getLast().comentario(), "") + " (" + h.getLast().usuario() + ")";
    }

    private static String origem(Evidencia e) {
        if (e.origem() == null) return "—";
        var o = e.origem();
        return o.arquivo() + (o.pagina() != null ? ", p. " + o.pagina() : "") + (o.registro() != null ? ", " + o.registro() : "")
                + (o.linha() != null ? ", linha " + o.linha() : "");
    }

    private static List<String> linha(String... c) {
        return Arrays.asList(c);
    }

    private static String cru(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }
}
