package com.fiscalhub.backend.contabil.planilha;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Mapeamentos de colunas (por cliente e formato) e de-para referencial do cliente. */
@Repository
public class RepositorioPlanilha {

    /** Colunas a partir de 0. Opcionais: null. */
    public record Colunas(int codigo, int descricao, Integer saldoInicial, Integer debitos, Integer creditos, int saldoFinal,
                          Integer dcInicial, Integer dcFinal) {
    }

    public record Mapeamento(long id, String assinatura, String aba, int linhaCabecalho, Colunas colunas, String planoReferencial) {
    }

    private static final RowMapper<Mapeamento> MAPEAMENTO = (rs, i) -> new Mapeamento(rs.getLong("id"), rs.getString("assinatura"),
            rs.getString("aba"), rs.getInt("linha_cabecalho"), new Colunas(rs.getInt("col_codigo"), rs.getInt("col_descricao"),
            (Integer) rs.getObject("col_saldo_inicial"), (Integer) rs.getObject("col_debitos"), (Integer) rs.getObject("col_creditos"),
            rs.getInt("col_saldo_final"), (Integer) rs.getObject("col_dc_inicial"), (Integer) rs.getObject("col_dc_final")),
            rs.getString("plano_referencial"));

    private final JdbcTemplate jdbc;

    public RepositorioPlanilha(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Formato da planilha: o cabeçalho normalizado (sem caixa e espaços nas pontas), em SHA-256. */
    public static String assinatura(List<String> cabecalho) {
        String normalizado = String.join("|", cabecalho.stream().map(c -> c.trim().toLowerCase(Locale.ROOT)).toList());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalizado.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Mapeamento> mapeamentos(long clienteId) {
        return jdbc.query("SELECT * FROM mapeamento_planilha WHERE cliente_id = ? ORDER BY atualizado_em DESC", MAPEAMENTO, clienteId);
    }

    public Optional<Mapeamento> mapeamento(long clienteId, String assinatura) {
        return jdbc.query("SELECT * FROM mapeamento_planilha WHERE cliente_id = ? AND assinatura = ?", MAPEAMENTO, clienteId, assinatura)
                .stream().findFirst();
    }

    public void salvarMapeamento(long clienteId, String assinatura, String aba, int linhaCabecalho, Colunas c, String plano) {
        jdbc.update("""
                INSERT INTO mapeamento_planilha (cliente_id, assinatura, aba, linha_cabecalho, col_codigo, col_descricao,
                    col_saldo_inicial, col_debitos, col_creditos, col_saldo_final, col_dc_inicial, col_dc_final, plano_referencial)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (cliente_id, assinatura) DO UPDATE SET aba = EXCLUDED.aba, linha_cabecalho = EXCLUDED.linha_cabecalho,
                    col_codigo = EXCLUDED.col_codigo, col_descricao = EXCLUDED.col_descricao,
                    col_saldo_inicial = EXCLUDED.col_saldo_inicial, col_debitos = EXCLUDED.col_debitos,
                    col_creditos = EXCLUDED.col_creditos, col_saldo_final = EXCLUDED.col_saldo_final,
                    col_dc_inicial = EXCLUDED.col_dc_inicial, col_dc_final = EXCLUDED.col_dc_final,
                    plano_referencial = EXCLUDED.plano_referencial, atualizado_em = CURRENT_TIMESTAMP""",
                clienteId, assinatura, aba, linhaCabecalho, c.codigo(), c.descricao(), c.saldoInicial(), c.debitos(), c.creditos(),
                c.saldoFinal(), c.dcInicial(), c.dcFinal(), plano);
    }

    /** De-para salvo pelo usuário (código da conta -> código referencial). */
    public Map<String, String> deparaSalvo(long clienteId) {
        Map<String, String> m = new HashMap<>();
        jdbc.query("SELECT codigo_conta, codigo_referencial FROM depara_referencial WHERE cliente_id = ?",
                rs -> { m.put(rs.getString(1), rs.getString(2)); }, clienteId);
        return m;
    }

    /** Mapeamento I051 das ECDs do cliente (a mais recente vence): sugestão para o mesmo código de conta. */
    public Map<String, String> referencialDasEcds(long clienteId) {
        Map<String, String> m = new HashMap<>();
        jdbc.query("""
                SELECT c.codigo, c.codigo_referencial FROM conta_contabil c JOIN escrituracao_contabil e ON e.id = c.escrituracao_id
                WHERE e.cliente_id = ? AND e.origem = 'ECD' AND c.codigo_referencial IS NOT NULL
                ORDER BY e.data_fim DESC, e.id DESC""", rs -> { m.putIfAbsent(rs.getString(1), rs.getString(2)); }, clienteId);
        return m;
    }

    public void salvarDepara(long clienteId, String codigoConta, String codigoReferencial) {
        if (codigoReferencial == null) {
            jdbc.update("DELETE FROM depara_referencial WHERE cliente_id = ? AND codigo_conta = ?", clienteId, codigoConta);
            return;
        }
        jdbc.update("""
                INSERT INTO depara_referencial (cliente_id, codigo_conta, codigo_referencial) VALUES (?, ?, ?)
                ON CONFLICT (cliente_id, codigo_conta) DO UPDATE SET codigo_referencial = EXCLUDED.codigo_referencial,
                    atualizado_em = CURRENT_TIMESTAMP""", clienteId, codigoConta, codigoReferencial);
    }

    /** Plano da ECD mais recente do cliente (regime de tributação): sugestão para o balancete. */
    public Optional<String> planoDaUltimaEcd(long clienteId) {
        return jdbc.queryForList("""
                SELECT plano_referencial FROM escrituracao_contabil WHERE cliente_id = ? AND origem = 'ECD'
                AND plano_referencial IS NOT NULL ORDER BY data_fim DESC LIMIT 1""", String.class, clienteId).stream().findFirst();
    }
}
