package com.fiscalhub.backend.contabil;

import com.fiscalhub.backend.contabil.RepositorioContabil.LinhaDemonstracao;
import com.fiscalhub.backend.contabil.referencial.MontagemReferencial;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.AcessoRecursoService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Leitura das demonstrações para a tela comparativa: um exercício por coluna (a escrituração vigente de cada ano),
 * análise horizontal e vertical calculadas aqui, conferência referencial x publicada e a origem de cada valor.
 * Valores na convenção única: devedor +, credor −.
 */
@Service
@Transactional(readOnly = true)
public class DemonstracaoService {

    /** Base da análise vertical da DRE: a receita líquida (código do plano referencial ou descrição na publicada). */
    private static final Set<String> RECEITA_LIQUIDA_REFERENCIAL = Set.of("3.01.01.01");
    private static final Pattern RECEITA_LIQUIDA = Pattern.compile("receita\\s+(operacional\\s+)?l[ií]quida", Pattern.CASE_INSENSITIVE);
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    public record Exercicio(int exercicio, long demonstracaoId, long documentoId, String arquivo, String origemDados,
                            LocalDate dataInicio, LocalDate dataFim, String tabelaReferencial) {
    }

    public record Valor(long linhaId, BigDecimal valor, BigDecimal valorInicial, BigDecimal av, BigDecimal ah) {
    }

    public record LinhaComparativa(String codigo, String descricao, Integer nivel, String codigoSuperior, boolean totalizadora,
                                   String grupo, Map<Integer, Valor> valores) {
    }

    public record Conferencia(int exercicio, String item, BigDecimal referencial, BigDecimal publicada, BigDecimal diferenca) {
    }

    public record Comparativo(String tipo, String origem, List<Exercicio> exercicios, List<LinhaComparativa> linhas,
                              List<Conferencia> conferencia) {
    }

    public record SaldoOrigem(String registro, long linha, BigDecimal valor, LocalDate dataFim, String centroCusto) {
    }

    public record ContaOrigem(long contaId, String codigo, String nome, String codigoReferencial, BigDecimal valor,
                              List<SaldoOrigem> saldos) {
    }

    public record Origem(long linhaId, String codigo, String descricao, String tipo, String origem, long documentoId,
                         String arquivo, String registro, Long linha, List<ContaOrigem> contas) {
    }

    public record Escrituracao(long id, long documentoId, String arquivo, String origem, LocalDate dataInicio, LocalDate dataFim,
                               String versaoLeiaute, String planoReferencial, String finalidade, String situacao) {
    }

    private final JdbcTemplate jdbc;
    private final AcessoRecursoService acesso;

    public DemonstracaoService(JdbcTemplate jdbc, AcessoRecursoService acesso) {
        this.jdbc = jdbc;
        this.acesso = acesso;
    }

    public List<Escrituracao> escrituracoes(long clienteId, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        return jdbc.query("""
                SELECT e.id, e.documento_id, d.nome_original_arquivo, e.origem, e.data_inicio, e.data_fim, e.versao_leiaute,
                       e.plano_referencial, e.finalidade, e.situacao
                FROM escrituracao_contabil e JOIN documento_fiscal d ON d.id = e.documento_id
                WHERE e.cliente_id = ? ORDER BY e.data_fim DESC, e.id DESC""",
                (rs, i) -> new Escrituracao(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getDate(5).toLocalDate(), rs.getDate(6).toLocalDate(), rs.getString(7), rs.getString(8),
                        rs.getString(9), rs.getString(10)), clienteId);
    }

    public Comparativo comparativo(long clienteId, String tipo, String origem, List<Integer> exercicios, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        if (!Set.of("BP", "DRE").contains(tipo) || !Set.of("REFERENCIAL", "PUBLICADA").contains(origem)) {
            throw new BusinessRuleException("Use tipo=BP|DRE e origem=REFERENCIAL|PUBLICADA.");
        }
        Map<Integer, Exercicio> porAno = demonstracoes(clienteId, tipo, origem, exercicios);
        Map<Integer, List<LinhaDemonstracao>> linhasPorAno = new TreeMap<>();
        Map<Integer, Map<String, Long>> idsPorAno = new TreeMap<>();
        porAno.forEach((ano, e) -> {
            Map<String, Long> ids = new LinkedHashMap<>();
            linhasPorAno.put(ano, linhas(e.demonstracaoId(), ids));
            idsPorAno.put(ano, ids);
        });

        // uma linha por código, na ordem do exercício mais recente em que aparece
        Map<String, LinhaComparativa> mescladas = new LinkedHashMap<>();
        Map<String, Integer> ordem = new LinkedHashMap<>();
        List<Integer> anos = new ArrayList<>(linhasPorAno.keySet());
        for (int i = anos.size() - 1; i >= 0; i--) {
            for (LinhaDemonstracao l : linhasPorAno.get(anos.get(i))) {
                mescladas.computeIfAbsent(l.codigo(), c -> new LinhaComparativa(l.codigo(), l.descricao(), l.nivel(),
                        l.codigoSuperior(), l.totalizadora(), l.grupo(), new TreeMap<>()));
                ordem.putIfAbsent(l.codigo(), l.ordem());
            }
        }
        for (int i = 0; i < anos.size(); i++) {
            int ano = anos.get(i);
            List<LinhaDemonstracao> linhas = linhasPorAno.get(ano);
            Map<String, BigDecimal> anteriores = i == 0 ? Map.of() : valoresPorCodigo(linhasPorAno.get(anos.get(i - 1)));
            for (LinhaDemonstracao l : linhas) {
                BigDecimal base = baseVertical(tipo, origem, linhas, l);
                mescladas.get(l.codigo()).valores().put(ano, new Valor(idsPorAno.get(ano).get(l.codigo()), l.valorFinal(),
                        l.valorInicial(), percentual(l.valorFinal(), base), variacao(l.valorFinal(), anteriores.get(l.codigo()))));
            }
        }
        List<LinhaComparativa> linhas = mescladas.values().stream()
                .sorted(Comparator.comparingInt((LinhaComparativa l) -> ordem.get(l.codigo())).thenComparing(LinhaComparativa::codigo))
                .toList();
        return new Comparativo(tipo, origem, List.copyOf(porAno.values()), linhas, conferencia(clienteId, tipo, porAno.keySet()));
    }

    /** Escrituração vigente de cada exercício; com ECD e planilha no mesmo ano, vale a ECD (e a mais recente). */
    private Map<Integer, Exercicio> demonstracoes(long clienteId, String tipo, String origem, List<Integer> exercicios) {
        Map<Integer, Exercicio> porAno = new TreeMap<>();
        jdbc.query("""
                SELECT d.exercicio, d.id, e.documento_id, doc.nome_original_arquivo, e.origem, d.data_inicio, d.data_fim,
                       d.tabela_referencial
                FROM demonstracao d
                JOIN escrituracao_contabil e ON e.id = d.escrituracao_id
                JOIN documento_fiscal doc ON doc.id = e.documento_id
                WHERE e.cliente_id = ? AND e.situacao = 'VIGENTE' AND d.tipo = ? AND d.origem = ?
                ORDER BY d.exercicio, CASE WHEN e.origem = 'ECD' THEN 0 ELSE 1 END, e.id DESC""", rs -> {
            int ano = rs.getInt(1);
            if (exercicios == null || exercicios.isEmpty() || exercicios.contains(ano)) {
                porAno.putIfAbsent(ano, new Exercicio(ano, rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5),
                        rs.getDate(6) == null ? null : rs.getDate(6).toLocalDate(), rs.getDate(7).toLocalDate(), rs.getString(8)));
            }
        }, clienteId, tipo, origem);
        return porAno;
    }

    private List<LinhaDemonstracao> linhas(long demonstracaoId, Map<String, Long> ids) {
        return jdbc.query("""
                SELECT id, codigo, descricao, nivel, codigo_superior, ordem, totalizadora, grupo, valor_inicial, valor_final,
                       registro, linha
                FROM linha_demonstracao WHERE demonstracao_id = ? ORDER BY ordem, id""", (rs, i) -> {
            ids.put(rs.getString(2), rs.getLong(1));
            return new LinhaDemonstracao(rs.getString(2), rs.getString(3), (Integer) rs.getObject(4), rs.getString(5),
                    rs.getInt(6), rs.getBoolean(7), rs.getString(8), rs.getBigDecimal(9), rs.getBigDecimal(10),
                    rs.getString(11), (Long) rs.getObject(12));
        }, demonstracaoId);
    }

    /**
     * AV: BP sobre o total do grupo (ativo ou passivo + PL); DRE sobre a receita líquida.
     * ponytail: na DRE publicada a receita líquida é achada pela descrição; sem ela, AV fica vazia.
     */
    private static BigDecimal baseVertical(String tipo, String origem, List<LinhaDemonstracao> linhas, LinhaDemonstracao l) {
        if ("BP".equals(tipo)) {
            return l.grupo() == null ? null : MontagemReferencial.topo(linhas, l.grupo());
        }
        return linhas.stream()
                .filter(x -> "REFERENCIAL".equals(origem) ? RECEITA_LIQUIDA_REFERENCIAL.contains(x.codigo())
                        : RECEITA_LIQUIDA.matcher(x.descricao()).find())
                .map(LinhaDemonstracao::valorFinal).findFirst().orElse(null);
    }

    private static Map<String, BigDecimal> valoresPorCodigo(List<LinhaDemonstracao> linhas) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        linhas.forEach(l -> m.put(l.codigo(), l.valorFinal()));
        return m;
    }

    /** valor / base × 100 (mesmo sinal = positivo). */
    private static BigDecimal percentual(BigDecimal valor, BigDecimal base) {
        if (valor == null || base == null || base.signum() == 0) {
            return null;
        }
        return valor.multiply(CEM).divide(base, 2, RoundingMode.HALF_EVEN);
    }

    /** AH: (atual − anterior) / |anterior| × 100. */
    private static BigDecimal variacao(BigDecimal atual, BigDecimal anterior) {
        if (atual == null || anterior == null || anterior.signum() == 0) {
            return null;
        }
        return atual.subtract(anterior).multiply(CEM).divide(anterior.abs(), 2, RoundingMode.HALF_EVEN);
    }

    /** Referencial x publicada por exercício: ativo e passivo + PL (BP) ou resultado (DRE). */
    private List<Conferencia> conferencia(long clienteId, String tipo, Set<Integer> anos) {
        Map<Integer, Exercicio> referenciais = demonstracoes(clienteId, tipo, "REFERENCIAL", List.copyOf(anos));
        Map<Integer, Exercicio> publicadas = demonstracoes(clienteId, tipo, "PUBLICADA", List.copyOf(anos));
        List<Conferencia> resultado = new ArrayList<>();
        for (int ano : anos) {
            Exercicio ref = referenciais.get(ano);
            Exercicio pub = publicadas.get(ano);
            if (ref == null || pub == null) {
                continue;
            }
            List<LinhaDemonstracao> r = linhas(ref.demonstracaoId(), new LinkedHashMap<>());
            List<LinhaDemonstracao> p = linhas(pub.demonstracaoId(), new LinkedHashMap<>());
            if ("BP".equals(tipo)) {
                resultado.add(item(ano, "Ativo", MontagemReferencial.topo(r, "A"), MontagemReferencial.topo(p, "A")));
                resultado.add(item(ano, "Passivo + PL", MontagemReferencial.topo(r, "P"), MontagemReferencial.topo(p, "P")));
            } else {
                resultado.add(item(ano, "Resultado do exercício", MontagemReferencial.topo(r, null), MontagemReferencial.topo(p, null)));
            }
        }
        return resultado;
    }

    private static Conferencia item(int ano, String item, BigDecimal referencial, BigDecimal publicada) {
        return new Conferencia(ano, item, referencial, publicada, referencial.subtract(publicada));
    }

    /**
     * De onde vem um valor: na publicada, o registro e a linha (J100/J150); no referencial, as contas da empresa que
     * compõem a linha (e as linhas abaixo dela) com os saldos usados, cada um com registro e linha do arquivo.
     */
    public Origem origem(long clienteId, long linhaId, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        Map<String, Object> l = jdbc.queryForList("""
                SELECT l.id, l.codigo, l.descricao, d.tipo, d.origem, e.documento_id, doc.nome_original_arquivo, l.registro,
                       l.linha, d.id AS demonstracao_id, d.data_fim, e.id AS escrituracao_id
                FROM linha_demonstracao l
                JOIN demonstracao d ON d.id = l.demonstracao_id
                JOIN escrituracao_contabil e ON e.id = d.escrituracao_id
                JOIN documento_fiscal doc ON doc.id = e.documento_id
                WHERE l.id = ? AND e.cliente_id = ?""", linhaId, clienteId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Linha de demonstração não encontrada com a ID: " + linhaId));
        String tipo = (String) l.get("tipo");
        List<ContaOrigem> contas = "REFERENCIAL".equals(l.get("origem"))
                ? contasDaLinha((Long) l.get("demonstracao_id"), (String) l.get("codigo"), tipo, (Long) l.get("escrituracao_id"),
                ((java.sql.Date) l.get("data_fim")).toLocalDate())
                : List.of();
        return new Origem(linhaId, (String) l.get("codigo"), (String) l.get("descricao"), tipo, (String) l.get("origem"),
                (Long) l.get("documento_id"), (String) l.get("nome_original_arquivo"), (String) l.get("registro"),
                (Long) l.get("linha"), contas);
    }

    private List<ContaOrigem> contasDaLinha(long demonstracaoId, String codigo, String tipo, long escrituracaoId, LocalDate fim) {
        Map<Long, ContaOrigem> contas = new LinkedHashMap<>();
        jdbc.query("""
                SELECT DISTINCT c.id, c.codigo, c.nome, c.codigo_referencial
                FROM linha_demonstracao_conta lc
                JOIN linha_demonstracao l ON l.id = lc.linha_id
                JOIN conta_contabil c ON c.id = lc.conta_id
                WHERE l.demonstracao_id = ? AND (l.codigo = ? OR l.codigo LIKE ?)
                ORDER BY c.codigo""", rs -> {
            contas.put(rs.getLong(1), new ContaOrigem(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                    BigDecimal.ZERO, new ArrayList<>()));
        }, demonstracaoId, codigo, codigo + ".%");
        if (contas.isEmpty()) {
            return List.of();
        }
        // os mesmos saldos que a montagem usou: BP no fim do exercício; DRE no resultado antes do encerramento (I355 ou,
        // no balancete em planilha, o saldo das contas de resultado)
        List<Object> parametros = new ArrayList<>(List.of(escrituracaoId));
        String filtro;
        if ("BP".equals(tipo)) {
            filtro = "s.origem IN ('I155', 'PLANILHA') AND s.data_fim = ?";
            parametros.add(java.sql.Date.valueOf(fim));
        } else {
            filtro = "s.origem IN ('I355', 'PLANILHA')";
        }
        String ids = String.join(",", contas.keySet().stream().map(String::valueOf).toList()); // ids numéricos do banco
        jdbc.query("SELECT s.conta_id, s.registro, s.linha, s.saldo_final, s.data_fim, s.centro_custo FROM saldo_conta s "
                + "WHERE s.escrituracao_id = ? AND s.conta_id IN (" + ids + ") AND " + filtro + " ORDER BY s.linha", rs -> {
            contas.get(rs.getLong(1)).saldos().add(new SaldoOrigem(rs.getString(2), rs.getLong(3), rs.getBigDecimal(4),
                    rs.getDate(5).toLocalDate(), rs.getString(6)));
        }, parametros.toArray());
        return contas.values().stream().map(c -> new ContaOrigem(c.contaId(), c.codigo(), c.nome(), c.codigoReferencial(),
                c.saldos().stream().map(SaldoOrigem::valor).reduce(BigDecimal.ZERO, BigDecimal::add), c.saldos())).toList();
    }
}
