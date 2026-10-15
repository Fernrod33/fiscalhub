package com.fiscalhub.backend.fiscal;

import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.importacao.ContextoProcessamento;
import com.fiscalhub.backend.importacao.Escrituracoes;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import com.fiscalhub.backend.fiscal.RepositorioApuracao.Apuracao;
import com.fiscalhub.backend.fiscal.RepositorioApuracao.Origem;
import com.fiscalhub.backend.fiscal.RepositorioApuracao.SaldoFiscal;
import com.fiscalhub.backend.sped.LayoutSped;
import com.fiscalhub.backend.sped.LeituraSped;
import com.fiscalhub.backend.sped.RegistroSped;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EFD-Contribuições: regime (0110), créditos (M100, M500), contribuição apurada e a recolher (M200/M210,
 * M600/M610) e controle de créditos (1100/1500). Os blocos A, C, D, F e os demais são pulados sem virar campos.
 * Uma apuração por tributo e parte do regime (não cumulativa e cumulativa, como no M200/M600). Leiautes e fonte dos
 * campos: resources/sped/efd-contribuicoes-leiautes.properties (Guia Prático 1.35).
 */
final class LeituraEfdContribuicoes extends LeituraSped {

    private static final Set<String> LIDOS = Set.of("0110", "M100", "M200", "M210", "M500", "M600", "M610", "1100", "1500");
    /** Tabela 4.3.5 do Guia Prático (código da contribuição social apurada). */
    private static final Map<String, String> CONTRIBUICOES = Map.ofEntries(
            Map.entry("01", "Não cumulativa à alíquota básica"), Map.entry("02", "Não cumulativa a alíquotas diferenciadas"),
            Map.entry("03", "Não cumulativa por unidade de medida de produto"),
            Map.entry("04", "Não cumulativa à alíquota básica – atividade imobiliária"),
            Map.entry("31", "Apurada por substituição tributária"),
            Map.entry("32", "Apurada por substituição tributária – vendas à Zona Franca de Manaus"),
            Map.entry("51", "Cumulativa à alíquota básica"), Map.entry("52", "Cumulativa a alíquotas diferenciadas"),
            Map.entry("53", "Cumulativa por unidade de medida de produto"),
            Map.entry("54", "Cumulativa à alíquota básica – atividade imobiliária"),
            Map.entry("71", "De SCP – incidência não cumulativa"), Map.entry("72", "De SCP – incidência cumulativa"),
            Map.entry("99", "PIS/Pasep – folha de salários"));
    private static final Map<String, String> DEDUCOES = Map.of(
            "VL_TOT_CRED_DESC", "Créditos descontados, apurados no período",
            "VL_TOT_CRED_DESC_ANT", "Créditos descontados, apurados em períodos anteriores",
            "VL_RET_NC", "Retenções na fonte", "VL_RET_CUM", "Retenções na fonte",
            "VL_OUT_DED_NC", "Outras deduções", "VL_OUT_DED_CUM", "Outras deduções");
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");
    /** COD_CONT da tabela 4.3.5 somados no M200/M600 (o próprio guia põe o 32 nos dois). */
    private static final Set<String> NAO_CUMULATIVO = Set.of("01", "02", "03", "04", "32", "71");
    private static final Set<String> CUMULATIVO = Set.of("31", "32", "51", "52", "53", "54", "72");

    /** O que o PIS e a COFINS têm de igual: registros e campos de cada um. */
    private record Tributo(String nome, String creditos, String consolidacao, String detalhe, String controle,
                           String campoBase, String saldoCredito) {
    }

    private static final Tributo PIS = new Tributo("PIS", "M100", "M200", "M210", "1100", "VL_BC_PIS", "CREDITO_PIS");
    private static final Tributo COFINS = new Tributo("COFINS", "M500", "M600", "M610", "1500", "VL_BC_COFINS", "CREDITO_COFINS");

    private final RepositorioApuracao apuracoes;
    private final List<RegistroSped> lidos = new ArrayList<>();
    private String regime;

    LeituraEfdContribuicoes(DocumentoFiscal documento, ContextoProcessamento contexto, Escrituracoes escrituracoes,
                            RepositorioApuracao apuracoes, LayoutSped layout) {
        super("EFD_CONTRIBUICOES", "EFD-Contribuições", layout, LIDOS, documento, contexto, escrituracoes);
        this.apuracoes = apuracoes;
    }

    /** |0000|COD_VER|... */
    @Override
    protected String versao(String registro, String[] campos) {
        return "0000".equals(registro) && campos.length > 2 ? campos[2].trim() : null;
    }

    /** TIPO_ESCRIT 1 = retificadora. */
    @Override
    protected void abertura(RegistroSped r) {
        abrirEscrituracao(r.texto("CNPJ"), r.texto("NOME"), r.data("DT_INI"), r.data("DT_FIN"), "1".equals(r.texto("TIPO_ESCRIT")), null);
    }

    /** Os registros do bloco M e do 1100/1500 são poucos por mês: ficam em memória até o fim. */
    @Override
    protected void tratar(RegistroSped r) {
        if ("0110".equals(r.registro())) {
            regime = r.texto("COD_INC_TRIB"); // 1 não cumulativo, 2 cumulativo, 3 ambos
            return;
        }
        conferirLimite(lidos.size(), r);
        lidos.add(r);
    }

    private List<RegistroSped> de(String registro) {
        return lidos.stream().filter(r -> registro.equals(r.registro())).toList();
    }

    @Override
    protected void validar() {
        if (regime == null) {
            contexto.registrar(Severidade.ALERTA, "0110", null, "SEM_REGIME", "O registro 0110 (regime de apuração) não foi encontrado.");
        }
        for (Tributo t : List.of(PIS, COFINS)) {
            for (RegistroSped m : de(t.consolidacao())) {
                // fórmulas do próprio leiaute (campos 05, 08, 12 e 13)
                formula(m, "VL_TOT_CONT_NC_DEV", soma(m, "VL_TOT_CONT_NC_PER").subtract(soma(m, "VL_TOT_CRED_DESC"))
                        .subtract(soma(m, "VL_TOT_CRED_DESC_ANT")), "contribuição não cumulativa − créditos descontados");
                formula(m, "VL_CONT_NC_REC", soma(m, "VL_TOT_CONT_NC_DEV").subtract(soma(m, "VL_RET_NC"))
                        .subtract(soma(m, "VL_OUT_DED_NC")), "devida − retenções − outras deduções");
                formula(m, "VL_CONT_CUM_REC", soma(m, "VL_TOT_CONT_CUM_PER").subtract(soma(m, "VL_RET_CUM"))
                        .subtract(soma(m, "VL_OUT_DED_CUM")), "contribuição cumulativa − retenções − outras deduções");
                formula(m, "VL_TOT_CONT_REC", soma(m, "VL_CONT_NC_REC").add(soma(m, "VL_CONT_CUM_REC")),
                        "não cumulativa a recolher + cumulativa a recolher");
                // consolidação = detalhe: contribuição do período e créditos descontados
                formula(m, "VL_TOT_CONT_NC_PER", somaDetalhe(t, NAO_CUMULATIVO), t.detalhe() + " (não cumulativa)");
                formula(m, "VL_TOT_CONT_CUM_PER", somaDetalhe(t, CUMULATIVO), t.detalhe() + " (cumulativa)");
                formula(m, "VL_TOT_CRED_DESC", de(t.creditos()).stream().map(c -> soma(c, "VL_CRED_DESC"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add), "créditos descontados no " + t.creditos());
            }
        }
    }

    private void formula(RegistroSped r, String campo, BigDecimal esperado, String expressao) {
        BigDecimal informado = soma(r, campo);
        if (informado.subtract(esperado).abs().compareTo(TOLERANCIA) > 0) {
            contexto.registrar(Severidade.ERRO, r.registro(), r.linha(), "FORMULA_" + r.registro(), r.registro() + "." + campo
                    + " informado " + informado.toPlainString() + ", mas " + expressao + " = " + esperado.toPlainString() + ".");
        }
    }

    private BigDecimal somaDetalhe(Tributo t, Set<String> codigos) {
        return de(t.detalhe()).stream().filter(d -> codigos.contains(d.texto("COD_CONT")))
                .map(d -> soma(d, "VL_CONT_PER")).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal soma(RegistroSped r, String campo) {
        BigDecimal v = r.decimal(campo);
        return v == null ? BigDecimal.ZERO : v;
    }

    @Override
    protected void gravar() {
        List<Apuracao> lidas = new ArrayList<>();
        List<SaldoFiscal> saldos = new ArrayList<>();
        for (Tributo t : List.of(PIS, COFINS)) {
            for (RegistroSped m : de(t.consolidacao())) {
                if (!"2".equals(regime)) {
                    lidas.add(parte(t, m, "NAO_CUMULATIVO", "Não cumulativo", NAO_CUMULATIVO, "VL_TOT_CONT_NC_PER",
                            List.of("VL_TOT_CRED_DESC", "VL_TOT_CRED_DESC_ANT", "VL_RET_NC", "VL_OUT_DED_NC"), "VL_CONT_NC_REC"));
                }
                if (!"1".equals(regime)) {
                    lidas.add(parte(t, m, "CUMULATIVO", "Cumulativo", CUMULATIVO, "VL_TOT_CONT_CUM_PER",
                            List.of("VL_RET_CUM", "VL_OUT_DED_CUM"), "VL_CONT_CUM_REC"));
                }
            }
            // controle de créditos: saldo de cada crédito (período de origem + código) ao fim do mês
            for (RegistroSped c : de(t.controle())) {
                saldos.add(new SaldoFiscal(t.saldoCredito(), null, c.texto("PER_APU_CRED") + "/" + c.texto("COD_CRED"),
                        "Crédito " + c.texto("COD_CRED") + " apurado em " + c.texto("PER_APU_CRED"), inicio, fim,
                        soma(c, "SD_CRED_DISP_EFD"), soma(c, "SLD_CRED_FIM"), c.registro(), c.linha()));
            }
        }
        apuracoes.gravar(escrituracaoId, cliente.getId(), documento.getId(), lidas);
        apuracoes.gravarSaldos(escrituracaoId, cliente.getId(), saldos);
    }

    /** Uma parte do regime: contribuição do período, créditos e deduções, a recolher; detalhe com M210/M610 e créditos. */
    private Apuracao parte(Tributo t, RegistroSped m, String regimeParte, String descricao, Set<String> codigos,
                           String campoApurado, List<String> camposDeducao, String campoARecolher) {
        Apuracao a = new Apuracao(t.nome(), "FEDERAL", null, inicio, fim, "MENSAL", regimeParte, descricao);
        Origem origem = Origem.de(m);
        a.apurado = soma(m, campoApurado);
        a.deducoes = camposDeducao.stream().map(c -> soma(m, c)).reduce(BigDecimal.ZERO, BigDecimal::add);
        a.aRecolher = soma(m, campoARecolher);
        a.origem.put("valorApurado", origem);
        a.origem.put("deducoes", origem);
        a.origem.put("valorARecolher", origem);
        BigDecimal base = BigDecimal.ZERO;
        for (RegistroSped d : de(t.detalhe())) {
            if (codigos.contains(d.texto("COD_CONT"))) {
                base = base.add(soma(d, "VL_BC_CONT_AJUS"));
                a.origem.putIfAbsent("baseCalculo", Origem.de(d));
                a.detalhe("CONTRIBUICAO", d.texto("COD_CONT"), CONTRIBUICOES.getOrDefault(d.texto("COD_CONT"),
                        "Contribuição " + d.texto("COD_CONT")) + " (base " + soma(d, "VL_BC_CONT_AJUS").toPlainString() + ")",
                        soma(d, "VL_CONT_PER"), d);
            }
        }
        a.base = a.origem.containsKey("baseCalculo") ? base : null;
        if ("NAO_CUMULATIVO".equals(regimeParte)) {
            for (RegistroSped c : de(t.creditos())) {
                a.detalhe("CREDITO", c.texto("COD_CRED"), "Crédito " + c.texto("COD_CRED") + " (base "
                        + soma(c, t.campoBase()).toPlainString() + ", disponível " + soma(c, "VL_CRED_DISP").toPlainString()
                        + ", saldo " + soma(c, "SLD_CRED").toPlainString() + ")", soma(c, "VL_CRED_DESC"), c);
            }
        }
        for (String campo : camposDeducao) {
            a.detalhe("DEDUCAO", campo, DEDUCOES.get(campo), soma(m, campo), m);
        }
        return a;
    }
}
