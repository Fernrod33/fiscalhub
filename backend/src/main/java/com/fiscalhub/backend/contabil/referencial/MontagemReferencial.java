package com.fiscalhub.backend.contabil.referencial;

import com.fiscalhub.backend.contabil.RepositorioContabil;
import com.fiscalhub.backend.contabil.RepositorioContabil.LinhaDemonstracao;
import com.fiscalhub.backend.importacao.ContextoProcessamento;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Balanço e DRE padronizados pelo Plano de Contas Referencial da RFB, a partir dos saldos das contas mapeadas
 * (I051 da ECD ou de-para da planilha). BP: saldos finais (e iniciais) do exercício; DRE: resultado antes do
 * encerramento (I355; na planilha, o saldo das contas de resultado). Cada linha analítica guarda as contas da empresa
 * que a compõem. Usado pela ECD e pelo balancete em planilha.
 */
@Service
public class MontagemReferencial {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");
    /** Linha derivada do BP montado de balancete antes do encerramento (não existe no plano oficial). */
    public static final String RESULTADO_NAO_ENCERRADO = "2.03.RNE";

    /** Pedido de montagem: o que ler (origens dos saldos) e o que conferir (publicadas, quando houver). */
    public record Pedido(long escrituracaoId, String plano, LocalDate inicio, LocalDate fim, String origemBalanco,
                         String origemResultado, List<LinhaDemonstracao> balancoPublicado,
                         List<LinhaDemonstracao> resultadoPublicado) {
    }

    private record ContaRef(String codigo, String descricao, String superior, int nivel, int ordem, boolean sintetica,
                            String natureza) {
    }

    private final JdbcTemplate jdbc;
    private final RepositorioContabil repositorio;

    public MontagemReferencial(JdbcTemplate jdbc, RepositorioContabil repositorio) {
        this.jdbc = jdbc;
        this.repositorio = repositorio;
    }

    public void montar(Pedido pedido, ContextoProcessamento contexto) {
        if (pedido.plano() == null) {
            return; // sem plano referencial: o alerta já foi registrado na leitura
        }
        Map<String, String> tabelas = tabelas(pedido.plano(), pedido.fim().getYear());
        if (tabelas.size() != 2) {
            boolean suportado = !anos(pedido.plano()).isEmpty();
            contexto.registrar(Severidade.ALERTA, "0000", null,
                    suportado ? "TABELA_REFERENCIAL_AUSENTE" : "PLANO_REFERENCIAL_NAO_SUPORTADO",
                    semTabelas(pedido.plano(), pedido.fim().getYear()));
            return;
        }
        Map<Long, String> referencialDaConta = new HashMap<>();
        jdbc.query("""
                SELECT id, codigo_referencial FROM conta_contabil
                WHERE escrituracao_id = ? AND NOT sintetica AND codigo_referencial IS NOT NULL""",
                rs -> { referencialDaConta.put(rs.getLong(1), rs.getString(2)); }, pedido.escrituracaoId());

        Map<String, ContaRef> planoBp = plano(tabelas.get("BP"));
        Map<String, ContaRef> planoDre = plano(tabelas.get("DRE"));
        List<String> foraDasTabelas = referencialDaConta.values().stream()
                .filter(c -> !planoBp.containsKey(c) && !planoDre.containsKey(c)).distinct().sorted().toList();
        if (!foraDasTabelas.isEmpty()) {
            contexto.registrar(Severidade.ALERTA, null, null, "REFERENCIAL_FORA_DA_TABELA", "Código(s) referencial(is) "
                    + "que não existem nas tabelas " + tabelas.values() + " ficaram fora das demonstrações referenciais: "
                    + String.join(", ", foraDasTabelas.subList(0, Math.min(30, foraDasTabelas.size()))));
        }
        Map<String, ContaRef> planoDoBalanco = planoBp;
        Map<Long, String> referencialNoBalanco = referencialDaConta;
        if ("PLANILHA".equals(pedido.origemResultado())) {
            // balancete antes do encerramento: o lucro do período ainda está nas contas de resultado, não no PL. Entra no
            // PL como linha derivada, composta pelas contas de resultado do próprio balancete (as mapeadas na DRE).
            ContaRef pl = planoBp.values().stream().filter(r -> "03".equals(r.natureza()))
                    .min(Comparator.comparingInt(ContaRef::nivel)).orElse(null);
            if (pl != null) {
                int ordem = planoBp.values().stream().filter(r -> r.codigo().startsWith(pl.codigo()))
                        .mapToInt(ContaRef::ordem).max().orElse(pl.ordem());
                planoDoBalanco = new LinkedHashMap<>(planoBp);
                planoDoBalanco.put(RESULTADO_NAO_ENCERRADO, new ContaRef(RESULTADO_NAO_ENCERRADO,
                        "Resultado do período não encerrado", pl.codigo(), pl.nivel() + 1, ordem, false, "03"));
                referencialNoBalanco = new HashMap<>(referencialDaConta);
                referencialNoBalanco.replaceAll((conta, ref) -> planoDre.containsKey(ref) ? RESULTADO_NAO_ENCERRADO : ref);
            }
        }
        BigDecimal[] totaisBp = montarUma("BP", tabelas.get("BP"), planoDoBalanco, pedido, referencialNoBalanco,
                valores(pedido.escrituracaoId(), pedido.origemBalanco(), pedido.fim(), true),
                valores(pedido.escrituracaoId(), pedido.origemBalanco(), pedido.inicio(), false));
        BigDecimal[] totaisDre = montarUma("DRE", tabelas.get("DRE"), planoDre, pedido, referencialDaConta, resultado(pedido), null);
        conferir(pedido, totaisBp, totaisDre, contexto);
    }

    private Map<String, ContaRef> plano(String tabela) {
        Map<String, ContaRef> plano = new LinkedHashMap<>();
        if (tabela != null) {
            jdbc.query("""
                    SELECT codigo, descricao, codigo_superior, nivel, ordem, sintetica, natureza FROM conta_referencial
                    WHERE tabela = ? ORDER BY ordem""", rs -> {
                plano.put(rs.getString(1), new ContaRef(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getInt(5), rs.getBoolean(6), rs.getString(7)));
            }, tabela);
        }
        return plano;
    }

    /**
     * Tabelas do plano para o exercício (demonstração BP/DRE -> tabela). Só a tabela oficial do próprio ano: a de outro
     * exercício nunca é reaproveitada. Único ponto dessa escolha.
     */
    public Map<String, String> tabelas(String plano, int exercicio) {
        Map<String, String> escolhidas = new HashMap<>();
        jdbc.query("SELECT demonstracao, tabela FROM tabela_referencial WHERE plano = ? AND ano = ?",
                rs -> { escolhidas.put(rs.getString(1), rs.getString(2)); }, plano, exercicio);
        return escolhidas;
    }

    /** Por que não há tabelas para o plano e o exercício (mensagem do alerta e do assistente da planilha). */
    public String semTabelas(String plano, int exercicio) {
        List<Integer> anos = anos(plano);
        if (anos.isEmpty()) {
            return "O plano referencial " + plano + " não é suportado (carregados: 1 - PJ em geral, Lucro Real; 2 - PJ em "
                    + "geral, Lucro Presumido). Planos de instituições financeiras, seguradoras e entidades imunes ou isentas "
                    + "estão fora do escopo. Só a demonstração publicada fica disponível.";
        }
        return "Não há tabela oficial do plano referencial " + plano + " carregada para o exercício " + exercicio
                + " (carregadas: " + anos.getFirst() + " a " + anos.getLast() + "). Só a demonstração publicada fica "
                + "disponível; o referencial será montado ao reprocessar quando a tabela oficial do ano for carregada.";
    }

    private List<Integer> anos(String plano) {
        return jdbc.queryForList("SELECT DISTINCT ano FROM tabela_referencial WHERE plano = ? ORDER BY ano", Integer.class, plano);
    }

    /** BP: saldo por conta na data (final: saldo final do período que termina no fim; inicial: saldo inicial do primeiro). */
    private Map<Long, BigDecimal> valores(long escrituracaoId, String origem, LocalDate data, boolean finalDoPeriodo) {
        Map<Long, BigDecimal> porConta = new HashMap<>();
        String sql = finalDoPeriodo
                ? "SELECT conta_id, sum(saldo_final) FROM saldo_conta WHERE escrituracao_id = ? AND origem = ? AND data_fim = ? GROUP BY conta_id"
                : "SELECT conta_id, sum(saldo_inicial) FROM saldo_conta WHERE escrituracao_id = ? AND origem = ? AND data_inicio = ? GROUP BY conta_id";
        jdbc.query(sql, rs -> { porConta.put(rs.getLong(1), rs.getBigDecimal(2)); }, escrituracaoId, origem, Date.valueOf(data));
        return porConta;
    }

    /** DRE: soma de todas as apurações do exercício (lucro real trimestral tem quatro I350). */
    private Map<Long, BigDecimal> resultado(Pedido pedido) {
        Map<Long, BigDecimal> porConta = new HashMap<>();
        String sql = "I355".equals(pedido.origemResultado())
                ? "SELECT conta_id, sum(saldo_final) FROM saldo_conta WHERE escrituracao_id = ? AND origem = 'I355' GROUP BY conta_id"
                // planilha: o saldo de todas as contas; entram na DRE só as mapeadas para a tabela de resultado
                : "SELECT conta_id, sum(saldo_final) FROM saldo_conta WHERE escrituracao_id = ? AND origem = 'PLANILHA' GROUP BY conta_id";
        jdbc.query(sql, rs -> { porConta.put(rs.getLong(1), rs.getBigDecimal(2)); }, pedido.escrituracaoId());
        return porConta;
    }

    /** Monta e grava uma demonstração; devolve os totais dos grupos de topo [nível 1 "1"/primeiro, "2"/segundo...]. */
    private BigDecimal[] montarUma(String tipo, String tabela, Map<String, ContaRef> plano, Pedido pedido,
                                   Map<Long, String> referencialDaConta, Map<Long, BigDecimal> finais,
                                   Map<Long, BigDecimal> iniciais) {
        Map<String, BigDecimal[]> valores = new HashMap<>(); // código referencial -> [inicial, final]
        Map<String, List<Long>> composicao = new HashMap<>();
        for (Map.Entry<Long, String> e : referencialDaConta.entrySet()) {
            ContaRef ref = plano.get(e.getValue());
            if (ref == null) {
                continue; // conta da outra demonstração
            }
            BigDecimal fim = finais.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal ini = iniciais == null ? null : iniciais.getOrDefault(e.getKey(), BigDecimal.ZERO);
            composicao.computeIfAbsent(ref.codigo(), c -> new ArrayList<>()).add(e.getKey());
            // soma na própria linha e em todas as superiores
            for (ContaRef r = ref; r != null; r = r.superior() == null ? null : plano.get(r.superior())) {
                BigDecimal[] v = valores.computeIfAbsent(r.codigo(), c -> new BigDecimal[]{iniciais == null ? null : BigDecimal.ZERO, BigDecimal.ZERO});
                if (ini != null) {
                    v[0] = v[0].add(ini);
                }
                v[1] = v[1].add(fim);
            }
        }
        if (valores.isEmpty()) {
            return null;
        }
        List<LinhaDemonstracao> linhas = valores.keySet().stream().map(plano::get)
                .sorted(Comparator.comparingInt(ContaRef::ordem))
                .map(r -> new LinhaDemonstracao(r.codigo(), r.descricao(), r.nivel(), r.superior(), r.ordem(), r.sintetica(),
                        grupo(tipo, r), valores.get(r.codigo())[0], valores.get(r.codigo())[1], null, null,
                        RESULTADO_NAO_ENCERRADO.equals(r.codigo())))
                .toList();
        long demonstracaoId = repositorio.inserirDemonstracao(pedido.escrituracaoId(), tipo, "REFERENCIAL",
                pedido.inicio(), pedido.fim(), tabela);
        Map<String, Long> idsLinhas = repositorio.inserirLinhas(demonstracaoId, linhas);
        Map<Long, List<Long>> contasPorLinha = new HashMap<>();
        composicao.forEach((codigo, contas) -> contasPorLinha.put(idsLinhas.get(codigo), contas));
        repositorio.inserirComposicao(contasPorLinha);
        // totais de topo, na ordem da tabela (BP: ativo, passivo + PL; DRE: resultado)
        return linhas.stream().filter(l -> l.nivel() == 1).map(LinhaDemonstracao::valorFinal).toArray(BigDecimal[]::new);
    }

    /** BP: A para o ativo (natureza 01), P para passivo e PL. DRE: sem grupo (o sinal já diz receita ou despesa). */
    private static String grupo(String tipo, ContaRef r) {
        if (!"BP".equals(tipo)) {
            return null;
        }
        return "01".equals(r.natureza()) ? "A" : "P";
    }

    /** Conferência cruzada: totais do referencial contra os da demonstração publicada pela empresa (J100/J150). */
    private static void conferir(Pedido pedido, BigDecimal[] bp, BigDecimal[] dre, ContextoProcessamento contexto) {
        if (bp != null && !pedido.balancoPublicado().isEmpty()) {
            comparar("Ativo", bp.length > 0 ? bp[0] : BigDecimal.ZERO, topo(pedido.balancoPublicado(), "A"), "J100", contexto);
            comparar("Passivo + PL", bp.length > 1 ? bp[1] : BigDecimal.ZERO, topo(pedido.balancoPublicado(), "P"), "J100", contexto);
        }
        if (dre != null && !pedido.resultadoPublicado().isEmpty()) {
            BigDecimal referencial = Arrays.stream(dre).reduce(BigDecimal.ZERO, BigDecimal::add);
            comparar("Resultado do exercício", referencial, topo(pedido.resultadoPublicado(), null), "J150", contexto);
        }
    }

    /** Soma das linhas de nível mais alto da publicada (do grupo, quando informado). */
    public static BigDecimal topo(List<LinhaDemonstracao> linhas, String grupo) {
        int nivel = linhas.stream().filter(l -> l.nivel() != null).mapToInt(LinhaDemonstracao::nivel).min().orElse(1);
        return linhas.stream()
                .filter(l -> l.nivel() != null && l.nivel() == nivel && (grupo == null || grupo.equals(l.grupo())))
                .map(l -> l.valorFinal() == null ? BigDecimal.ZERO : l.valorFinal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void comparar(String item, BigDecimal referencial, BigDecimal publicada, String registro,
                                 ContextoProcessamento contexto) {
        BigDecimal diferenca = referencial.subtract(publicada);
        if (diferenca.abs().compareTo(TOLERANCIA) > 0) {
            contexto.registrar(Severidade.ALERTA, registro, null, "REFERENCIAL_DIFERE_PUBLICADA", item + ": referencial "
                    + referencial.toPlainString() + ", publicada " + publicada.toPlainString() + ", diferença "
                    + diferenca.toPlainString() + " (devedor +, credor −). Confira contas sem mapeamento ou mapeadas errado.");
        }
    }
}
