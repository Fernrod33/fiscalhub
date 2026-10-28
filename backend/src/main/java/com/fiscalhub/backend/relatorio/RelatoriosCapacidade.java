package com.fiscalhub.backend.relatorio;

import com.fiscalhub.backend.capacidade.AnaliseGerencial;
import com.fiscalhub.backend.capacidade.CapacidadeService;
import com.fiscalhub.backend.capacidade.DfcDiretaService;
import com.fiscalhub.backend.capacidade.MetodologiaCapacidade;
import com.fiscalhub.backend.capacidade.ResultadoCapacidade;
import com.fiscalhub.backend.capacidade.ResultadoCapacidade.Criterio;
import com.fiscalhub.backend.contabil.DemonstracaoService;
import com.fiscalhub.backend.relatorio.ModeloRelatorio.*;
import com.fiscalhub.backend.relatorio.MontagemRelatorios.Corpo;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.situacao.SituacaoFiscalService.OrigemValor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.fiscalhub.backend.relatorio.MontagemRelatorios.colunas;
import static com.fiscalhub.backend.relatorio.MontagemRelatorios.secao;

/**
 * Relatório de capacidade de pagamento e dossiê de apoio à revisão da CAPAG, a partir do CapacidadeService (nada é
 * recalculado aqui). Cada bloco leva o rótulo (oficial, estimativa, gerencial) no título, os critérios com fundamento
 * e origem, e os parâmetros das estimativas.
 */
@Component
class RelatoriosCapacidade {

    private static final String ND = "não disponível";
    private final CapacidadeService capacidade;
    private final DemonstracaoService demonstracoes;

    RelatoriosCapacidade(CapacidadeService capacidade, DemonstracaoService demonstracoes) {
        this.capacidade = capacidade;
        this.demonstracoes = demonstracoes;
    }

    Corpo capacidade(long clienteId, UsuarioAutenticado ua) {
        CapacidadeService.Capacidade c = capacidade.capacidade(clienteId, ua);
        Set<Long> documentos = new LinkedHashSet<>();
        List<Secao> secoes = new ArrayList<>();
        ResultadoCapacidade est = metodologia(c, "CAPAG_ESTIMADA");
        ResultadoCapacidade pr = metodologia(c, "PR");
        ResultadoCapacidade ger = metodologia(c, "GERENCIAL");
        if (c.capagOficial() != null) {
            secoes.add(criterios(c.capagOficial(), documentos));
        } else {
            secoes.add(new Secao("CAPAG oficial", "Não disponível — importe a tela de CAPAG do Regularize.", false, null, null));
        }
        secoes.add(criterios(est, documentos));
        secoes.add(secao("Créditos irrecuperáveis (art. 25)", "Nenhuma inscrição na PGFN nos documentos importados.", false, est.itens(),
                colunas("Inscrição", "Valor:MOEDA", "Situação", "Fundamento"), i -> linha(i.id(), i.valor(),
                        i.classe() == null ? ND : i.classe().equals("IRRECUPERAVEL") ? "irrecuperável" : "recuperável",
                        i.observacao() == null ? i.criterios().stream().filter(x -> x.naoDisponivel() != null).map(Criterio::naoDisponivel)
                                .findFirst().orElse("nenhum inciso do art. 25 se aplica") : i.observacao()), null, null));
        secoes.add(criterios(pr, documentos));
        secoes.add(secao("CDAs do Paraná — classe recalculada × oficial", "Nenhuma CDA de ICMS nos documentos importados.", false, pr.itens(),
                colunas("CDA", "Valor:MOEDA", "Índice (média geométrica)", "Classe recalculada", "Classe oficial (RTI)", "Observação"),
                i -> linha(i.id(), i.valor(), Objects.toString(i.indice(), ND), Objects.toString(i.classe(), ND),
                        Objects.toString(i.classeOficial(), "—"), Objects.toString(i.observacao(), "")), null, null));
        secoes.add(criterios(ger, documentos));
        secoes.add(secao("Indicadores gerenciais por exercício", "Sem demonstrações importadas.", false, c.indicadores(),
                colunas("Exercício", "EBITDA:MOEDA", "Caixa operacional:MOEDA", "Liquidez corrente", "Liquidez seca", "Liquidez geral",
                        "Endividamento", "Cobertura de juros", "Ciclo financeiro (dias)"),
                i -> linha(String.valueOf(i.exercicio()), cru(i.ebitda()), cru(i.fco()), nd(i.liquidezCorrente()),
                        nd(i.liquidezSeca()), nd(i.liquidezGeral()), nd(i.endividamento()), nd(i.coberturaJuros()), nd(i.cicloFinanceiro())),
                null, "Análise do consultor — não é critério oficial."));
        if (!c.indicadores().isEmpty()) {
            List<AnaliseGerencial.Indicadores> com = c.indicadores();
            secoes.add(new Secao("EBITDA × caixa operacional", null, false, null, new Grafico(TipoGrafico.BARRAS, "EBITDA × caixa operacional por exercício",
                    Formato.MOEDA, com.stream().map(i -> String.valueOf(i.exercicio())).toList(),
                    List.of(new Serie("EBITDA", com.stream().map(AnaliseGerencial.Indicadores::ebitda).toList()),
                            new Serie("Caixa operacional", com.stream().map(AnaliseGerencial.Indicadores::fco).toList())))));
        }
        secoes.add(parametros(c));
        List<Indicador> sumario = List.of(
                new Indicador("CAPAG oficial", c.capagOficial() == null ? ND : c.capagOficial().classe(), Formato.TEXTO, "Regularize"),
                new Indicador("CAPAG estimada", Objects.toString(est.classe(), ND), Formato.TEXTO, "estimativa do consultor"),
                new Indicador("Classe PR (recálculo)", Objects.toString(pr.classe(), ND), Formato.TEXTO,
                        "oficial no RTI: " + Objects.toString(pr.parametros().get("oficial"), "—")),
                new Indicador("Parcela sustentável", ger.valor() == null ? ND : ger.valor().toPlainString(), Formato.MOEDA, "análise gerencial"));
        List<String> notas = new ArrayList<>();
        notas.addAll(est.alertas());
        normas(c).forEach(n -> notas.add("Norma aplicada: " + n + "."));
        return new Corpo(sumario, secoes, documentos, notas);
    }

    Corpo dossie(long clienteId, UsuarioAutenticado ua) {
        CapacidadeService.Capacidade c = capacidade.capacidade(clienteId, ua);
        MetodologiaCapacidade.Insumos in = capacidade.insumos(clienteId, ua, MetodologiaCapacidade.Simulacao.NENHUMA);
        Set<Long> documentos = new LinkedHashSet<>();
        List<Secao> secoes = new ArrayList<>();
        secoes.add(new Secao("Finalidade", "Documento de apoio ao pedido de revisão da capacidade de pagamento (Portaria PGFN 6.757/2022, "
                + "arts. 27 a 30, com a Portaria PGFN 1.457/2024). As declarações exigidas pela PGFN no pedido (dados cadastrais e "
                + "patrimoniais, partes relacionadas, créditos contra a União, alienação ou oneração de bens) são feitas pelo "
                + "contribuinte e pelo contador; este dossiê reúne as demonstrações e a memória de cálculo para a revisão e a "
                + "assinatura do contador responsável.", false, null, null));
        List<Integer> anos = in.dre().exercicios().stream().map(e -> e.exercicio()).sorted(Comparator.reverseOrder()).limit(2).sorted().toList();
        in.dre().exercicios().stream().filter(e -> anos.contains(e.exercicio())).forEach(e -> documentos.add(e.documentoId()));
        secoes.add(demonstracao("Balanço patrimonial (plano referencial)", in.bp(), anos));
        secoes.add(demonstracao("Demonstração do resultado (plano referencial)", in.dre(), anos));
        for (int ano : anos) {
            DfcDiretaService.Dfc dfc = in.dfcs().get(ano);
            if (dfc == null) {
                secoes.add(new Secao("DFC pelo método direto — " + ano, "Não disponível — importe a ECD de " + ano + " (lançamentos I200/I250).", true, null, null));
                continue;
            }
            documentos.add(dfc.documentoId());
            List<List<String>> linhas = dfc.linhas().stream().map(l -> linha(l.atividade().toLowerCase(), l.rotulo(),
                    l.total().toPlainString())).collect(Collectors.toCollection(ArrayList::new));
            dfc.atividades().forEach((a, v) -> linhas.add(linha(a.toLowerCase(), "Total " + a.toLowerCase(), v.toPlainString())));
            secoes.add(new Secao("DFC pelo método direto — " + ano, dfc.alerta() == null
                    ? "Variação do caixa " + dfc.variacaoDfc().toPlainString() + ", igual à variação dos saldos de caixa no I155."
                    : dfc.alerta(), dfc.alerta() != null, new Tabela(colunas("Atividade", "Linha", "Valor:MOEDA"), linhas,
                    List.of("", "Variação do caixa", dfc.variacaoDfc().toPlainString())), null));
        }
        int curso = in.hoje().getYear();
        secoes.add(new Secao("DFC pelo método direto — exercício em curso (" + curso + ")", in.dfcs().containsKey(curso) ? null
                : "Não disponível — a Portaria pede o exercício em curso até o último trimestre fechado: importe a ECD ou o balancete com "
                + "os lançamentos de " + curso + ".", !in.dfcs().containsKey(curso), null, null));
        ResultadoCapacidade est = metodologia(c, "CAPAG_ESTIMADA");
        secoes.add(criterios(est, documentos));
        secoes.add(secao("Créditos irrecuperáveis (art. 25)", "Nenhuma inscrição na PGFN nos documentos importados.", false, est.itens(),
                colunas("Inscrição", "Valor:MOEDA", "Situação", "Fundamento"), i -> linha(i.id(), i.valor(),
                        i.classe() == null ? ND : i.classe().equals("IRRECUPERAVEL") ? "irrecuperável" : "recuperável",
                        Objects.toString(i.observacao(), "nenhum inciso do art. 25 se aplica")), null, null));
        secoes.add(parametros(c));
        List<Indicador> sumario = List.of(
                new Indicador("CAPAG oficial", c.capagOficial() == null ? ND : c.capagOficial().classe(), Formato.TEXTO, null),
                new Indicador("CAPAG estimada", Objects.toString(est.classe(), ND), Formato.TEXTO, "estimativa do consultor"),
                new Indicador("Valor estimado", est.valor() == null ? ND : est.valor().toPlainString(), Formato.MOEDA,
                        "método: " + c.parametros().capag().metodoConsiderado().replace('_', ' ').toLowerCase()),
                new Indicador("Exercícios", anos.toString(), Formato.TEXTO, "BP, DRE e DFC direta"));
        return new Corpo(sumario, secoes, documentos, new ArrayList<>(est.alertas()));
    }

    private Secao criterios(ResultadoCapacidade r, Set<Long> documentos) {
        r.criterios().forEach(x -> x.origens().stream().filter(Objects::nonNull).forEach(o -> documentos.add(o.documentoId())));
        r.itens().forEach(i -> i.criterios().forEach(x -> x.origens().stream().filter(Objects::nonNull).forEach(o -> documentos.add(o.documentoId()))));
        String texto = (r.classe() == null ? "Classe não calculada" : "Classe " + r.classe()) + (r.valor() == null ? "" : " · valor "
                + Formatos.moeda(r.valor())) + " · " + r.rotuloTexto() + (r.alertas().isEmpty() ? "" : " · " + String.join(" ", r.alertas()));
        return secao(r.titulo() + " — " + r.rotuloTexto(), texto, !r.alertas().isEmpty(), r.criterios(),
                colunas("Critério", "Valor", "Pontos / índice", "Faixa ou cálculo", "Fundamento", "Origem"),
                x -> linha(x.nome(), x.naoDisponivel() != null ? x.naoDisponivel() : Formatos.celula(x.valor(), x.formato()),
                        Objects.toString(x.pontos(), "—"), Objects.toString(x.faixa(), "—"), x.fundamento(),
                        x.origens().stream().filter(Objects::nonNull).map(RelatoriosCapacidade::origem).distinct().collect(Collectors.joining(" + "))),
                null, texto);
    }

    private static Secao parametros(CapacidadeService.Capacidade c) {
        var p = c.parametros();
        List<List<String>> linhas = List.of(
                linha("Faixas A / B / C da CAPAG (CAPAG ÷ dívida)", p.capag().faixaA() + " / " + p.capag().faixaB() + " / " + p.capag().faixaC()),
                linha("Método considerado na classe", p.capag().metodoConsiderado()),
                linha("Exercícios nas médias", String.valueOf(p.capag().exercicios())),
                linha("Realização no PLR (disponível / créditos / estoques / imobilizado)",
                        p.capag().realizacaoDisponivel() + "% / " + p.capag().realizacaoCreditos() + "% / " + p.capag().realizacaoEstoques()
                                + "% / " + p.capag().realizacaoImobilizado() + "%"),
                linha("Parcela sustentável (% do caixa operacional livre)", p.gerencial().percentualFcoLivre() + "%"),
                linha("Última alteração", p.atualizadoPor() == null ? "padrão do sistema" : p.atualizadoPor() + " em " + Formatos.dataHora(p.atualizadoEm())));
        return new Secao("Parâmetros das estimativas (escritório)", "Valores que a norma não fixa: definidos pelo escritório e usados nas estimativas acima.",
                false, new Tabela(colunas("Parâmetro", "Valor"), linhas, null), null);
    }

    private Secao demonstracao(String titulo, DemonstracaoService.Comparativo c, List<Integer> anos) {
        List<String> cols = new ArrayList<>(List.of("Código", "Descrição"));
        anos.forEach(a -> cols.add(a + ":MOEDA"));
        return secao(titulo, "Não disponível — importe a ECD dos exercícios.", false,
                c.linhas().stream().filter(l -> anos.stream().anyMatch(a -> l.valores().get(a) != null && l.valores().get(a).valor() != null
                        && l.valores().get(a).valor().signum() != 0)).toList(), colunas(cols.toArray(String[]::new)),
                l -> {
                    List<String> v = new ArrayList<>(List.of(l.codigo(), l.descricao()));
                    anos.forEach(a -> v.add(l.valores().get(a) == null ? null : cru(l.valores().get(a).valor())));
                    return v;
                }, null, "Saldos no plano de contas referencial da RFB, na convenção do sistema (devedor +, credor −).");
    }

    private static ResultadoCapacidade metodologia(CapacidadeService.Capacidade c, String chave) {
        return c.metodologias().stream().filter(m -> chave.equals(m.metodologia())).findFirst().orElseThrow();
    }

    private static java.util.stream.Stream<String> normas(CapacidadeService.Capacidade c) {
        return c.metodologias().stream().flatMap(m -> m.normas().stream()).distinct();
    }

    private static String origem(OrigemValor o) {
        return MontagemRelatorios.origem(o);
    }

    private static List<String> linha(String... celulas) {
        return Arrays.asList(celulas);
    }

    private static String cru(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    private static String nd(BigDecimal v) {
        return v == null ? ND : v.toPlainString();
    }
}
