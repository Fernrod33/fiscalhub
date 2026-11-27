package com.fiscalhub.backend.capacidade;

import com.fiscalhub.backend.capacidade.DfcDiretaService.Dfc;
import com.fiscalhub.backend.capacidade.ResultadoCapacidade.CriterioCapacidade;
import com.fiscalhub.backend.capacidade.ResultadoCapacidade.ItemCapacidade;
import com.fiscalhub.backend.capacidade.ResultadoCapacidade.Rotulo;
import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.relatorio.ModeloRelatorio.Formato;
import com.fiscalhub.backend.situacao.AcrescimosMoratorios;
import com.fiscalhub.backend.situacao.SituacaoFiscalService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static com.fiscalhub.backend.capacidade.ResultadoCapacidade.cru;

/**
 * Análise gerencial (não normativa): indicadores por exercício das demonstrações referenciais e da DFC direta, passivo
 * tributário atualizado até hoje e a parcela mensal sustentável (percentual do caixa operacional livre ÷ 12, parâmetro do
 * escritório). Sempre rotulada "análise do consultor — não é critério oficial".
 */
@Component
public class AnaliseGerencial implements MetodologiaCapacidade {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final BigDecimal DIAS = BigDecimal.valueOf(360);
    private final AcrescimosMoratorios acrescimos;

    public AnaliseGerencial(AcrescimosMoratorios acrescimos) {
        this.acrescimos = acrescimos;
    }

    @Override
    public String chave() {
        return "GERENCIAL";
    }

    /** IndicadoresGerenciais de um exercício (nulos quando falta a linha ou o divisor é zero). */
    public record IndicadoresGerenciais(int exercicio, BigDecimal ebitda, BigDecimal fco, BigDecimal investimento, BigDecimal liquidezCorrente,
                              BigDecimal liquidezSeca, BigDecimal liquidezGeral, BigDecimal endividamento, BigDecimal coberturaJuros,
                              BigDecimal cicloFinanceiro) {
    }

    public static IndicadoresGerenciais indicadores(Insumos in, int ano) {
        Dfc dfc = in.dfcs().get(ano);
        BigDecimal ac = Insumos.linha(in.bp(), "1.01", ano), pc = neg(Insumos.linha(in.bp(), "2.01", ano)), estoques = Insumos.linha(in.bp(), "1.01.03", ano);
        BigDecimal rlp = Insumos.linha(in.bp(), "1.02.01", ano), pnc = neg(Insumos.linha(in.bp(), "2.02", ano)), ativo = Insumos.linha(in.bp(), "1", ano);
        BigDecimal despFin = Insumos.soma(in.dre(), CapagEstimada.DESPESAS_FINANCEIRAS, ano);
        BigDecimal recFin = Insumos.soma(in.dre(), CapagEstimada.RECEITAS_FINANCEIRAS, ano).negate();
        BigDecimal lair = neg(Insumos.linha(in.dre(), "3.01", ano));
        BigDecimal ebit = lair == null ? null : lair.add(despFin).subtract(recFin);
        BigDecimal receita = neg(Insumos.linha(in.dre(), "3.01.01.01.01", ano));
        BigDecimal cpv = Insumos.linha(in.dre(), "3.01.01.03", ano);
        BigDecimal clientes = Insumos.linha(in.bp(), "1.01.02.02", ano), fornecedores = neg(Insumos.linha(in.bp(), "2.01.01.03", ano));
        BigDecimal ciclo = null;
        if (receita != null && cpv != null && cpv.signum() != 0 && receita.signum() != 0 && clientes != null && estoques != null && fornecedores != null) {
            ciclo = clientes.multiply(DIAS).divide(receita, 1, RoundingMode.HALF_EVEN)
                    .add(estoques.multiply(DIAS).divide(cpv.abs(), 1, RoundingMode.HALF_EVEN))
                    .subtract(fornecedores.multiply(DIAS).divide(cpv.abs(), 1, RoundingMode.HALF_EVEN));
        }
        return new IndicadoresGerenciais(ano, CapagEstimada.ebitda(in, ano), CapagEstimada.fco(dfc),
                dfc == null ? null : dfc.atividades().getOrDefault("INVESTIMENTO", BigDecimal.ZERO),
                div(ac, pc), div(ac == null || estoques == null ? null : ac.subtract(estoques), pc),
                div(ac == null ? null : ac.add(Objects.requireNonNullElse(rlp, BigDecimal.ZERO)),
                        pc == null ? null : pc.add(Objects.requireNonNullElse(pnc, BigDecimal.ZERO))),
                pc == null || ativo == null ? null : div(pc.add(Objects.requireNonNullElse(pnc, BigDecimal.ZERO)), ativo),
                div(ebit, despFin.signum() == 0 ? null : despFin), ciclo);
    }

    @Override
    public ResultadoCapacidade calcular(Insumos in) {
        List<Integer> exercicios = new TreeSet<>(in.dre().exercicios().stream().map(e -> e.exercicio()).toList()).stream().toList();
        List<IndicadoresGerenciais> porAno = exercicios.stream().map(a -> indicadores(in, a)).toList();
        List<ItemCapacidade> itens = porAno.stream().map(i -> new ItemCapacidade(String.valueOf(i.exercicio()), "Exercício " + i.exercicio(), cru(i.ebitda()), null, null, null,
                List.of(c("EBITDA", i.ebitda(), Formato.MOEDA), c("Caixa operacional (FCO)", i.fco(), Formato.MOEDA),
                        c("Liquidez corrente", i.liquidezCorrente(), Formato.TEXTO), c("Liquidez seca", i.liquidezSeca(), Formato.TEXTO),
                        c("Liquidez geral", i.liquidezGeral(), Formato.TEXTO), c("Endividamento (passivo ÷ ativo)", i.endividamento(), Formato.TEXTO),
                        c("Cobertura de juros (EBIT ÷ despesas financeiras)", i.coberturaJuros(), Formato.TEXTO),
                        c("Ciclo financeiro (dias)", i.cicloFinanceiro(), Formato.TEXTO)), i.fco() == null ? "FCO: importe a ECD com os lançamentos" : null)).toList();

        // passivo tributário atualizado até hoje
        List<CriterioCapacidade> criterios = new ArrayList<>();
        List<String> notas = new ArrayList<>();
        BigDecimal passivo = BigDecimal.ZERO;
        Map<OrgaoCredor, BigDecimal> porOrgao = new EnumMap<>(OrgaoCredor.class);
        boolean semSelic = false;
        for (SituacaoFiscalService.DebitoConsolidado d : in.debitos()) {
            BigDecimal v = d.valorTotal();
            if (d.orgaoCredor() == OrgaoCredor.RFB && d.valorPrincipal() != null && d.dataVencimento() != null) {
                try {
                    v = d.valorPrincipal().add(acrescimos.calcular(d.valorPrincipal(), d.dataVencimento(), in.hoje()).total());
                } catch (BusinessRuleException e) {
                    semSelic = true;
                }
            }
            if (v != null) {
                passivo = passivo.add(v);
                porOrgao.merge(d.orgaoCredor(), v, BigDecimal::add);
            }
        }
        criterios.add(new CriterioCapacidade("Passivo tributário atualizado", cru(passivo), Formato.MOEDA, null, porOrgao.toString(),
                "RFB: principal + acréscimos de mora (Lei 9.430/96, art. 61) até " + in.hoje() + "; PGFN e PR: valor consolidado ou "
                        + "atualizado informado no documento do órgão, na data dele", in.debitos().stream()
                .flatMap(d -> d.origens().stream().map(SituacaoFiscalService.OrigemDebito::origem)).toList(), null));
        if (semSelic) {
            notas.add("Sem SELIC carregada até hoje: os débitos da RFB entram pelo valor do documento, sem a atualização.");
        }

        // parcela sustentável
        BigDecimal pct = in.parametros().gerencial().percentualFcoLivre();
        IndicadoresGerenciais ultimo = porAno.stream().filter(i -> i.fco() != null).reduce((a, b) -> b).orElse(null);
        BigDecimal parcela = null;
        if (ultimo == null) {
            criterios.add(CriterioCapacidade.faltando("Parcela mensal sustentável", "análise do consultor", "importe a ECD com os lançamentos (DFC direta)"));
        } else {
            BigDecimal livre = ultimo.fco().add(ultimo.investimento());
            parcela = livre.max(BigDecimal.ZERO).multiply(pct).divide(CEM, 2, RoundingMode.HALF_EVEN).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_EVEN);
            criterios.add(new CriterioCapacidade("Parcela mensal sustentável", cru(parcela), Formato.MOEDA, null,
                    pct + "% do caixa operacional livre de " + ultimo.exercicio() + " (FCO " + ultimo.fco() + " + investimento "
                            + ultimo.investimento() + " = " + livre + ") ÷ 12", "análise do consultor — parâmetro do escritório", List.of(), null));
            if (parcela.signum() > 0) {
                long meses = passivo.divide(parcela, 0, RoundingMode.CEILING).longValue();
                criterios.add(new CriterioCapacidade("Prazo de quitação na parcela sustentável", String.valueOf(meses), Formato.INTEIRO, null,
                        meses + " meses (" + passivo + " ÷ " + parcela + ", sem juros)", "análise do consultor", List.of(), null));
            } else {
                notas.add("Caixa operacional livre negativo ou zero no último exercício: não há parcela sustentável pelo caixa.");
            }
        }
        notas.add("IndicadoresGerenciais das demonstrações no plano referencial e da DFC direta. Não substituem a CAPAG nem a classificação estadual.");
        return ResultadoCapacidade.de("GERENCIAL", "Análise gerencial", Rotulo.GERENCIAL, parcela, null, criterios, itens,
                Map.of("percentualFcoLivre", pct), List.of(), notas, List.of());
    }

    private static CriterioCapacidade c(String nome, BigDecimal v, Formato f) {
        return v == null ? CriterioCapacidade.faltando(nome, "análise do consultor", "insumo ausente nas demonstrações importadas")
                : new CriterioCapacidade(nome, cru(v), f, null, null, "análise do consultor", List.of(), null);
    }


    private static BigDecimal neg(BigDecimal v) {
        return v == null ? null : v.negate();
    }

    private static BigDecimal div(BigDecimal a, BigDecimal b) {
        return a == null || b == null || b.signum() == 0 ? null : a.divide(b, 2, RoundingMode.HALF_EVEN);
    }
}
