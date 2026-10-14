package com.fiscalhub.backend.contabil;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gravação dos dados contábeis lidos dos arquivos, em lote (JDBC batch: com chave IDENTITY o Hibernate não agrupa
 * inserts, e um ECD grande tem centenas de milhares de saldos). Valores na convenção única: devedor +, credor −.
 */
@Repository
public class RepositorioContabil {

    static final int TAMANHO_LOTE = 1000;

    public record NovaEscrituracao(long documentoId, long clienteId, String origem, LocalDate inicio, LocalDate fim,
                                   String versaoLeiaute, String planoReferencial, String finalidade) {
    }

    public record Conta(String codigo, String codigoSuperior, Integer nivel, String natureza, boolean sintetica,
                        String nome, String codigoReferencial, String registro, long linha) {
    }

    public record Saldo(long contaId, String origem, String centroCusto, LocalDate inicio, LocalDate fim,
                        BigDecimal saldoInicial, BigDecimal debitos, BigDecimal creditos, BigDecimal saldoFinal,
                        String registro, long linha) {
    }

    public record LinhaDemonstracao(String codigo, String descricao, Integer nivel, String codigoSuperior, int ordem,
                                    boolean totalizadora, String grupo, BigDecimal valorInicial, BigDecimal valorFinal,
                                    String registro, Long linha) {
    }

    /** Escrituração vigente do mesmo cliente e período (para a retificação). */
    public record Vigente(long id, long documentoId, String finalidade) {
    }

    private final JdbcTemplate jdbc;

    public RepositorioContabil(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** O que um processamento anterior do documento gerou (cascata para contas, saldos e demonstrações). */
    public void apagarDoDocumento(long documentoId) {
        jdbc.update("DELETE FROM escrituracao_contabil WHERE documento_id = ?", documentoId);
    }

    public long inserirEscrituracao(NovaEscrituracao e) {
        return jdbc.queryForObject("""
                INSERT INTO escrituracao_contabil (documento_id, cliente_id, origem, data_inicio, data_fim, versao_leiaute,
                                                   plano_referencial, finalidade, situacao)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'VIGENTE') RETURNING id""", Long.class,
                e.documentoId(), e.clienteId(), e.origem(), Date.valueOf(e.inicio()), Date.valueOf(e.fim()),
                e.versaoLeiaute(), e.planoReferencial(), e.finalidade());
    }

    /** Insere o plano de contas e devolve código -> id. */
    public Map<String, Long> inserirContas(long escrituracaoId, List<Conta> contas) {
        jdbc.batchUpdate("""
                INSERT INTO conta_contabil (escrituracao_id, codigo, codigo_superior, nivel, natureza, sintetica, nome,
                                            codigo_referencial, registro, linha)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", contas, TAMANHO_LOTE, (ps, c) -> {
            ps.setLong(1, escrituracaoId);
            ps.setString(2, c.codigo());
            ps.setString(3, c.codigoSuperior());
            ps.setObject(4, c.nivel());
            ps.setString(5, c.natureza());
            ps.setBoolean(6, c.sintetica());
            ps.setString(7, c.nome());
            ps.setString(8, c.codigoReferencial());
            ps.setString(9, c.registro());
            ps.setLong(10, c.linha());
        });
        Map<String, Long> ids = new HashMap<>();
        jdbc.query("SELECT codigo, id FROM conta_contabil WHERE escrituracao_id = ?",
                rs -> { ids.put(rs.getString(1), rs.getLong(2)); }, escrituracaoId);
        return ids;
    }

    /** Saldos acumulados em memória só até completar um lote; feche (try-with-resources) para gravar o resto. */
    public LoteSaldos lote(long escrituracaoId) {
        return new LoteSaldos(escrituracaoId);
    }

    public final class LoteSaldos implements AutoCloseable {
        private final long escrituracaoId;
        private final List<Saldo> pendentes = new ArrayList<>(TAMANHO_LOTE);

        private LoteSaldos(long escrituracaoId) {
            this.escrituracaoId = escrituracaoId;
        }

        public void adicionar(Saldo s) {
            pendentes.add(s);
            if (pendentes.size() == TAMANHO_LOTE) {
                gravar();
            }
        }

        private void gravar() {
            jdbc.batchUpdate("""
                    INSERT INTO saldo_conta (escrituracao_id, conta_id, origem, centro_custo, data_inicio, data_fim,
                                             saldo_inicial, debitos, creditos, saldo_final, registro, linha)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", pendentes, TAMANHO_LOTE, (ps, s) -> {
                ps.setLong(1, escrituracaoId);
                ps.setLong(2, s.contaId());
                ps.setString(3, s.origem());
                ps.setString(4, s.centroCusto());
                ps.setObject(5, s.inicio() == null ? null : Date.valueOf(s.inicio()));
                ps.setDate(6, Date.valueOf(s.fim()));
                ps.setBigDecimal(7, s.saldoInicial());
                ps.setBigDecimal(8, s.debitos());
                ps.setBigDecimal(9, s.creditos());
                ps.setBigDecimal(10, s.saldoFinal());
                ps.setString(11, s.registro());
                ps.setLong(12, s.linha());
            });
            pendentes.clear();
        }

        @Override
        public void close() {
            if (!pendentes.isEmpty()) {
                gravar();
            }
        }
    }

    public long inserirDemonstracao(long escrituracaoId, String tipo, String origem, LocalDate inicio, LocalDate fim,
                                    String tabelaReferencial) {
        return jdbc.queryForObject("""
                INSERT INTO demonstracao (escrituracao_id, tipo, origem, data_inicio, data_fim, exercicio, tabela_referencial)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, escrituracaoId, tipo, origem,
                inicio == null ? null : Date.valueOf(inicio), Date.valueOf(fim), fim.getYear(), tabelaReferencial);
    }

    /** Insere as linhas e devolve código -> id (códigos são únicos dentro de uma demonstração). */
    public Map<String, Long> inserirLinhas(long demonstracaoId, List<LinhaDemonstracao> linhas) {
        jdbc.batchUpdate("""
                INSERT INTO linha_demonstracao (demonstracao_id, codigo, descricao, nivel, codigo_superior, ordem,
                                                totalizadora, grupo, valor_inicial, valor_final, registro, linha)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", linhas, TAMANHO_LOTE, (ps, l) -> {
            ps.setLong(1, demonstracaoId);
            ps.setString(2, l.codigo());
            ps.setString(3, l.descricao());
            ps.setObject(4, l.nivel());
            ps.setString(5, l.codigoSuperior());
            ps.setInt(6, l.ordem());
            ps.setBoolean(7, l.totalizadora());
            ps.setString(8, l.grupo());
            ps.setBigDecimal(9, l.valorInicial());
            ps.setBigDecimal(10, l.valorFinal());
            ps.setString(11, l.registro());
            ps.setObject(12, l.linha());
        });
        Map<String, Long> ids = new HashMap<>();
        jdbc.query("SELECT codigo, id FROM linha_demonstracao WHERE demonstracao_id = ?",
                rs -> { ids.put(rs.getString(1), rs.getLong(2)); }, demonstracaoId);
        return ids;
    }

    /** Contas da empresa que compõem cada linha (id da linha -> ids das contas). */
    public void inserirComposicao(Map<Long, List<Long>> contasPorLinha) {
        List<long[]> pares = new ArrayList<>();
        contasPorLinha.forEach((linha, contas) -> contas.forEach(c -> pares.add(new long[]{linha, c})));
        jdbc.batchUpdate("INSERT INTO linha_demonstracao_conta (linha_id, conta_id) VALUES (?, ?)", pares, TAMANHO_LOTE,
                (ps, p) -> {
                    ps.setLong(1, p[0]);
                    ps.setLong(2, p[1]);
                });
    }

    public List<Vigente> vigentesDoPeriodo(long clienteId, LocalDate inicio, LocalDate fim, long excetoEscrituracao) {
        return jdbc.query("""
                        SELECT id, documento_id, finalidade FROM escrituracao_contabil
                        WHERE cliente_id = ? AND data_inicio = ? AND data_fim = ? AND situacao = 'VIGENTE' AND id <> ?""",
                (rs, i) -> new Vigente(rs.getLong(1), rs.getLong(2), rs.getString(3)),
                clienteId, Date.valueOf(inicio), Date.valueOf(fim), excetoEscrituracao);
    }

    /** Leiaute 7: o plano referencial só aparece no I051, depois da escrituração criada. */
    public void atualizarPlanoReferencial(long escrituracaoId, String planoReferencial) {
        jdbc.update("UPDATE escrituracao_contabil SET plano_referencial = ? WHERE id = ?", planoReferencial, escrituracaoId);
    }

    public void marcarSituacao(long escrituracaoId, String situacao) {
        jdbc.update("UPDATE escrituracao_contabil SET situacao = ? WHERE id = ?", situacao, escrituracaoId);
    }
}
