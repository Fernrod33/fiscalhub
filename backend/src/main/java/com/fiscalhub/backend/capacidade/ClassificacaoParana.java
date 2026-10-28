package com.fiscalhub.backend.capacidade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fiscalhub.backend.capacidade.ResultadoCapacidade.Criterio;
import com.fiscalhub.backend.capacidade.ResultadoCapacidade.Item;
import com.fiscalhub.backend.capacidade.ResultadoCapacidade.Rotulo;
import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.entity.enums.SituacaoDebito;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.relatorio.ModeloRelatorio.Formato;
import com.fiscalhub.backend.situacao.SituacaoFiscalService;
import com.fiscalhub.backend.situacao.SituacaoFiscalService.OrigemValor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.fiscalhub.backend.capacidade.ResultadoCapacidade.cru;

/**
 * Classificação do Paraná pela Resolução Conjunta PGE/SEFA 01/2025 (regras da tabela, vigentes na data do cálculo):
 * cada CDA de ICMS pela média geométrica dos 6 índices do art. 3º (classe do art. 4º, ajuste do art. 5º com parcelamento
 * ativo) e o devedor pela soma dos índices do art. 7º (classe do art. 8º). Só recalcula com dados importados: insumo
 * faltando deixa o critério "não disponível — importe X" e a classe sem cálculo. A simulação (CDAs quitadas ou
 * parceladas) recalcula sem alterar dado importado.
 */
@Component
public class ClassificacaoParana implements MetodologiaCapacidade {

    private static final BigDecimal DIAS_ANO = new BigDecimal("365.25");
    private final RegrasCapacidade regras;

    public ClassificacaoParana(RegrasCapacidade regras) {
        this.regras = regras;
    }

    @Override
    public String chave() {
        return "PR";
    }

    /** Uma CDA classificada (para o critério das CDAs do devedor e para a tela). */
    record Cda(String numero, String classe, Item item) {
    }

    @Override
    public ResultadoCapacidade calcular(Insumos in) {
        LocalDate data = in.dataCalculoPr();
        var regraCda = regras.vigente("PR_CDA_INDICES", data);
        var regraClasseCda = regras.vigente("PR_CDA_CLASSES", data);
        var regraDevedor = regras.vigente("PR_DEVEDOR_INDICES", data);
        var regraClasse = regras.vigente("PR_DEVEDOR_CLASSES", data);
        Simulacao sim = in.simulacao();
        var cadastro = in.situacoes().get("SEFA_PR");
        String situacao = cadastro == null ? null : normalizar(cadastro.situacao());

        List<SituacaoFiscalService.DebitoConsolidado> cdas = in.debitos().stream()
                .filter(d -> d.orgaoCredor() == OrgaoCredor.PGE_PR && d.numeroInscricao() != null
                        && (d.tributo() == TipoTributo.ICMS || d.tributo() == TipoTributo.ICMS_ST))
                .filter(d -> !sim.quitar().contains(d.numeroInscricao())).toList();
        List<Cda> classificadas = cdas.stream().map(d -> cda(d, in, situacao, regraCda, regraClasseCda, data)).toList();

        List<Criterio> criterios = new ArrayList<>();
        String fund7 = regraDevedor.citacao();
        // I — situação cadastral
        if (cadastro == null) {
            criterios.add(Criterio.faltando("Situação cadastral", fund7 + ", I", "importe a conta corrente do ICMS com a situação do CAD/ICMS"));
        } else {
            JsonNode pts = regraDevedor.parametros().get("situacaoCadastral").get(situacao);
            criterios.add(new Criterio("Situação cadastral", cadastro.situacao(), Formato.TEXTO, pts == null ? null : pts.asText(),
                    cadastro.situacao(), fund7 + ", I", List.of(cadastro.origem()), pts == null ? "não disponível — situação \""
                    + cadastro.situacao() + "\" fora das faixas da norma" : null));
        }
        // II — tempo de inatividade
        if (cadastro == null) {
            criterios.add(Criterio.faltando("Tempo de inatividade", fund7 + ", II", "importe a conta corrente do ICMS com a situação do CAD/ICMS"));
        } else if (situacao.startsWith("ATIVA")) {
            criterios.add(new Criterio("Tempo de inatividade", "empresa ativa", Formato.TEXTO, "4", "empresa ativa", fund7 + ", II",
                    List.of(cadastro.origem()), null));
        } else if (cadastro.desde() == null) {
            criterios.add(Criterio.faltando("Tempo de inatividade", fund7 + ", II", "o documento não traz desde quando a empresa está inativa"));
        } else {
            BigDecimal anos = anos(cadastro.desde(), data);
            criterios.add(new Criterio("Tempo de inatividade", anos.toPlainString(), Formato.TEXTO,
                    faixaSemAtiva(regraDevedor.parametros().get("inatividadeAnos"), anos).toPlainString(),
                    anos.toPlainString() + " anos", fund7 + ", II", List.of(cadastro.origem()), null));
        }
        // III — índice de endividamento
        var fat = in.faturamento();
        BigDecimal exigivel = cdas.stream().filter(d -> exigivel(d) && !sim.parcelar().contains(d.numeroInscricao()))
                .map(SituacaoFiscalService.DebitoConsolidado::valorTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (fat == null || !fat.faltando().isEmpty() || fat.valor() == null || fat.valor().signum() <= 0) {
            criterios.add(Criterio.faltando("Índice de endividamento", fund7 + ", III", "importe a EFD ICMS/IPI dos meses "
                    + (fat == null ? "dos 12 meses anteriores ao cálculo" : fat.faltando().stream().map(m -> String.format("%02d/%d",
                    m.getMonthValue(), m.getYear())).toList()) + " (faturamento declarado em 12 meses)"));
        } else {
            BigDecimal razao = exigivel.divide(fat.valor(), 6, RoundingMode.HALF_EVEN);
            List<OrigemValor> origens = new ArrayList<>(fat.origens());
            cdas.stream().filter(this::exigivel).forEach(d -> d.origens().forEach(o -> origens.add(o.origem())));
            criterios.add(new Criterio("Índice de endividamento", razao.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_EVEN)
                    .toPlainString(), Formato.PERCENTUAL, regraDevedor.faixa("endividamento", razao).toPlainString(),
                    "dívida exigível " + exigivel.toPlainString() + " ÷ faturamento " + fat.valor().toPlainString() + " ("
                            + String.format("%02d/%d", fat.de().getMonthValue(), fat.de().getYear()) + " a "
                            + String.format("%02d/%d", fat.ate().getMonthValue(), fat.ate().getYear()) + "); "
                            + regraDevedor.rotuloFaixa("endividamento", razao), fund7 + ", III", origens, null));
        }
        // IV — indicativo de pagamento: último recolhimento de ICMS (GR-PR) até a data do cálculo
        Optional<SituacaoFiscalService.PagamentoConsolidado> ultimo = in.pagamentos().stream()
                .filter(p -> "GR_PR".equals(p.documento()) && p.dataPagamento() != null && !p.dataPagamento().isAfter(data))
                .max(Comparator.comparing(SituacaoFiscalService.PagamentoConsolidado::dataPagamento));
        if (ultimo.isEmpty()) {
            criterios.add(Criterio.faltando("Indicativo de pagamento", fund7 + ", IV", "importe a relação de pagamentos GR-PR"));
        } else {
            BigDecimal dias = BigDecimal.valueOf(ChronoUnit.DAYS.between(ultimo.get().dataPagamento(), data));
            criterios.add(new Criterio("Indicativo de pagamento", dias.toPlainString(), Formato.INTEIRO,
                    regraDevedor.faixa("pagamentoDias", dias).toPlainString(), "último recolhimento em "
                    + ultimo.get().dataPagamento() + " (" + dias + " dias); " + regraDevedor.rotuloFaixa("pagamentoDias", dias),
                    fund7 + ", IV", ultimo.get().origens(), null));
        }
        // V — classificação das CDAs
        JsonNode cdasRegra = regraDevedor.parametros().get("cdas");
        if (classificadas.isEmpty()) {
            criterios.add(new Criterio("Classificação das CDAs", "sem débitos inscritos", Formato.TEXTO,
                    cdasRegra.get("semDebitos").asText(), "sem débitos inscritos em dívida ativa", fund7 + ", V", List.of(), null));
        } else if (classificadas.stream().anyMatch(c -> c.classe() == null)) {
            criterios.add(Criterio.faltando("Classificação das CDAs", fund7 + ", V",
                    "importe a conta corrente do ICMS com o detalhamento da dívida ativa (inscrição, origem, ajuizamento)"));
        } else {
            long ab = classificadas.stream().filter(c -> "A".equals(c.classe()) || "B".equals(c.classe())).count();
            BigDecimal pct = BigDecimal.valueOf(ab).divide(BigDecimal.valueOf(classificadas.size()), 6, RoundingMode.HALF_EVEN);
            String pontos = null;
            for (JsonNode f : cdasRegra.get("faixas")) {
                if (pct.compareTo(f.get("acimaDe").decimalValue()) > 0 || f.get("acimaDe").decimalValue().signum() == 0) {
                    pontos = f.get("indice").asText();
                    break;
                }
            }
            criterios.add(new Criterio("Classificação das CDAs", pct.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_EVEN)
                    .toPlainString(), Formato.PERCENTUAL, pontos, ab + " de " + classificadas.size() + " CDAs em classe A ou B",
                    fund7 + ", V", List.of(), null));
        }

        boolean completo = criterios.stream().allMatch(c -> c.naoDisponivel() == null);
        BigDecimal soma = completo ? criterios.stream().map(c -> new BigDecimal(c.pontos())).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        String classe = soma == null ? null : classeDevedor(regraClasse.parametros(), soma);
        String oficial = in.capacidadesOficiais().stream().filter(c -> "SEFA_PR".equals(c.fonte())).map(SituacaoFiscalService.CapacidadeOficial::classe)
                .filter(Objects::nonNull).findFirst().orElse(null);
        List<String> notas = new ArrayList<>(List.of(
                "Recalculado pelo FiscalHub com os documentos importados; a classificação oficial é a do Portal Receita/PR (RTI).",
                "Data do cálculo: " + data + " (posição da conta corrente do ICMS). Faturamento: C190 das saídas (CFOP 5, 6 e 7) "
                        + "da EFD ICMS/IPI dos 12 últimos períodos cuja EFD já venceu na data do cálculo.",
                "Dívida exigível: CDAs de ICMS inscritas, sem parcelamento ou suspensão (o parcelamento suspende a exigibilidade)."));
        if (!sim.vazia()) {
            notas.addFirst("SIMULAÇÃO — CDAs quitadas: " + sim.quitar() + "; parceladas: " + sim.parcelar() + ". Não altera os dados importados.");
        }
        List<String> alertas = new ArrayList<>();
        if (classe != null && oficial != null && !classe.equals(oficial)) {
            alertas.add("A classe recalculada (" + classe + ") difere da oficial no RTI (" + oficial + "): confira a data e os insumos.");
        }
        Map<String, Object> parametros = new LinkedHashMap<>();
        parametros.put("dataCalculo", data.toString());
        parametros.put("oficial", oficial);
        parametros.put("simulacao", sim.vazia() ? null : Map.of("quitar", sim.quitar(), "parcelar", sim.parcelar()));
        return new ResultadoCapacidade("PR", "Classificação do Paraná (PGE/SEFA)", Rotulo.ESTIMATIVA,
                sim.vazia() ? "recálculo pela Resolução Conjunta PGE/SEFA 01/2025 — não é a classificação oficial"
                        : "simulação — não altera os dados importados nem a classificação oficial",
                soma, classe, criterios, classificadas.stream().map(Cda::item).toList(), parametros,
                List.of(regraCda.citacao(), regraClasseCda.citacao(), fund7, regraClasse.citacao()), notas, alertas);
    }

    private boolean exigivel(SituacaoFiscalService.DebitoConsolidado d) {
        return d.situacao() == SituacaoDebito.INSCRITO_DIVIDA_ATIVA || d.situacao() == SituacaoDebito.EM_ABERTO;
    }

    /** CDA de ICMS: 6 índices do art. 3º, média geométrica (art. 4º) e o ajuste por parcelamento ativo (art. 5º). */
    Cda cda(SituacaoFiscalService.DebitoConsolidado d, Insumos in, String situacao, RegrasCapacidade.Regra regra,
            RegrasCapacidade.Regra classes, LocalDate data) {
        Detalhe det = in.detalhes().get(d.numeroInscricao());
        boolean parcelarSimulado = in.simulacao().parcelar().contains(d.numeroInscricao());
        String fund = regra.citacao();
        List<Criterio> c = new ArrayList<>();
        JsonNode p = regra.parametros();
        if (det == null || det.dataInscricao() == null || det.origemDivida() == null || det.ajuizamento() == null || det.parcelamentos() == null) {
            String importe = "importe a conta corrente do ICMS com o detalhamento da dívida ativa";
            c.add(Criterio.faltando("Dados da CDA", fund, importe));
            return new Cda(d.numeroInscricao(), null, new Item(d.numeroInscricao(), d.periodo(), cru(d.valorTotal()), null, null,
                    det == null ? null : det.classeOficial(), c, "não disponível — " + importe));
        }
        int parcelamentos = det.parcelamentos() + (parcelarSimulado ? 1 : 0);
        BigDecimal anos = anos(det.dataInscricao(), data);
        List<BigDecimal> indices = new ArrayList<>();
        indices.add(add(c, "Parcelamentos", String.valueOf(parcelamentos), regra.faixa("parcelamentos", BigDecimal.valueOf(parcelamentos)),
                fund + ", I", det.origens()));
        indices.add(add(c, "Tempo de inscrição (anos)", anos.toPlainString(), regra.faixa("tempoInscricaoAnos", anos), fund + ", II", det.origens()));
        BigDecimal origem = p.get("origem").get(det.origemDivida().toLowerCase().contains("declarado") ? "IMPOSTO_DECLARADO" : "LANCAMENTO_DE_OFICIO").decimalValue();
        indices.add(add(c, "Origem", det.origemDivida(), origem, fund + ", III", det.origens()));
        indices.add(add(c, "Valor", cru(d.valorTotal()), regra.faixa("valor", d.valorTotal()), fund + ", IV",
                d.origens().stream().map(SituacaoFiscalService.OrigemDebito::origem).toList()));
        String aj = det.ajuizamento().toUpperCase();
        BigDecimal ajuizamento = p.get("ajuizamento").get(aj.startsWith("DESIST") ? "DESISTENCIA" : aj.startsWith("NÃO") || aj.startsWith("NAO") ? "NAO_AJUIZADA" : "AJUIZADA").decimalValue();
        indices.add(add(c, "Ajuizamento", det.ajuizamento(), ajuizamento, fund + ", V", det.origens()));
        JsonNode sit = situacao == null ? null : p.get("situacaoCadastral").get(situacao);
        if (sit == null) {
            c.add(Criterio.faltando("Situação cadastral", fund + ", VI", "importe a conta corrente do ICMS com a situação do CAD/ICMS"));
            return new Cda(d.numeroInscricao(), null, new Item(d.numeroInscricao(), d.periodo(), cru(d.valorTotal()), null, null,
                    det.classeOficial(), c, "não disponível — situação cadastral"));
        }
        indices.add(add(c, "Situação cadastral do devedor", situacao, sit.decimalValue(), fund + ", VI", List.of()));
        BigDecimal media = mediaGeometrica(indices);
        String classe = classeCda(classes.parametros(), media);
        String obs = null;
        Optional<SituacaoFiscalService.ParcelamentoFiscal> parc = in.parcelamentos().stream()
                .filter(x -> x.orgao() == OrgaoCredor.PGE_PR && !"RESCINDIDO".equals(x.situacao()) && x.debitos().stream()
                        .anyMatch(n -> n.contains(d.numeroInscricao()))).findFirst();
        if (parcelarSimulado) {
            obs = "parcelamento simulado (0% pago): mantém a classe do índice (art. 5º, III)";
        } else if (parc.isPresent() && parc.get().parcelasPagas() != null && parc.get().parcelasTotal() != null && parc.get().parcelasTotal() > 0) {
            BigDecimal pagas = BigDecimal.valueOf(parc.get().parcelasPagas()).divide(BigDecimal.valueOf(parc.get().parcelasTotal()), 6, RoundingMode.HALF_EVEN);
            String ajustada = classeParcelamento(classes.parametros(), pagas);
            obs = "parcelamento " + parc.get().numero() + ": " + parc.get().parcelasPagas() + " de " + parc.get().parcelasTotal()
                    + " pagas (" + pagas.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_EVEN) + "%) — "
                    + (ajustada == null ? "abaixo de 30%: vale o índice" : "classe " + ajustada + " pelo art. 5º");
            classe = ajustada == null ? classe : ajustada;
        }
        return new Cda(d.numeroInscricao(), classe, new Item(d.numeroInscricao(), d.periodo(), cru(d.valorTotal()), media.toPlainString(),
                classe, det.classeOficial(), c, obs));
    }

    private static BigDecimal add(List<Criterio> c, String nome, String valor, BigDecimal indice, String fundamento, List<OrigemValor> origens) {
        c.add(new Criterio(nome, valor, Formato.TEXTO, indice.toPlainString(), null, fundamento, origens, null));
        return indice;
    }

    /** Média geométrica com 6 casas (arredondamento par), como a norma compara com 6 casas. */
    public static BigDecimal mediaGeometrica(List<BigDecimal> indices) {
        double soma = indices.stream().mapToDouble(x -> Math.log(x.doubleValue())).sum();
        return BigDecimal.valueOf(Math.exp(soma / indices.size())).setScale(6, RoundingMode.HALF_EVEN);
    }

    public static String classeCda(JsonNode classes, BigDecimal media) {
        for (JsonNode c : classes.get("classes")) {
            if (media.compareTo(c.get("acimaDe").decimalValue()) > 0) {
                return c.get("classe").asText();
            }
        }
        return "D";
    }

    /** Art. 5º: classe pelo percentual pago, ou null (abaixo do mínimo: vale o índice). */
    public static String classeParcelamento(JsonNode classes, BigDecimal pagas) {
        for (JsonNode f : classes.get("parcelamentoAtivo")) {
            if (pagas.compareTo(f.get("pagasMinimo").decimalValue()) >= 0) {
                return f.get("classe").asText();
            }
        }
        return null;
    }

    /** Art. 8º: A acima de X; as demais a partir de um mínimo. */
    public static String classeDevedor(JsonNode regra, BigDecimal soma) {
        for (JsonNode c : regra.get("classes")) {
            if (c.has("acimaDe") ? soma.compareTo(c.get("acimaDe").decimalValue()) > 0 : soma.compareTo(c.get("minimo").decimalValue()) >= 0) {
                return c.get("classe").asText();
            }
        }
        return "D";
    }

    private static BigDecimal faixaSemAtiva(JsonNode faixas, BigDecimal anos) {
        for (JsonNode f : faixas) {
            if (f.has("ativa")) {
                continue;
            }
            if (f.get("ate").isNull() || anos.compareTo(f.get("ate").decimalValue()) <= 0) {
                return f.get("indice").decimalValue();
            }
        }
        throw new IllegalStateException("faixa de inatividade sem limite aberto");
    }

    static BigDecimal anos(LocalDate de, LocalDate ate) {
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(de, ate)).divide(DIAS_ANO, 6, RoundingMode.HALF_EVEN);
    }

    /** "Ativa CEC", "ATIVA COM ESTABELECIMENTOS CANCELADOS" -> ATIVA_CEC; "Baixada" -> BAIXADA. */
    static String normalizar(String situacao) {
        String s = java.text.Normalizer.normalize(situacao.toUpperCase(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        if (s.startsWith("ATIVA")) {
            return s.contains("CEC") || s.contains("CANCELAD") ? "ATIVA_CEC" : "ATIVA";
        }
        for (String k : List.of("BAIXADA", "PARALISADA", "CANCELADA")) {
            if (s.startsWith(k)) {
                return k;
            }
        }
        return s.contains("NAO CADASTRADO") ? "NAO_CADASTRADO" : s;
    }
}
