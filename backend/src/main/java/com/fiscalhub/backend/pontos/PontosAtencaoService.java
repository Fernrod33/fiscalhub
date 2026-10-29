package com.fiscalhub.backend.pontos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.importacao.DocumentoProcessado;
import com.fiscalhub.backend.pontos.RegraPontoAtencao.*;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.AcessoRecursoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Motor de pontos de atenção: roda as regras (beans {@link RegraPontoAtencao}) para um cliente, grava os achados pela chave
 * de identidade e cuida do ciclo de vida — o que deixou de ocorrer vira RESOLVIDO ("condição não ocorre mais"), o
 * DESCARTADO não volta enquanto a evidência for a mesma, o RESOLVIDO volta como NOVO se a condição reaparecer. Roda
 * depois de cada importação processada (evento do pipeline, agrupado por cliente) e sob demanda.
 */
@Service
public class PontosAtencaoService {

    private static final Logger log = LoggerFactory.getLogger(PontosAtencaoService.class);
    private static final List<String> ABERTOS = List.of("NOVO", "EM_ANALISE");
    private static final Map<String, Integer> ORDEM = Map.of("CRITICA", 0, "ALTA", 1, "MEDIA", 2, "INFO", 3);

    public record DefinicaoArquivo(String codigo, String titulo, Categoria categoria, Severidade severidade, String vigenciaInicio,
                                   String fundamento, String recomendacao, Map<String, Object> parametros, boolean conferido) {
    }

    private record Arquivo(String versao, String nota, List<DefinicaoArquivo> regras) {
    }

    public record Historico(String de, String para, String comentario, String usuario, LocalDateTime em) {
    }

    public record PontoAtencao(long id, String regra, String versaoRegra, Severidade severidade, Categoria categoria, String titulo,
                               String descricao, String fundamento, String recomendacao, BigDecimal valor, List<Evidencia> evidencias,
                               String estado, LocalDateTime detectadoEm, LocalDateTime confirmadoEm, List<Historico> historico) {
    }

    public record RegraNaoAvaliada(String codigo, String titulo, String falta) {
    }

    public record ResultadoAvaliacao(LocalDateTime avaliadoEm, int abertos, int novos, int resolvidos, List<RegraNaoAvaliada> naoAvaliadas) {
    }

    /** Uma regra para a tela de configuração: o padrão (dado versionado) e o que o escritório mudou. */
    public record ConfiguracaoRegra(String codigo, String titulo, Categoria categoria, String versao, Severidade severidadePadrao,
                                    Map<String, Object> parametrosPadrao, Severidade severidade, Map<String, Object> parametros,
                                    String fundamento, String recomendacao, boolean conferido, String atualizadoPor, LocalDateTime atualizadoEm) {
    }

    public record AlteracaoRegra(Map<String, Object> parametros, Severidade severidade) {
    }

    public record ClientePainel(long clienteId, String razaoSocial, String cnpj, int criticos, int altos, int medios, int info,
                                int importacoesPendentes, int importacoesComErro, LocalDateTime avaliadoEm) {
    }

    public record SimulacaoRecente(long id, long clienteId, String razaoSocial, LocalDateTime criadaEm, String recomendacao, BigDecimal custo) {
    }

    public record Painel(List<ClientePainel> clientes, List<SimulacaoRecente> simulacoesRecentes) {
    }

    private final List<RegraPontoAtencao> regras;
    private final ContextoPontos.Servicos servicos;
    private final AcessoRecursoService acesso;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate transacao;
    private final Arquivo arquivo;
    private final Map<Long, Object> travas = new ConcurrentHashMap<>();
    private final Set<Long> pendentes = ConcurrentHashMap.newKeySet();
    // ponytail: uma thread local agrupando por cliente; fila externa quando houver mais de uma instância (como o pipeline)
    private final ExecutorService fila = Executors.newSingleThreadExecutor(r -> new Thread(r, "pontos-atencao"));

    public PontosAtencaoService(List<RegraPontoAtencao> regras, ContextoPontos.Servicos servicos, AcessoRecursoService acesso,
                                JdbcTemplate jdbc, ObjectMapper json, TransactionTemplate transacao) {
        this.regras = regras.stream().sorted(Comparator.comparing(RegraPontoAtencao::codigo)).toList();
        this.servicos = servicos;
        this.acesso = acesso;
        this.jdbc = jdbc;
        this.json = json;
        this.transacao = transacao;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("pontos/regras.json")) {
            this.arquivo = new ObjectMapper().readValue(in, Arquivo.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------------------------------------ avaliação

    /** Depois de cada importação processada: reavalia o cliente do documento, em segundo plano, uma vez por rajada. */
    @EventListener
    public void aoProcessar(DocumentoProcessado e) {
        List<Map<String, Object>> doc = jdbc.queryForList("""
                SELECT c.id AS cliente, c.escritorio_id AS escritorio FROM documento_fiscal d JOIN cliente c ON c.id = d.cliente_id
                WHERE d.id = ?""", e.documentoId());
        if (doc.isEmpty()) {
            return; // documento ainda sem cliente
        }
        long cliente = ((Number) doc.getFirst().get("cliente")).longValue();
        long escritorio = ((Number) doc.getFirst().get("escritorio")).longValue();
        if (pendentes.add(cliente)) {
            fila.submit(() -> {
                pendentes.remove(cliente);
                try {
                    reavaliar(cliente, new UsuarioAutenticado(null, "sistema", escritorio, Papel.ADMIN, 0));
                } catch (RuntimeException ex) {
                    log.error("Falha ao reavaliar os pontos de atenção do cliente {}", cliente, ex);
                }
            });
        }
    }

    public ResultadoAvaliacao reavaliar(long clienteId, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        synchronized (travas.computeIfAbsent(clienteId, k -> new Object())) {
            // as regras leem fora da transação de escrita (cada serviço tem a sua, de leitura)
            ContextoPontos ctx = new ContextoPontos(clienteId, ua, servicos);
            Map<String, Achado> achados = new LinkedHashMap<>();
            Map<String, Definicao> defs = new HashMap<>();
            Set<String> avaliadas = new HashSet<>();
            List<RegraNaoAvaliada> nao = new ArrayList<>();
            for (RegraPontoAtencao r : regras) {
                Definicao def = definicao(r.codigo(), ua.escritorioId());
                defs.put(r.codigo(), def);
                Avaliacao a;
                try {
                    a = r.avaliar(ctx, def);
                } catch (BusinessRuleException e) {
                    a = Avaliacao.naoAvaliada(e.getMessage());
                }
                if (a.naoAvaliada() != null) {
                    nao.add(new RegraNaoAvaliada(r.codigo(), def.titulo(), a.naoAvaliada()));
                }
                if (a.naoAvaliada() == null || !a.achados().isEmpty()) {
                    avaliadas.add(r.codigo());
                }
                a.achados().forEach(x -> achados.put(r.codigo() + "|" + x.chave(), x));
            }
            return transacao.execute(t -> gravar(clienteId, achados, defs, avaliadas, nao));
        }
    }

    private ResultadoAvaliacao gravar(long clienteId, Map<String, Achado> achados, Map<String, Definicao> defs, Set<String> avaliadas,
                                      List<RegraNaoAvaliada> nao) {
        LocalDateTime agora = LocalDateTime.now();
        Map<String, Object[]> existentes = new HashMap<>(); // chave -> [id, estado, hash]
        jdbc.query("SELECT chave, id, estado, hash_evidencia FROM ponto_atencao WHERE cliente_id = ?",
                rs -> { existentes.put(rs.getString(1), new Object[]{rs.getLong(2), rs.getString(3), rs.getString(4)}); }, clienteId);
        int novos = 0;
        int resolvidos = 0;
        for (Map.Entry<String, Achado> e : achados.entrySet()) {
            String regra = e.getKey().substring(0, e.getKey().indexOf('|'));
            Definicao def = defs.get(regra);
            Achado a = e.getValue();
            String evidencias = escrever(a.evidencias());
            String hash = sha256(evidencias + "|" + a.valor());
            Severidade sev = a.severidade() != null ? a.severidade() : def.severidade();
            Object[] atual = existentes.remove(e.getKey());
            if (atual == null) {
                Long id = jdbc.queryForObject("""
                        INSERT INTO ponto_atencao (cliente_id, regra, versao_regra, chave, severidade, categoria, titulo, descricao, fundamento,
                            recomendacao, valor, evidencias, hash_evidencia, estado, detectado_em, confirmado_em)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 'NOVO', ?, ?) RETURNING id""", Long.class, clienteId, regra, def.versao(),
                        e.getKey(), sev.name(), def.categoria().name(), corta(a.titulo(), 300), corta(a.descricao(), 2000), corta(def.fundamento(), 1000),
                        corta(def.recomendacao(), 1000), a.valor(), evidencias, hash, Timestamp.valueOf(agora), Timestamp.valueOf(agora));
                historico(id, null, "NOVO", "detectado", null, agora);
                novos++;
                continue;
            }
            long id = (Long) atual[0];
            String estado = (String) atual[1];
            boolean mudou = !hash.equals(atual[2]);
            String novoEstado = estado;
            if ("RESOLVIDO".equals(estado) || ("DESCARTADO".equals(estado) && mudou)) {
                novoEstado = "NOVO";
                historico(id, estado, "NOVO", "RESOLVIDO".equals(estado) ? "a condição voltou a ocorrer" : "a evidência mudou", null, agora);
                novos++;
            }
            jdbc.update("""
                    UPDATE ponto_atencao SET versao_regra = ?, severidade = ?, titulo = ?, descricao = ?, fundamento = ?, recomendacao = ?, valor = ?,
                        evidencias = ?::jsonb, hash_evidencia = ?, estado = ?, confirmado_em = ? WHERE id = ?""", def.versao(), sev.name(),
                    corta(a.titulo(), 300), corta(a.descricao(), 2000), corta(def.fundamento(), 1000), corta(def.recomendacao(), 1000), a.valor(),
                    evidencias, hash, novoEstado, Timestamp.valueOf(agora), id);
        }
        for (Map.Entry<String, Object[]> e : existentes.entrySet()) {
            String regra = e.getKey().substring(0, e.getKey().indexOf('|'));
            if (avaliadas.contains(regra) && ABERTOS.contains((String) e.getValue()[1])) {
                long id = (Long) e.getValue()[0];
                jdbc.update("UPDATE ponto_atencao SET estado = 'RESOLVIDO' WHERE id = ?", id);
                historico(id, (String) e.getValue()[1], "RESOLVIDO", "condição não ocorre mais", null, agora);
                resolvidos++;
            }
        }
        jdbc.update("""
                INSERT INTO avaliacao_pontos (cliente_id, avaliado_em, nao_avaliadas) VALUES (?, ?, ?::jsonb)
                ON CONFLICT (cliente_id) DO UPDATE SET avaliado_em = EXCLUDED.avaliado_em, nao_avaliadas = EXCLUDED.nao_avaliadas""",
                clienteId, Timestamp.valueOf(agora), escrever(nao));
        Integer abertos = jdbc.queryForObject("SELECT count(*) FROM ponto_atencao WHERE cliente_id = ? AND estado IN ('NOVO', 'EM_ANALISE')",
                Integer.class, clienteId);
        return new ResultadoAvaliacao(agora, abertos, novos, resolvidos, nao);
    }

    private void historico(long ponto, String de, String para, String comentario, Long usuario, LocalDateTime em) {
        jdbc.update("INSERT INTO ponto_atencao_historico (ponto_id, de, para, comentario, usuario_id, em) VALUES (?, ?, ?, ?, ?, ?)",
                ponto, de, para, comentario, usuario, Timestamp.valueOf(em));
    }

    // ------------------------------------------------------------------------------------------------ consultas e estado

    public List<PontoAtencao> listar(long clienteId, Severidade severidade, Categoria categoria, String estado, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        return jdbc.query("""
                SELECT id, regra, versao_regra, severidade, categoria, titulo, descricao, fundamento, recomendacao, valor, evidencias::text,
                       estado, detectado_em, confirmado_em FROM ponto_atencao
                WHERE cliente_id = ? AND (?::text IS NULL OR severidade = ?) AND (?::text IS NULL OR categoria = ?) AND (?::text IS NULL OR estado = ?)""",
                (rs, i) -> ponto(rs, List.of()), clienteId, nome(severidade), nome(severidade), nome(categoria), nome(categoria), estado, estado)
                .stream().sorted(Comparator.comparing((PontoAtencao p) -> ORDEM.get(p.severidade().name()))
                        .thenComparing(p -> Objects.requireNonNullElse(p.valor(), BigDecimal.ZERO), Comparator.reverseOrder())
                        .thenComparing(PontoAtencao::detectadoEm, Comparator.reverseOrder())).toList();
    }

    public PontoAtencao detalhe(long clienteId, long id, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        List<Historico> h = jdbc.query("""
                SELECT h.de, h.para, h.comentario, u.nome, h.em FROM ponto_atencao_historico h LEFT JOIN usuario u ON u.id = h.usuario_id
                WHERE h.ponto_id = ? ORDER BY h.em, h.id""", (rs, i) -> new Historico(rs.getString(1), rs.getString(2), rs.getString(3),
                Objects.requireNonNullElse(rs.getString(4), "sistema"), rs.getTimestamp(5).toLocalDateTime()), id);
        return jdbc.query("""
                SELECT id, regra, versao_regra, severidade, categoria, titulo, descricao, fundamento, recomendacao, valor, evidencias::text,
                       estado, detectado_em, confirmado_em FROM ponto_atencao WHERE id = ? AND cliente_id = ?""", (rs, i) -> ponto(rs, h), id, clienteId)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Ponto de atenção " + id + " não encontrado."));
    }

    public PontoAtencao mudarEstado(long clienteId, long id, String estado, String comentario, UsuarioAutenticado ua) {
        PontoAtencao p = detalhe(clienteId, id, ua);
        if (!List.of("NOVO", "EM_ANALISE", "RESOLVIDO", "DESCARTADO").contains(estado)) {
            throw new BusinessRuleException("Estado inválido: " + estado + ".");
        }
        if ("DESCARTADO".equals(estado) && (comentario == null || comentario.isBlank())) {
            throw new BusinessRuleException("Descartar exige a justificativa.");
        }
        transacao.executeWithoutResult(t -> {
            jdbc.update("UPDATE ponto_atencao SET estado = ? WHERE id = ?", estado, id);
            historico(id, p.estado(), estado, comentario == null ? null : corta(comentario, 1000), ua.id(), LocalDateTime.now());
        });
        return detalhe(clienteId, id, ua);
    }

    public List<RegraNaoAvaliada> naoAvaliadas(long clienteId, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        return jdbc.query("SELECT nao_avaliadas::text FROM avaliacao_pontos WHERE cliente_id = ?", (rs, i) -> ler(rs.getString(1),
                new TypeReference<List<RegraNaoAvaliada>>() { }), clienteId).stream().findFirst().orElse(List.of());
    }

    /** Painel do escritório: achados abertos por severidade, importações pendentes ou com erro e as simulações recentes. */
    public Painel painel(UsuarioAutenticado ua) {
        List<ClientePainel> clientes = jdbc.query("""
                SELECT c.id, c.razao_social, c.cnpj,
                  count(p.id) FILTER (WHERE p.severidade = 'CRITICA'), count(p.id) FILTER (WHERE p.severidade = 'ALTA'),
                  count(p.id) FILTER (WHERE p.severidade = 'MEDIA'), count(p.id) FILTER (WHERE p.severidade = 'INFO'),
                  (SELECT count(*) FROM documento_fiscal d WHERE d.cliente_id = c.id AND d.status IN ('PENDENTE', 'PROCESSANDO', 'AGUARDANDO_CLIENTE', 'AGUARDANDO_MAPEAMENTO')),
                  (SELECT count(*) FROM documento_fiscal d WHERE d.cliente_id = c.id AND d.status IN ('ERRO', 'LAYOUT_NAO_RECONHECIDO')),
                  (SELECT a.avaliado_em FROM avaliacao_pontos a WHERE a.cliente_id = c.id)
                FROM cliente c LEFT JOIN ponto_atencao p ON p.cliente_id = c.id AND p.estado IN ('NOVO', 'EM_ANALISE')
                WHERE c.escritorio_id = ? GROUP BY c.id, c.razao_social, c.cnpj ORDER BY 4 DESC, 5 DESC, c.razao_social""",
                (rs, i) -> new ClientePainel(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getInt(6),
                        rs.getInt(7), rs.getInt(8), rs.getInt(9), rs.getTimestamp(10) == null ? null : rs.getTimestamp(10).toLocalDateTime()),
                ua.escritorioId());
        List<SimulacaoRecente> simulacoes = jdbc.query("""
                SELECT s.id, c.id, c.razao_social, s.data_criacao, s.recomendacao, s.custo_recomendado FROM simulacao_parcelamento s
                JOIN cliente c ON c.id = s.cliente_id WHERE c.escritorio_id = ? ORDER BY s.data_criacao DESC, s.id DESC LIMIT 10""",
                (rs, i) -> new SimulacaoRecente(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getTimestamp(4).toLocalDateTime(),
                        rs.getString(5), rs.getBigDecimal(6)), ua.escritorioId());
        return new Painel(clientes, simulacoes);
    }

    // ------------------------------------------------------------------------------------------------ parâmetros

    public String versaoRegras() {
        return arquivo.versao();
    }

    Definicao definicao(String codigo, long escritorioId) {
        DefinicaoArquivo d = arquivo.regras().stream().filter(x -> x.codigo().equals(codigo)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Regra " + codigo + " sem definição em pontos/regras.json"));
        Map<String, Object> parametros = new LinkedHashMap<>(d.parametros());
        Severidade sev = d.severidade();
        for (Map<String, Object> o : jdbc.queryForList("SELECT parametros::text AS p, severidade FROM parametro_ponto_atencao WHERE escritorio_id = ? AND regra = ?",
                escritorioId, codigo)) {
            parametros.putAll(ler((String) o.get("p"), new TypeReference<Map<String, Object>>() { }));
            if (o.get("severidade") != null) sev = Severidade.valueOf((String) o.get("severidade"));
        }
        return new Definicao(codigo, d.codigo() + "@" + arquivo.versao(), d.titulo(), d.categoria(), sev, d.fundamento(), d.recomendacao(),
                parametros, d.conferido());
    }

    public List<ConfiguracaoRegra> configuracoes(UsuarioAutenticado ua) {
        Map<String, Map<String, Object>> esc = new HashMap<>();
        jdbc.queryForList("""
                SELECT p.regra, p.parametros::text AS parametros, p.severidade, u.nome, p.atualizado_em FROM parametro_ponto_atencao p
                JOIN usuario u ON u.id = p.atualizado_por WHERE p.escritorio_id = ?""", ua.escritorioId()).forEach(m -> esc.put((String) m.get("regra"), m));
        return arquivo.regras().stream().map(d -> {
            Definicao atual = definicao(d.codigo(), ua.escritorioId());
            Map<String, Object> m = esc.get(d.codigo());
            return new ConfiguracaoRegra(d.codigo(), d.titulo(), d.categoria(), atual.versao(), d.severidade(), d.parametros(), atual.severidade(),
                    atual.parametros(), d.fundamento(), d.recomendacao(), d.conferido(), m == null ? null : (String) m.get("nome"),
                    m == null ? null : ((Timestamp) m.get("atualizado_em")).toLocalDateTime());
        }).toList();
    }

    /** Só os parâmetros que a regra já tem (sem chave nova) e do mesmo tipo do padrão. */
    public ConfiguracaoRegra alterar(String codigo, AlteracaoRegra alteracao, UsuarioAutenticado ua) {
        DefinicaoArquivo d = arquivo.regras().stream().filter(x -> x.codigo().equals(codigo)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Regra " + codigo + " não encontrada."));
        Map<String, Object> p = alteracao.parametros() == null ? Map.of() : alteracao.parametros();
        for (Map.Entry<String, Object> e : p.entrySet()) {
            Object padrao = d.parametros().get(e.getKey());
            if (padrao == null) throw new BusinessRuleException("Parâmetro desconhecido em " + codigo + ": " + e.getKey() + ".");
            boolean numero = padrao instanceof Number;
            if (numero != (e.getValue() instanceof Number) || (numero && ((Number) e.getValue()).doubleValue() < 0)) {
                throw new BusinessRuleException("Valor inválido para " + e.getKey() + " em " + codigo + ".");
            }
        }
        jdbc.update("""
                INSERT INTO parametro_ponto_atencao (escritorio_id, regra, parametros, severidade, atualizado_em, atualizado_por) VALUES (?, ?, ?::jsonb, ?, ?, ?)
                ON CONFLICT (escritorio_id, regra) DO UPDATE SET parametros = EXCLUDED.parametros, severidade = EXCLUDED.severidade,
                    atualizado_em = EXCLUDED.atualizado_em, atualizado_por = EXCLUDED.atualizado_por""",
                ua.escritorioId(), codigo, escrever(p), nome(alteracao.severidade()), Timestamp.valueOf(LocalDateTime.now()), ua.id());
        return configuracoes(ua).stream().filter(c -> c.codigo().equals(codigo)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------------------------------------ apoio

    private PontoAtencao ponto(java.sql.ResultSet rs, List<Historico> h) throws java.sql.SQLException {
        return new PontoAtencao(rs.getLong(1), rs.getString(2), rs.getString(3), Severidade.valueOf(rs.getString(4)), Categoria.valueOf(rs.getString(5)),
                rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getBigDecimal(10),
                ler(rs.getString(11), new TypeReference<List<Evidencia>>() { }), rs.getString(12), rs.getTimestamp(13).toLocalDateTime(),
                rs.getTimestamp(14).toLocalDateTime(), h);
    }

    private static String nome(Enum<?> e) {
        return e == null ? null : e.name();
    }

    private static String corta(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private String escrever(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T ler(String texto, TypeReference<T> tipo) {
        try {
            return json.readValue(texto, tipo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
