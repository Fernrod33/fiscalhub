package com.fiscalhub.backend.capacidade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fiscalhub.backend.capacidade.DfcDiretaService.Dfc;
import com.fiscalhub.backend.capacidade.ResultadoCapacidade.CriterioCapacidade;
import com.fiscalhub.backend.capacidade.ResultadoCapacidade.ItemCapacidade;
import com.fiscalhub.backend.capacidade.ResultadoCapacidade.Rotulo;
import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.relatorio.ModeloRelatorio.Formato;
import com.fiscalhub.backend.situacao.SituacaoFiscalService;
import com.fiscalhub.backend.situacao.SituacaoFiscalService.OrigemValor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static com.fiscalhub.backend.capacidade.ResultadoCapacidade.cru;

/**
 * CAPAG federal estimada pelos dois métodos descritos para a revisão (Portaria PGFN 6.757/2022, arts. 21 e 30, com a
 * 1.457/2024), em 5 anos (art. 21, da regra PGFN_HORIZONTE):
 * <ul>
 *   <li>resultado operacional: GRF (média do EBITDA dos últimos exercícios × anos) + PLR (ativos realizáveis × % de
 *   realização − obrigações trabalhistas, que têm preferência sobre o crédito tributário);</li>
 *   <li>fluxo de caixa: média do caixa operacional da DFC direta × anos − obrigações trabalhistas.</li>
 * </ul>
 * O texto não fixa as fórmulas de GRF e PLR, os percentuais de realização, as faixas A-D nem qual método prevalece (a
 * CAPAG efetiva é calculada pela PGFN, art. 32): tudo isso é parâmetro do escritório e o resultado é ESTIMATIVA.
 * Créditos irrecuperáveis (art. 25) saem da dívida considerada e ficam listados com o fundamento.
 */
@Component
public class CapagEstimada implements MetodologiaCapacidade {

    static final List<String> DEPRECIACAO = List.of("3.01.01.07.01.23", "3.01.01.07.01.24", "3.01.01.09.01.16", "3.01.01.09.01.17");
    static final List<String> RECEITAS_FINANCEIRAS = List.of("3.01.01.05.01.01", "3.01.01.05.01.04", "3.01.01.05.01.05",
            "3.01.01.05.01.08", "3.01.01.05.01.16", "3.01.01.05.01.27", "3.01.01.05.01.28", "3.01.01.05.01.29",
            "3.01.01.05.01.30", "3.01.01.05.01.31", "3.01.01.05.01.32");
    static final List<String> DESPESAS_FINANCEIRAS = List.of("3.01.01.09.01.01", "3.01.01.09.01.04", "3.01.01.09.01.06",
            "3.01.01.09.01.07", "3.01.01.09.01.08", "3.01.01.09.01.15", "3.01.01.09.01.25");
    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private final RegrasCapacidade regras;

    public CapagEstimada(RegrasCapacidade regras) {
        this.regras = regras;
    }

    @Override
    public String chave() {
        return "CAPAG_ESTIMADA";
    }

    /** EBITDA do exercício pelo DRE referencial: LAIR + depreciação/amortização + despesas − receitas financeiras. */
    static BigDecimal ebitda(Insumos in, int ano) {
        BigDecimal lair = Insumos.linha(in.dre(), "3.01", ano);
        if (lair == null) {
            return null;
        }
        return lair.negate().add(Insumos.soma(in.dre(), DEPRECIACAO, ano)).add(Insumos.soma(in.dre(), DESPESAS_FINANCEIRAS, ano))
                .add(Insumos.soma(in.dre(), RECEITAS_FINANCEIRAS, ano)); // receita financeira é crédito (negativo): somar a subtrai
    }

    static BigDecimal fco(Dfc dfc) {
        return dfc == null ? null : dfc.atividades().getOrDefault("OPERACIONAL", BigDecimal.ZERO);
    }

    @Override
    public ResultadoCapacidade calcular(Insumos in) {
        var horizonte = regras.vigente("PGFN_HORIZONTE", in.hoje());
        var irrecRegra = regras.vigente("PGFN_IRRECUPERAVEIS", in.hoje());
        int anos = horizonte.parametros().get("anos").asInt();
        var p = in.parametros().capag();
        List<Integer> exercicios = in.dre().exercicios().stream().map(e -> e.exercicio()).sorted(Comparator.reverseOrder())
                .limit(p.exercicios()).sorted().toList();
        List<CriterioCapacidade> criterios = new ArrayList<>();
        List<String> notas = new ArrayList<>();
        String fundEst = "estimativa do consultor — parâmetro do escritório (o texto da Portaria não fixa)";

        // ---- método 1: resultado operacional (GRF + PLR)
        BigDecimal grf = null;
        BigDecimal plr = null;
        if (exercicios.isEmpty()) {
            criterios.add(CriterioCapacidade.faltando("GRF — geração de resultados futuros", fundEst, "importe a ECD (DRE no plano referencial)"));
            criterios.add(CriterioCapacidade.faltando("PLR — patrimônio líquido realizável", fundEst, "importe a ECD (BP no plano referencial)"));
        } else {
            List<BigDecimal> ebitdas = exercicios.stream().map(a -> ebitda(in, a)).filter(Objects::nonNull).toList();
            BigDecimal media = ebitdas.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(ebitdas.size()), 2, RoundingMode.HALF_EVEN);
            grf = media.max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(anos));
            criterios.add(new CriterioCapacidade("GRF — geração de resultados futuros", cru(grf), Formato.MOEDA, null,
                    "EBITDA médio de " + exercicios + " (" + media.toPlainString() + ") × " + anos + " anos (negativo conta zero)",
                    fundEst + "; prazo de " + anos + " anos: " + horizonte.citacao(), exercicios.stream()
                    .map(a -> Insumos.origem(in.dre(), a, "3.01")).filter(Objects::nonNull).toList(), null));
            int ult = exercicios.getLast();
            BigDecimal disp = saldo(in, "1.01.01", ult), cred = saldo(in, "1.01.02", ult), est = saldo(in, "1.01.03", ult),
                    imob = saldo(in, "1.02.03", ult);
            BigDecimal trab = Optional.ofNullable(Insumos.linha(in.bp(), "2.01.01.01", ult)).orElse(BigDecimal.ZERO).negate();
            BigDecimal realizavel = pct(disp, p.realizacaoDisponivel()).add(pct(cred, p.realizacaoCreditos()))
                    .add(pct(est, p.realizacaoEstoques())).add(pct(imob, p.realizacaoImobilizado()));
            plr = realizavel.subtract(trab).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN);
            criterios.add(new CriterioCapacidade("PLR — patrimônio líquido realizável", cru(plr), Formato.MOEDA, null,
                    "BP " + ult + ": disponível " + disp + " × " + p.realizacaoDisponivel() + "% + créditos " + cred + " × "
                            + p.realizacaoCreditos() + "% + estoques " + est + " × " + p.realizacaoEstoques() + "% + imobilizado "
                            + imob + " × " + p.realizacaoImobilizado() + "% − obrigações trabalhistas " + trab + " (preferência)",
                    fundEst, List.of(Insumos.origem(in.bp(), ult, "1/2")), null));
        }
        BigDecimal resultadoOperacional = grf == null ? null : grf.add(plr);

        // ---- método 2: fluxo de caixa (DFC direta)
        List<Integer> comDfc = exercicios.stream().filter(in.dfcs()::containsKey).toList();
        BigDecimal fluxo = null;
        if (comDfc.isEmpty()) {
            criterios.add(CriterioCapacidade.faltando("Fluxo de caixa (DFC direta)", fundEst, "importe a ECD com os lançamentos (DFC pelo método direto)"));
        } else {
            BigDecimal media = comDfc.stream().map(a -> fco(in.dfcs().get(a))).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(comDfc.size()), 2, RoundingMode.HALF_EVEN);
            BigDecimal trab = Optional.ofNullable(Insumos.linha(in.bp(), "2.01.01.01", comDfc.getLast())).orElse(BigDecimal.ZERO).negate();
            fluxo = media.max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(anos)).subtract(trab).max(BigDecimal.ZERO);
            criterios.add(new CriterioCapacidade("Fluxo de caixa (DFC direta)", cru(fluxo), Formato.MOEDA, null,
                    "caixa operacional médio de " + comDfc + " (" + media.toPlainString() + ") × " + anos + " anos − obrigações trabalhistas "
                            + trab, fundEst + "; DFC direta exigida na revisão: Portaria PGFN 6.757/2022, art. 30, I (redação da 1.457/2024)",
                    comDfc.stream().map(a -> new OrigemValor(in.dfcs().get(a).documentoId(), in.dfcs().get(a).arquivo(), "ECD", null,
                            "I200/I250 (DFC direta " + a + ")", null, null)).toList(), null));
        }

        // ---- dívida consolidada na PGFN e irrecuperáveis (art. 25)
        List<SituacaoFiscalService.DebitoConsolidado> pgfn = in.debitos().stream().filter(d -> d.orgaoCredor() == OrgaoCredor.PGFN).toList();
        List<ItemCapacidade> irrecuperaveis = pgfn.stream().map(d -> irrecuperavel(d, in, irrecRegra)).toList();
        BigDecimal divida = pgfn.stream().map(SituacaoFiscalService.DebitoConsolidado::valorTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal irrec = pgfn.stream().filter(d -> irrecuperaveis.stream().anyMatch(i -> i.id().equals(d.numeroInscricao()) && "IRRECUPERAVEL".equals(i.classe())))
                .map(SituacaoFiscalService.DebitoConsolidado::valorTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal considerada = divida.subtract(irrec);
        criterios.add(new CriterioCapacidade("Dívida consolidada na PGFN", cru(considerada), Formato.MOEDA, null,
                "total " + divida + (irrec.signum() > 0 ? " − irrecuperáveis " + irrec : "") + " (" + pgfn.size() + " inscrições, FGTS incluído se importado)",
                "Portaria PGFN 6.757/2022, art. 24", pgfn.stream().flatMap(d -> d.origens().stream().map(SituacaoFiscalService.OrigemDebito::origem)).toList(), null));

        // ---- valor considerado e classe
        BigDecimal valor = switch (p.metodoConsiderado()) {
            case "RESULTADO_OPERACIONAL" -> resultadoOperacional;
            case "FLUXO_DE_CAIXA" -> fluxo;
            case "MAIOR" -> resultadoOperacional == null || fluxo == null ? null : resultadoOperacional.max(fluxo);
            default -> resultadoOperacional == null || fluxo == null ? null : resultadoOperacional.min(fluxo);
        };
        String classe = null;
        if (valor != null && considerada.signum() > 0) {
            BigDecimal razao = valor.divide(considerada, 4, RoundingMode.HALF_EVEN);
            classe = classe(razao, p);
            criterios.add(new CriterioCapacidade("Classe", razao.toPlainString(), Formato.TEXTO, null, "CAPAG ÷ dívida = " + razao + " → classe " + classe
                    + " (faixas do escritório: A ≥ " + p.faixaA() + "; B ≥ " + p.faixaB() + "; C ≥ " + p.faixaC() + "; D abaixo)",
                    "Portaria PGFN 6.757/2022, art. 24 (classes); faixas numéricas: " + fundEst, List.of(), null));
        } else if (valor != null) {
            classe = "A";
            notas.add("Sem dívida consolidada na PGFN nos documentos importados.");
        }
        notas.add("Método considerado na classe: " + p.metodoConsiderado().replace('_', ' ').toLowerCase()
                + " (parâmetro do escritório). A Portaria exige os dois documentos na revisão, mas não fixa qual prevalece: a CAPAG "
                + "efetiva é calculada pela PGFN (art. 32).");
        notas.add("Resultado operacional = " + (resultadoOperacional == null ? "não disponível" : resultadoOperacional.toPlainString())
                + "; fluxo de caixa = " + (fluxo == null ? "não disponível" : fluxo.toPlainString()) + ".");

        List<String> alertas = new ArrayList<>();
        String oficial = in.capacidadesOficiais().stream().filter(c -> "PGFN".equals(c.fonte())).map(SituacaoFiscalService.CapacidadeOficial::classe)
                .filter(Objects::nonNull).findFirst().orElse(null);
        if (revisaoVantajosa(classe, oficial)) {
            alertas.add("Revisão da CAPAG potencialmente vantajosa: a classe estimada (" + classe + ") é mais favorável ao contribuinte "
                    + "que a oficial (" + oficial + ") — C e D dão acesso a descontos na transação. O efeito no desconto depende do edital "
                    + "(Fase 5). Prazo do pedido: 30 dias da ciência da CAPAG (art. 29).");
        }
        Map<String, Object> parametros = new LinkedHashMap<>();
        parametros.put("horizonteAnos", anos);
        parametros.put("faixaA", p.faixaA());
        parametros.put("faixaB", p.faixaB());
        parametros.put("faixaC", p.faixaC());
        parametros.put("metodoConsiderado", p.metodoConsiderado());
        parametros.put("exercicios", exercicios);
        parametros.put("realizacao", Map.of("disponivel", p.realizacaoDisponivel(), "creditos", p.realizacaoCreditos(),
                "estoques", p.realizacaoEstoques(), "imobilizado", p.realizacaoImobilizado()));
        parametros.put("resultadoOperacional", resultadoOperacional);
        parametros.put("fluxoDeCaixa", fluxo);
        parametros.put("oficial", oficial);
        return ResultadoCapacidade.de("CAPAG_ESTIMADA", "CAPAG federal estimada (dois métodos)", Rotulo.ESTIMATIVA, valor, classe,
                criterios, irrecuperaveis, parametros, List.of(horizonte.citacao(), irrecRegra.citacao(),
                        "Portaria PGFN 6.757/2022, art. 30, I (redação da Portaria PGFN 1.457/2024)"), notas, alertas);
    }

    /** C e D são mais favoráveis ao contribuinte (descontos); entre elas, D é a mais favorável. */
    public static boolean revisaoVantajosa(String estimada, String oficial) {
        return estimada != null && oficial != null && (estimada.equals("C") || estimada.equals("D")) && estimada.compareTo(oficial) > 0;
    }

    public static String classe(BigDecimal razao, ParametrosCapacidade.ParametrosCapag p) {
        return razao.compareTo(p.faixaA()) >= 0 ? "A" : razao.compareTo(p.faixaB()) >= 0 ? "B" : razao.compareTo(p.faixaC()) >= 0 ? "C" : "D";
    }

    /** Art. 25: cada inscrição com os critérios que dá para conferir com o que foi importado. */
    ItemCapacidade irrecuperavel(SituacaoFiscalService.DebitoConsolidado d, Insumos in, RegrasCapacidade.RegraCapacidade regra) {
        JsonNode r = regra.parametros();
        Detalhe det = in.detalhes().get(d.numeroInscricao());
        var cnpj = in.situacoes().get("RFB");
        String fund = regra.citacao();
        List<CriterioCapacidade> c = new ArrayList<>();
        List<String> enquadra = new ArrayList<>();
        if (det == null || det.dataInscricao() == null) {
            c.add(CriterioCapacidade.faltando("Inscrita há mais de 15 anos sem garantia nem suspensão", fund + ", I",
                    "importe o extrato do Regularize com os dados das inscrições"));
        } else {
            BigDecimal anos = ClassificacaoParana.anos(det.dataInscricao(), in.hoje()).setScale(1, RoundingMode.HALF_EVEN);
            boolean semGarantia = det.garantia() == null || det.garantia().toUpperCase().startsWith("SEM");
            boolean sim = anos.compareTo(r.get("inscricaoSemGarantiaAnos").decimalValue()) > 0 && semGarantia && det.suspensaoDesde() == null;
            c.add(new CriterioCapacidade("Inscrita há mais de 15 anos sem garantia nem suspensão", anos + " anos", Formato.TEXTO, null,
                    sim ? "sim" : "não", fund + ", I", det.origens(), null));
            if (sim) {
                enquadra.add("art. 25, I");
            }
            boolean susp = det.suspensaoDesde() != null && ClassificacaoParana.anos(det.suspensaoDesde(), in.hoje())
                    .compareTo(r.get("suspensaoJudicialAnos").decimalValue()) > 0;
            c.add(new CriterioCapacidade("Suspensa por decisão judicial há mais de 10 anos", det.suspensaoDesde() == null ? "sem suspensão"
                    : det.suspensaoDesde().toString(), Formato.TEXTO, null, susp ? "sim" : "não", fund + ", II", det.origens(), null));
            if (susp) {
                enquadra.add("art. 25, II");
            }
        }
        if (cnpj == null) {
            c.add(CriterioCapacidade.faltando("Situação do devedor no CNPJ (falência, recuperação, baixa, inaptidão)", fund + ", III e IV",
                    "importe o extrato do Regularize com a situação cadastral no CNPJ"));
        } else {
            String s = java.text.Normalizer.normalize(cnpj.situacao().toUpperCase(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            boolean naoPresume = contem(r.get("naoPresume"), s); // § 2º
            boolean sim = !naoPresume && (contem(r.get("cnpj"), s) || contem(r.get("devedor"), s.replace(' ', '_')));
            c.add(new CriterioCapacidade("Situação do devedor no CNPJ (falência, recuperação, baixa, inaptidão)", cnpj.situacao(), Formato.TEXTO,
                    null, sim ? "sim" : "não", fund + ", III e IV (§ 2º: liquidação voluntária não presume)", List.of(cnpj.origem()), null));
            if (sim) {
                enquadra.add("art. 25, III/IV");
            }
        }
        c.add(CriterioCapacidade.faltando("Execução fiscal arquivada (art. 40 da LEF) há mais de 3 anos", fund + ", VI",
                "os documentos importados não trazem o arquivamento da execução"));
        String classe = enquadra.isEmpty() ? (c.stream().anyMatch(x -> x.naoDisponivel() != null && !x.nome().startsWith("Execução"))
                ? null : "RECUPERAVEL") : "IRRECUPERAVEL";
        return new ItemCapacidade(d.numeroInscricao(), d.periodo(), cru(d.valorTotal()), null, classe, null, c,
                enquadra.isEmpty() ? null : "irrecuperável: " + String.join(", ", enquadra));
    }

    private static boolean contem(JsonNode lista, String texto) {
        for (JsonNode x : lista) {
            if (texto.contains(x.asText())) {
                return true;
            }
        }
        return false;
    }

    private static BigDecimal saldo(Insumos in, String codigo, int ano) {
        return Optional.ofNullable(Insumos.linha(in.bp(), codigo, ano)).orElse(BigDecimal.ZERO);
    }

    private static BigDecimal pct(BigDecimal v, BigDecimal p) {
        return v.multiply(p).divide(CEM, 2, RoundingMode.HALF_EVEN);
    }
}
