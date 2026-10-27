package com.fiscalhub.backend.fiscal;

import com.fiscalhub.backend.contabil.RepositorioContabil;
import com.fiscalhub.backend.contabil.RepositorioContabil.LinhaDemonstracao;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.importacao.ContextoProcessamento;
import com.fiscalhub.backend.importacao.Escrituracoes;
import com.fiscalhub.backend.importacao.FalhaImportacao;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import com.fiscalhub.backend.fiscal.RepositorioApuracao.Apuracao;
import com.fiscalhub.backend.fiscal.RepositorioApuracao.Origem;
import com.fiscalhub.backend.fiscal.RepositorioApuracao.SaldoFiscal;
import com.fiscalhub.backend.sped.LayoutSped;
import com.fiscalhub.backend.sped.LeituraSped;
import com.fiscalhub.backend.sped.RegistroSped;
import com.fiscalhub.backend.sped.Saldos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ECF: forma de tributação (0010), e-Lalur/e-Lacs Parte A (M300/M350) e Parte B (M010/M500), cálculo do IRPJ e da CSLL
 * no Lucro Real (N500 a N670, estimativas mensais e ajuste anual ou trimestral) e no Lucro Presumido (P200 a P500), e a
 * DRE referencial (L300/P150) para a conferência com a ECD. Leiautes e fonte dos campos:
 * resources/sped/ecf-leiautes.properties. Os códigos das linhas (base, alíquota, adicional, a pagar) são os das tabelas
 * dinâmicas oficiais (planilha "Tabelas Dinâmicas e Planos de Contas Referenciais", leiautes 9 a 12, iguais nos quatro).
 */
final class LeituraEcf extends LeituraSped {

    private static final Set<String> LIDOS = Set.of("0010", "L030", "L300", "P030", "P150", "M010", "M030", "M300", "M350",
            "M500", "N030", "N500", "N620", "N630", "N650", "N660", "N670", "P200", "P300", "P400", "P500");
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");
    /** Códigos padrão da Parte B (tabela PARTEB_PADRAO): prejuízo fiscal (IRPJ) e base negativa (CSLL). */
    private static final Set<String> PREJUIZO = Set.of("1000", "1001", "1002");
    private static final Set<String> BASE_NEGATIVA = Set.of("1003", "1004");

    /** Uma linha de tabela dinâmica (CODIGO|DESCRICAO|VALOR) com a origem. */
    private record Linha(String codigo, String descricao, BigDecimal valor, String tipo, RegistroSped r) {
    }

    /** Período de um bloco (L030, M030, N030, P030) e as linhas lidas nele, por registro. */
    private static final class Periodo {
        final LocalDate inicio;
        final LocalDate fim;
        final String codigo;
        final Map<String, List<Linha>> linhas = new HashMap<>();

        Periodo(RegistroSped r) {
            inicio = r.data("DT_INI");
            fim = r.data("DT_FIN");
            codigo = r.texto("PER_APUR");
        }

        /** A00 (anual) e T01..T04 (trimestrais) são períodos de apuração; A01..A12 são balancetes acumulados até o mês. */
        boolean apuracao() {
            return "A00".equals(codigo) || codigo != null && codigo.startsWith("T");
        }

        List<Linha> de(String registro) {
            return linhas.getOrDefault(registro, List.of());
        }

        Linha linha(String registro, String codigo) {
            return de(registro).stream().filter(l -> codigo.equals(l.codigo())).findFirst().orElse(null);
        }
    }

    private final RepositorioApuracao apuracoes;
    private final RepositorioContabil contabil;

    private String formaTributacao;
    private String qualificacao;
    private long linha0010;
    private final Map<String, Periodo> blocoAtual = new HashMap<>(); // L, M, N, P -> período em leitura
    private final Map<String, List<Periodo>> periodos = new LinkedHashMap<>(); // L, M, N, P -> períodos do arquivo
    /** M010: conta da Parte B -> [código padrão RFB, descrição]. */
    private final Map<String, String[]> contasParteB = new HashMap<>();
    private final List<SaldoFiscal> saldos = new ArrayList<>();
    private int guardados;

    LeituraEcf(DocumentoFiscal documento, ContextoProcessamento contexto, Escrituracoes escrituracoes,
               RepositorioApuracao apuracoes, RepositorioContabil contabil, LayoutSped layout) {
        super("ECF", "ECF", layout, LIDOS, documento, contexto, escrituracoes);
        this.apuracoes = apuracoes;
        this.contabil = contabil;
    }

    /** |0000|LECF|COD_VER|... */
    @Override
    protected String versao(String registro, String[] campos) {
        return "0000".equals(registro) && campos.length > 3 ? campos[3].trim() : null;
    }

    /** RETIFICADORA: S (retificadora) ou F (original com mudança de forma de tributação) substituem a anterior. */
    @Override
    protected void abertura(RegistroSped r) {
        String retificadora = r.texto("RETIFICADORA");
        abrirEscrituracao(r.texto("CNPJ"), r.texto("NOME"), r.data("DT_INI"), r.data("DT_FIN"),
                "S".equals(retificadora) || "F".equals(retificadora), null);
    }

    @Override
    protected void tratar(RegistroSped r) {
        conferirLimite(guardados++, r);
        String registro = r.registro();
        switch (registro) {
            case "0010" -> {
                formaTributacao = r.texto("FORMA_TRIB");
                qualificacao = r.texto("COD_QUALIF_PJ");
                linha0010 = r.linha();
            }
            case "L030", "M030", "N030", "P030" -> {
                Periodo p = new Periodo(r);
                blocoAtual.put(registro.substring(0, 1), p);
                periodos.computeIfAbsent(registro.substring(0, 1), b -> new ArrayList<>()).add(p);
            }
            case "M010" -> contasParteB.put(r.texto("COD_CTA_B"), new String[]{r.texto("COD_PB_RFB"), r.texto("DESC_CTA_LAL")});
            case "M500" -> saldoParteB(r);
            default -> {
                Periodo p = blocoAtual.get(registro.substring(0, 1));
                if (p == null) {
                    throw new FalhaImportacao("ECF_SEM_PERIODO", "O registro " + registro + " aparece antes do período ("
                            + registro.charAt(0) + "030) do bloco.", registro, r.linha());
                }
                p.linhas.computeIfAbsent(registro, k -> new ArrayList<>()).add(linha(r));
            }
        }
    }

    /** L300/P150 (conta referencial com D/C), M300/M350 (lançamento com tipo) e tabelas N/P (código, descrição, valor). */
    private static Linha linha(RegistroSped r) {
        BigDecimal valor = switch (r.registro()) {
            case "L300", "P150" -> Saldos.comSinal(r.decimal("VALOR"), r.texto("IND_VALOR"));
            default -> r.decimal("VALOR");
        };
        return new Linha(r.texto("CODIGO"), r.texto("DESCRICAO"), valor == null ? BigDecimal.ZERO : valor,
                r.texto("TIPO_LANCAMENTO"), r);
    }

    /** Parte B: prejuízo fiscal e base negativa da CSLL (pelo código padrão da RFB da conta, no M010). */
    private void saldoParteB(RegistroSped r) {
        Periodo p = blocoAtual.get("M");
        String[] conta = contasParteB.get(r.texto("COD_CTA_B"));
        if (p == null || conta == null) {
            return; // conta fora do M010: o PVA não deixa transmitir; não há o que classificar
        }
        String tipo = PREJUIZO.contains(conta[0]) ? "PREJUIZO_FISCAL" : BASE_NEGATIVA.contains(conta[0]) ? "BASE_NEGATIVA_CSLL" : null;
        if (tipo != null) {
            // D = prejuízo ou valor a excluir no futuro (o saldo que interessa); C reduz
            saldos.add(new SaldoFiscal(tipo, null, r.texto("COD_CTA_B"), conta[1] == null ? tipo : conta[1], p.inicio, p.fim,
                    Saldos.comSinal(r.decimal("SD_INI_LAL"), r.texto("IND_SD_INI_LAL")),
                    Saldos.comSinal(r.decimal("SD_FIM_LAL"), r.texto("IND_SD_FIM_LAL")), "M500", r.linha()));
        }
    }

    @Override
    protected void validar() {
        if (formaTributacao == null) {
            throw new FalhaImportacao("ECF_SEM_0010", "A ECF não tem o registro 0010 (parâmetros de tributação).", "0010", null);
        }
        if (Set.of("8", "9").contains(formaTributacao)) {
            contexto.registrar(Severidade.INFO, "0010", linha0010, "IMUNE_OU_ISENTA",
                    "ECF de entidade imune ou isenta do IRPJ: não há apuração de IRPJ e CSLL.");
        }
        if (Set.of("2", "4", "6", "7").contains(formaTributacao)) {
            contexto.registrar(Severidade.ALERTA, "0010", linha0010, "LUCRO_ARBITRADO_NAO_LIDO",
                    "A forma de tributação inclui Lucro Arbitrado: os trimestres arbitrados (bloco T) não são lidos.");
        }
        // e-Lalur x base: N500(1) e N650(1) seguem M300/M350 linhas 175 (atividade geral) e 349 (rural) (tabelas dinâmicas)
        for (Periodo n : periodos.getOrDefault("N", List.of())) {
            Periodo m = periodos.getOrDefault("M", List.of()).stream().filter(x -> x.codigo.equals(n.codigo)).findFirst().orElse(null);
            if (m == null || !"01".equals(qualificacao)) {
                continue;
            }
            conferirBase(m, "M300", n.linha("N500", "1"), "IRPJ", "lucro real");
            conferirBase(m, "M350", n.linha("N650", "1"), "CSLL", "base de cálculo da CSLL");
        }
    }

    private void conferirBase(Periodo m, String parteA, Linha base, String tributo, String nome) {
        Linha geral = m.linha(parteA, "175");
        Linha rural = m.linha(parteA, "349");
        if (base == null || geral == null) {
            return;
        }
        BigDecimal g = geral.valor();
        BigDecimal r = rural == null ? BigDecimal.ZERO : rural.valor();
        BigDecimal esperado = g.signum() <= 0 && r.signum() <= 0 ? g.add(r) : g.max(BigDecimal.ZERO).add(r.max(BigDecimal.ZERO));
        if (esperado.subtract(base.valor()).abs().compareTo(TOLERANCIA) > 0) {
            contexto.registrar(Severidade.ALERTA, base.r().registro(), base.r().linha(), "BASE_DIFERE_ELALUR",
                    tributo + " " + m.codigo + ": a base de cálculo (" + base.valor().toPlainString() + ") difere do " + nome
                            + " do e-Lalur/e-Lacs (" + parteA + " linha " + geral.r().linha() + ": " + esperado.toPlainString() + ").");
        }
    }

    @Override
    protected void gravar() {
        List<Apuracao> lidas = new ArrayList<>();
        for (Periodo n : periodos.getOrDefault("N", List.of())) {
            Periodo m = periodos.getOrDefault("M", List.of()).stream().filter(x -> x.codigo.equals(n.codigo)).findFirst().orElse(null);
            boolean estimativa = !n.apuracao();
            String descricao = estimativa ? "Estimativa mensal" : "A00".equals(n.codigo) ? "Ajuste anual" : n.codigo.charAt(2) + "º trimestre";
            String periodicidade = estimativa ? "MENSAL" : "A00".equals(n.codigo) ? "ANUAL" : "TRIMESTRAL";
            if (estimativa) {
                adicionar(lidas, apuracao("IRPJ", n, "N620", "1", List.of("3", "4"), "26", periodicidade, "LUCRO_REAL", descricao, null, null));
                adicionar(lidas, apuracao("CSLL", n, "N660", "2", List.of("3"), "18", periodicidade, "LUCRO_REAL", descricao, null, null));
            } else {
                adicionar(lidas, apuracao("IRPJ", n, "N630", "1", List.of("3", "4"), "26", periodicidade, "LUCRO_REAL", descricao, m, "M300"));
                adicionar(lidas, apuracao("CSLL", n, "N670", "1", List.of("4"), "21", periodicidade, "LUCRO_REAL", descricao, m, "M350"));
            }
        }
        for (Periodo p : periodos.getOrDefault("P", List.of())) {
            if (!p.apuracao() || "A00".equals(p.codigo)) {
                continue; // Lucro Presumido é trimestral; A00 só traz balanço e DRE anuais
            }
            String descricao = p.codigo.charAt(2) + "º trimestre";
            adicionar(lidas, apuracao("IRPJ", p, "P300", "1", List.of("3", "4"), "15", "TRIMESTRAL", "LUCRO_PRESUMIDO", descricao, p, "P200"));
            adicionar(lidas, apuracao("CSLL", p, "P500", "1", List.of("4"), "13", "TRIMESTRAL", "LUCRO_PRESUMIDO", descricao, p, "P400"));
        }
        apuracoes.gravar(escrituracaoId, cliente.getId(), documento.getId(), lidas);
        apuracoes.gravarSaldos(escrituracaoId, cliente.getId(), saldos);
        gravarDre();
    }

    private static void adicionar(List<Apuracao> lidas, Apuracao a) {
        if (a != null) {
            lidas.add(a);
        }
    }

    /**
     * Uma apuração a partir da tabela de cálculo do período (N620/N630/N660/N670/P300/P500): base, imposto apurado
     * (alíquota + adicional), valor a pagar e, no detalhe, a demonstração que leva à base (Parte A ou base presumida)
     * seguida das linhas do cálculo. "A pagar" negativo é saldo negativo (credor). Deduções = apurado − a pagar.
     */
    private static Apuracao apuracao(String tributo, Periodo p, String calculo, String codigoBase, List<String> codigosApurado,
                                     String codigoAPagar, String periodicidade, String regime, String descricao,
                                     Periodo demonstracao, String registroDemonstracao) {
        Linha aPagar = p.linha(calculo, codigoAPagar);
        if (aPagar == null) {
            return null;
        }
        Apuracao a = new Apuracao(tributo, "FEDERAL", null, p.inicio, p.fim, periodicidade, regime, descricao);
        Linha base = p.linha(calculo, codigoBase);
        if (base != null) {
            a.base = base.valor();
            a.origem.put("baseCalculo", Origem.de(base.r()));
        }
        a.apurado = BigDecimal.ZERO;
        for (String codigo : codigosApurado) {
            Linha l = p.linha(calculo, codigo);
            if (l != null) {
                a.apurado = a.apurado.add(l.valor());
                a.origem.putIfAbsent("valorApurado", Origem.de(l.r()));
            }
        }
        a.aRecolher = aPagar.valor().max(BigDecimal.ZERO);
        a.origem.put("valorARecolher", Origem.de(aPagar.r()));
        a.deducoes = a.apurado.subtract(aPagar.valor());
        a.origem.put("deducoes", Origem.de(aPagar.r()));
        if (aPagar.valor().signum() < 0) {
            a.saldoCredor = aPagar.valor().negate();
            a.origem.put("saldoCredor", Origem.de(aPagar.r()));
        }
        if (demonstracao != null) {
            for (Linha l : demonstracao.de(registroDemonstracao)) {
                a.detalhe(grupo(l), l.codigo(), l.descricao(), l.valor(), l.r());
            }
        }
        for (Linha l : p.de(calculo)) {
            a.detalhe("CALCULO", l.codigo(), l.descricao(), l.valor(), l.r());
        }
        return a;
    }

    /** Tipo do lançamento da Parte A (M300/M350) ou base presumida (P200/P400). */
    private static String grupo(Linha l) {
        if (l.tipo() == null) {
            return "BASE";
        }
        return switch (l.tipo()) {
            case "A" -> "ADICAO";
            case "E" -> "EXCLUSAO";
            case "P" -> "COMPENSACAO";
            default -> "LUCRO";
        };
    }

    /**
     * DRE referencial declarada (L300 no Lucro Real, P150 no Presumido), por período de apuração, para a conferência com
     * a ECD do mesmo exercício. Fica fora das demonstrações da tela (origem ECF).
     */
    private void gravarDre() {
        for (String bloco : List.of("L", "P")) {
            String registro = "L".equals(bloco) ? "L300" : "P150";
            List<Periodo> lista = periodos.getOrDefault(bloco, List.of()).stream().filter(Periodo::apuracao).toList();
            boolean anual = lista.stream().anyMatch(p -> "A00".equals(p.codigo));
            for (Periodo p : lista) {
                if (p.de(registro).isEmpty() || anual && !"A00".equals(p.codigo)) {
                    continue;
                }
                long id = contabil.inserirDemonstracao(escrituracaoId, "DRE", "ECF", p.inicio, p.fim, null);
                List<LinhaDemonstracao> linhas = new ArrayList<>();
                int ordem = 0;
                for (Linha l : p.de(registro)) {
                    linhas.add(new LinhaDemonstracao(l.codigo(), l.descricao() == null ? l.codigo() : l.descricao(), null, null,
                            ++ordem, false, null, null, l.valor(), registro, l.r().linha(), false, null));
                }
                contabil.inserirLinhas(id, linhas);
            }
        }
    }
}
