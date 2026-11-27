package com.fiscalhub.backend.capacidade;

import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.AcessoRecursoService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/**
 * DFC pelo método direto a partir dos lançamentos da ECD (I200/I250), relidos do arquivo guardado (ReleituraSped).
 * <ul>
 *   <li>caixa e equivalentes: contas cujo código referencial começa por 1.01.01 (disponibilidades);</li>
 *   <li>cada lançamento que movimenta caixa: a variação líquida do caixa é rateada entre as contrapartidas do mesmo
 *   sentido (crédito, se entrou caixa; débito, se saiu), na proporção dos valores; a última leva o resto do
 *   arredondamento. Só contas de caixa (transferência entre bancos): ignorado;</li>
 *   <li>categoria da contrapartida: de-para do plano referencial (prefixo mais longo; padrão em
 *   resources/capacidade/dfc-depara.txt, sobreposto por escritório);</li>
 *   <li>conferência: a soma da DFC tem que bater com a variação dos saldos de caixa (I155); diferença vira alerta.</li>
 * </ul>
 * ponytail: cache em memória dos movimentos de caixa por escrituração (a ECD vigente não muda; uma nova vira outra
 * escrituração), limitado a 32; persistir os movimentos se os arquivos forem grandes demais para reler.
 */
@Service
@Transactional(readOnly = true)
public class DfcDiretaService {

    public static final Map<String, String> ATIVIDADE = Map.ofEntries(
            Map.entry("RECEBIMENTO_CLIENTES", "OPERACIONAL"), Map.entry("PAGAMENTO_FORNECEDORES", "OPERACIONAL"),
            Map.entry("SALARIOS_ENCARGOS", "OPERACIONAL"), Map.entry("TRIBUTOS", "OPERACIONAL"),
            Map.entry("OUTROS_OPERACIONAIS", "OPERACIONAL"), Map.entry("IMOBILIZADO", "INVESTIMENTO"),
            Map.entry("INVESTIMENTOS", "INVESTIMENTO"), Map.entry("EMPRESTIMOS", "FINANCIAMENTO"),
            Map.entry("PARCELAMENTOS_TRIBUTARIOS", "FINANCIAMENTO"), Map.entry("CAPITAL_DIVIDENDOS", "FINANCIAMENTO"),
            Map.entry("OUTROS", "OUTROS"));
    public static final Map<String, String> ROTULO = Map.ofEntries(
            Map.entry("RECEBIMENTO_CLIENTES", "Recebimento de clientes"), Map.entry("PAGAMENTO_FORNECEDORES", "Pagamento a fornecedores"),
            Map.entry("SALARIOS_ENCARGOS", "Salários e encargos"), Map.entry("TRIBUTOS", "Tributos"),
            Map.entry("OUTROS_OPERACIONAIS", "Outros operacionais"), Map.entry("IMOBILIZADO", "Imobilizado"),
            Map.entry("INVESTIMENTOS", "Investimentos"), Map.entry("EMPRESTIMOS", "Empréstimos e financiamentos"),
            Map.entry("PARCELAMENTOS_TRIBUTARIOS", "Parcelamentos tributários"), Map.entry("CAPITAL_DIVIDENDOS", "Capital e dividendos"),
            Map.entry("OUTROS", "Outros"));
    private static final List<String> ORDEM = List.of("RECEBIMENTO_CLIENTES", "PAGAMENTO_FORNECEDORES", "SALARIOS_ENCARGOS",
            "TRIBUTOS", "OUTROS_OPERACIONAIS", "IMOBILIZADO", "INVESTIMENTOS", "EMPRESTIMOS", "PARCELAMENTOS_TRIBUTARIOS",
            "CAPITAL_DIVIDENDOS", "OUTROS");
    private static final String CAIXA = "1.01.01";

    /** Parte de um lançamento atribuída a uma contrapartida (valor com sinal: + entrou caixa). */
    public record Movimento(String lancamento, LocalDate data, long linha, String contaReferencial, BigDecimal valor) {
    }

    public record LancamentoOrigem(String numero, LocalDate data, long linha, BigDecimal valor) {
    }

    /** meses: 1 a 12 -> valor; origens: os lançamentos que compõem a linha (registro I200 e linha do arquivo). */
    public record LinhaDfc(String atividade, String categoria, String rotulo, Map<Integer, BigDecimal> meses, BigDecimal total,
                           List<LancamentoOrigem> origens) {
    }

    /** variacaoSaldos: dos saldos de caixa (I155); diferenca = variacaoDfc − variacaoSaldos. */
    public record Dfc(int exercicio, long documentoId, String arquivo, List<LinhaDfc> linhas, Map<String, BigDecimal> atividades,
                      BigDecimal variacaoDfc, BigDecimal variacaoSaldos, BigDecimal diferenca, String alerta) {
    }

    public record DeParaItem(String prefixo, String categoria, boolean doEscritorio) {
    }

    private final JdbcTemplate jdbc;
    private final AcessoRecursoService acesso;
    private final ReleituraSped releitura;
    private final Map<String, String> deparaPadrao = carregarDepara();
    private final Map<Long, List<Movimento>> cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, List<Movimento>> e) {
            return size() > 32;
        }
    });

    public DfcDiretaService(JdbcTemplate jdbc, AcessoRecursoService acesso, ReleituraSped releitura) {
        this.jdbc = jdbc;
        this.acesso = acesso;
        this.releitura = releitura;
    }

    /** Exercícios com ECD vigente (só a ECD tem lançamentos). */
    public List<Integer> exercicios(long clienteId, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        return jdbc.queryForList("""
                SELECT DISTINCT extract(YEAR FROM data_fim)::int FROM escrituracao_contabil
                WHERE cliente_id = ? AND origem = 'ECD' AND situacao = 'VIGENTE' ORDER BY 1""", Integer.class, clienteId);
    }

    public Dfc dfc(long clienteId, int exercicio, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        return dfc(clienteId, exercicio, ua.escritorioId()).orElseThrow(() -> new BusinessRuleException(
                "DFC de " + exercicio + " não disponível: importe a ECD do exercício (a DFC direta vem dos lançamentos I200/I250)."));
    }

    /** Sem checagem de acesso (quem chama já checou). Vazio: sem ECD vigente no exercício. */
    public Optional<Dfc> dfc(long clienteId, int exercicio, long escritorioId) {
        List<Map<String, Object>> esc = jdbc.queryForList("""
                SELECT e.id, e.documento_id, d.nome_original_arquivo FROM escrituracao_contabil e
                JOIN documento_fiscal d ON d.id = e.documento_id
                WHERE e.cliente_id = ? AND e.origem = 'ECD' AND e.situacao = 'VIGENTE' AND extract(YEAR FROM e.data_fim) = ?
                ORDER BY e.data_fim DESC, e.id DESC LIMIT 1""", clienteId, exercicio);
        if (esc.isEmpty()) {
            return Optional.empty();
        }
        long escrituracao = ((Number) esc.getFirst().get("id")).longValue();
        Map<String, String> depara = depara(escritorioId);
        Map<String, Map<Integer, BigDecimal>> meses = new TreeMap<>(Comparator.comparingInt(ORDEM::indexOf));
        Map<String, List<LancamentoOrigem>> origens = new HashMap<>();
        for (Movimento m : cache.computeIfAbsent(escrituracao, this::movimentos)) {
            String categoria = categoria(m.contaReferencial(), depara);
            meses.computeIfAbsent(categoria, c -> new TreeMap<>()).merge(m.data().getMonthValue(), m.valor(), BigDecimal::add);
            origens.computeIfAbsent(categoria, c -> new ArrayList<>()).add(new LancamentoOrigem(m.lancamento(), m.data(), m.linha(), m.valor()));
        }
        List<LinhaDfc> linhas = new ArrayList<>();
        Map<String, BigDecimal> atividades = new LinkedHashMap<>();
        meses.forEach((c, porMes) -> {
            BigDecimal total = porMes.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            linhas.add(new LinhaDfc(ATIVIDADE.get(c), c, ROTULO.get(c), porMes, total, origens.get(c)));
            atividades.merge(ATIVIDADE.get(c), total, BigDecimal::add);
        });
        BigDecimal variacaoDfc = atividades.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal variacaoSaldos = variacaoSaldosCaixa(escrituracao);
        BigDecimal diferenca = variacaoSaldos == null ? null : variacaoDfc.subtract(variacaoSaldos);
        String alerta = variacaoSaldos == null ? "Sem saldos de caixa (I155) para conferir a DFC."
                : diferenca.signum() != 0 ? "A variação do caixa pela DFC (" + variacaoDfc.toPlainString() + ") difere da variação dos "
                + "saldos de caixa no I155 (" + variacaoSaldos.toPlainString() + "): diferença de " + diferenca.toPlainString()
                + ". Confira lançamentos de caixa sem contrapartida ou contas de caixa fora do grupo 1.01.01." : null;
        return Optional.of(new Dfc(exercicio, ((Number) esc.getFirst().get("documento_id")).longValue(),
                (String) esc.getFirst().get("nome_original_arquivo"), linhas, atividades, variacaoDfc, variacaoSaldos, diferenca, alerta));
    }

    /** Movimentos de caixa de uma escrituração, relidos do arquivo: um por contrapartida, já rateado. */
    List<Movimento> movimentos(long escrituracaoId) {
        Map<String, String> referencial = new HashMap<>();
        jdbc.query("SELECT codigo, codigo_referencial FROM conta_contabil WHERE escrituracao_id = ? AND codigo_referencial IS NOT NULL",
                rs -> { referencial.put(rs.getString(1), rs.getString(2)); }, escrituracaoId);
        List<Movimento> movimentos = new ArrayList<>();
        List<Partida> partidas = new ArrayList<>();
        String[] lancamento = new String[3]; // número, data, linha
        releitura.ler(escrituracaoId, Set.of("I200", "I250"), (c, linha) -> {
            if ("I200".equals(c[1])) {
                ratear(lancamento, partidas, movimentos);
                partidas.clear();
                lancamento[0] = c[2];
                lancamento[1] = c[3];
                lancamento[2] = String.valueOf(linha);
            } else {
                BigDecimal valor = com.fiscalhub.backend.sped.LeitorSped.decimal(c[4]);
                partidas.add(new Partida(referencial.get(c[2]), "D".equals(c[5]) ? valor : valor.negate()));
            }
        });
        ratear(lancamento, partidas, movimentos);
        return List.copyOf(movimentos);
    }

    /** Uma partida do lançamento: conta referencial e valor com sinal (+ débito). */
    record Partida(String referencial, BigDecimal valor) {
        boolean caixa() {
            return referencial != null && (referencial.equals(CAIXA) || referencial.startsWith(CAIXA + "."));
        }
    }

    /** Rateio: a variação do caixa vai para as contrapartidas do mesmo sentido, na proporção dos valores. */
    static void ratear(String[] lancamento, List<Partida> partidas, List<Movimento> saida) {
        if (lancamento[0] == null || partidas.isEmpty()) {
            return;
        }
        BigDecimal caixa = partidas.stream().filter(Partida::caixa).map(Partida::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (caixa.signum() == 0) {
            return; // não movimenta caixa ou só transfere entre contas de caixa
        }
        // entrou caixa (débito): as contrapartidas são os créditos; saiu: os débitos
        List<Partida> contra = partidas.stream().filter(p -> !p.caixa() && p.valor().signum() == -caixa.signum()).toList();
        BigDecimal total = contra.stream().map(p -> p.valor().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate data = com.fiscalhub.backend.sped.LeitorSped.data(lancamento[1]);
        long linha = Long.parseLong(lancamento[2]);
        BigDecimal resto = caixa;
        for (int i = 0; i < contra.size(); i++) {
            BigDecimal parte = i == contra.size() - 1 ? resto
                    : caixa.multiply(contra.get(i).valor().abs()).divide(total, 2, RoundingMode.HALF_UP);
            saida.add(new Movimento(lancamento[0], data, linha, contra.get(i).referencial(), parte));
            resto = resto.subtract(parte);
        }
    }

    /** Variação dos saldos das contas de caixa no I155: saldo final do último período − inicial do primeiro. */
    private BigDecimal variacaoSaldosCaixa(long escrituracaoId) {
        Map<String, Object> r = jdbc.queryForMap("""
                SELECT sum(s.saldo_final) FILTER (WHERE s.data_fim = max_fim) AS fim, sum(s.saldo_inicial) FILTER (WHERE s.data_inicio = min_ini) AS ini
                FROM saldo_conta s JOIN conta_contabil c ON c.id = s.conta_id,
                     (SELECT max(data_fim) AS max_fim, min(data_inicio) AS min_ini FROM saldo_conta WHERE escrituracao_id = ? AND origem = 'I155') p
                WHERE s.escrituracao_id = ? AND s.origem = 'I155' AND (c.codigo_referencial = ? OR c.codigo_referencial LIKE ?)""",
                escrituracaoId, escrituracaoId, CAIXA, CAIXA + ".%");
        BigDecimal fim = (BigDecimal) r.get("fim");
        BigDecimal ini = (BigDecimal) r.get("ini");
        return fim == null || ini == null ? null : fim.subtract(ini);
    }

    // ---------------------------------------------------------------- de-para

    public List<DeParaItem> deparaDoEscritorio(UsuarioAutenticado ua) {
        Map<String, String> doEscritorio = sobreposicoes(ua.escritorioId());
        Map<String, String> todos = depara(ua.escritorioId());
        return todos.entrySet().stream().map(e -> new DeParaItem(e.getKey(), e.getValue(), doEscritorio.containsKey(e.getKey()))).toList();
    }

    @Transactional
    public List<DeParaItem> alterarDepara(String prefixo, String categoria, UsuarioAutenticado ua) {
        if (!prefixo.matches("[0-9]+(\\.[0-9]+)*") || !ATIVIDADE.containsKey(categoria)) {
            throw new BusinessRuleException("Use um prefixo do plano referencial (ex.: 2.01.01.09) e uma categoria da DFC: " + ORDEM + " (caixa e equivalentes é o grupo 1.01.01, fixo).");
        }
        jdbc.update("""
                INSERT INTO dfc_depara_escritorio (escritorio_id, prefixo, categoria) VALUES (?, ?, ?)
                ON CONFLICT (escritorio_id, prefixo) DO UPDATE SET categoria = EXCLUDED.categoria""", ua.escritorioId(), prefixo, categoria);
        return deparaDoEscritorio(ua);
    }

    @Transactional
    public List<DeParaItem> removerDepara(String prefixo, UsuarioAutenticado ua) {
        jdbc.update("DELETE FROM dfc_depara_escritorio WHERE escritorio_id = ? AND prefixo = ?", ua.escritorioId(), prefixo);
        return deparaDoEscritorio(ua);
    }

    private Map<String, String> depara(long escritorioId) {
        Map<String, String> m = new TreeMap<>(deparaPadrao);
        m.putAll(sobreposicoes(escritorioId));
        return m;
    }

    private Map<String, String> sobreposicoes(long escritorioId) {
        Map<String, String> m = new TreeMap<>();
        jdbc.query("SELECT prefixo, categoria FROM dfc_depara_escritorio WHERE escritorio_id = ?",
                rs -> { m.put(rs.getString(1), rs.getString(2)); }, escritorioId);
        return m;
    }

    /** Categoria pelo prefixo mais longo (por nível do código, não por caractere); sem conta referencial: OUTROS. */
    static String categoria(String referencial, Map<String, String> depara) {
        if (referencial == null) {
            return "OUTROS";
        }
        String melhor = null;
        for (String p : depara.keySet()) {
            if ((referencial.equals(p) || referencial.startsWith(p + ".")) && (melhor == null || p.length() > melhor.length())) {
                melhor = p;
            }
        }
        return melhor == null ? "OUTROS" : depara.get(melhor);
    }

    private static Map<String, String> carregarDepara() {
        try {
            Map<String, String> m = new LinkedHashMap<>();
            for (String l : new ClassPathResource("capacidade/dfc-depara.txt").getContentAsString(StandardCharsets.UTF_8).split("\r?\n")) {
                if (!l.isBlank() && !l.startsWith("#")) {
                    String[] f = l.split("\\|");
                    m.put(f[0].strip(), f[1].strip());
                }
            }
            return Map.copyOf(m);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
