package com.fiscalhub.backend.fiscal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.fiscal.RepositorioApuracao.Detalhe;
import com.fiscalhub.backend.fiscal.RepositorioApuracao.Obrigacao;
import com.fiscalhub.backend.fiscal.RepositorioApuracao.Origem;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.AcessoRecursoService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Consulta do que o cliente apurou (escriturações vigentes): apurações por tributo e período, o detalhe do cálculo, os
 * saldos fiscais e os alertas de conferência. Isolamento por escritório pelo AcessoRecursoService; toda consulta filtra
 * pelo cliente.
 */
@Service
@Transactional(readOnly = true)
public class ApuracaoService {

    /** Uma apuração. origem: campo -> registro e linha do arquivo (o documento é documentoId/arquivo). */
    public record Apuracao(long id, String tributo, String esfera, String uf, LocalDate dataInicio, LocalDate dataFim,
                           String periodicidade, String regime, String descricao, BigDecimal baseCalculo,
                           BigDecimal valorApurado, BigDecimal deducoes, BigDecimal valorARecolher, BigDecimal saldoCredor,
                           Map<String, Origem> origem, long documentoId, String arquivo, String escrituracao) {
    }

    public record Detalhamento(Apuracao apuracao, List<Detalhe> linhas, List<Obrigacao> obrigacoes) {
    }

    public record SaldoFiscal(long id, String tipo, String uf, String codigo, String descricao, LocalDate dataInicio,
                              LocalDate dataFim, BigDecimal saldoInicial, BigDecimal saldoFinal, long documentoId,
                              String arquivo, String registro, long linha) {
    }

    public record Alerta(long documentoId, String arquivo, String severidade, String regra, String registro, Long linha,
                         String mensagem) {
    }

    private static final String APURACAO = """
            SELECT a.id, a.tributo, a.esfera, a.uf, a.data_inicio, a.data_fim, a.periodicidade, a.regime, a.descricao,
                   a.base_calculo, a.valor_apurado, a.deducoes, a.valor_a_recolher, a.saldo_credor, a.origem::text,
                   a.documento_id, d.nome_original_arquivo, e.origem
            FROM apuracao_tributo a
            JOIN escrituracao_contabil e ON e.id = a.escrituracao_id
            JOIN documento_fiscal d ON d.id = a.documento_id
            WHERE a.cliente_id = ? AND e.situacao = 'VIGENTE'""";

    private final JdbcTemplate jdbc;
    private final AcessoRecursoService acesso;
    private final ObjectMapper json;

    public ApuracaoService(JdbcTemplate jdbc, AcessoRecursoService acesso, ObjectMapper json) {
        this.jdbc = jdbc;
        this.acesso = acesso;
        this.json = json;
    }

    /** Apurações do cliente; tributo, de e ate opcionais (período que se sobrepõe a [de, ate]). */
    public List<Apuracao> apuracoes(long clienteId, TipoTributo tributo, LocalDate de, LocalDate ate, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        StringBuilder sql = new StringBuilder(APURACAO);
        List<Object> parametros = new ArrayList<>(List.of(clienteId));
        if (tributo != null) {
            sql.append(" AND a.tributo = ?");
            parametros.add(tributo.name());
        }
        if (de != null) {
            sql.append(" AND a.data_fim >= ?");
            parametros.add(Date.valueOf(de));
        }
        if (ate != null) {
            sql.append(" AND a.data_inicio <= ?");
            parametros.add(Date.valueOf(ate));
        }
        sql.append(" ORDER BY a.data_inicio, a.tributo, a.uf NULLS FIRST, a.id");
        return jdbc.query(sql.toString(), apuracao(), parametros.toArray());
    }

    public Detalhamento detalhe(long clienteId, long apuracaoId, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        Apuracao a = jdbc.query(APURACAO + " AND a.id = ?", apuracao(), clienteId, apuracaoId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Apuração não encontrada com a ID: " + apuracaoId));
        List<Detalhe> linhas = jdbc.query("""
                        SELECT grupo, codigo, descricao, valor, registro, linha FROM detalhe_apuracao
                        WHERE apuracao_id = ? ORDER BY ordem""",
                (rs, i) -> new Detalhe(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4),
                        rs.getString(5), rs.getLong(6)), apuracaoId);
        List<Obrigacao> obrigacoes = jdbc.query("""
                        SELECT codigo_obrigacao, codigo_receita, vencimento, valor, registro, linha FROM obrigacao_recolher
                        WHERE apuracao_id = ? ORDER BY linha""",
                (rs, i) -> new Obrigacao(rs.getString(1), rs.getString(2),
                        rs.getDate(3) == null ? null : rs.getDate(3).toLocalDate(), rs.getBigDecimal(4), rs.getString(5),
                        rs.getLong(6)), apuracaoId);
        return new Detalhamento(a, linhas, obrigacoes);
    }

    /** Prejuízo fiscal, base negativa, créditos de PIS/COFINS e saldos credores, por período (evolução). */
    public List<SaldoFiscal> saldos(long clienteId, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        return jdbc.query("""
                SELECT s.id, s.tipo, s.uf, s.codigo, s.descricao, s.data_inicio, s.data_fim, s.saldo_inicial, s.saldo_final,
                       e.documento_id, d.nome_original_arquivo, s.registro, s.linha
                FROM saldo_fiscal s
                JOIN escrituracao_contabil e ON e.id = s.escrituracao_id
                JOIN documento_fiscal d ON d.id = e.documento_id
                WHERE s.cliente_id = ? AND e.situacao = 'VIGENTE'
                ORDER BY s.tipo, s.data_fim, s.uf NULLS FIRST, s.codigo, s.id""",
                (rs, i) -> new SaldoFiscal(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getDate(6).toLocalDate(), rs.getDate(7).toLocalDate(), rs.getBigDecimal(8),
                        rs.getBigDecimal(9), rs.getLong(10), rs.getString(11), rs.getString(12), rs.getLong(13)), clienteId);
    }

    /**
     * Alertas e erros das escriturações fiscais vigentes (fórmulas que não fecham, contagem) e as conferências entre
     * arquivos (ECD × ECF), que também aparecem na gaveta de inconsistências de cada documento.
     */
    public List<Alerta> alertas(long clienteId, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        return jdbc.query("""
                SELECT i.documento_id, d.nome_original_arquivo, i.severidade, i.regra, i.registro, i.linha, i.mensagem
                FROM inconsistencia_importacao i
                JOIN documento_fiscal d ON d.id = i.documento_id
                JOIN escrituracao_contabil e ON e.documento_id = d.id AND e.situacao = 'VIGENTE'
                WHERE e.cliente_id = ? AND i.severidade IN ('ERRO', 'ALERTA')
                  AND (e.origem IN ('ECF', 'EFD_CONTRIBUICOES', 'EFD_ICMS_IPI') OR i.regra LIKE 'CONFERENCIA%')
                ORDER BY e.data_fim DESC, i.documento_id, i.id""",
                (rs, i) -> new Alerta(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        (Long) rs.getObject(6), rs.getString(7)), clienteId);
    }

    private RowMapper<Apuracao> apuracao() {
        return (rs, i) -> new Apuracao(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getDate(5).toLocalDate(), rs.getDate(6).toLocalDate(), rs.getString(7), rs.getString(8), rs.getString(9),
                rs.getBigDecimal(10), rs.getBigDecimal(11), rs.getBigDecimal(12), rs.getBigDecimal(13), rs.getBigDecimal(14),
                origem(rs.getString(15)), rs.getLong(16), rs.getString(17), rs.getString(18));
    }

    private Map<String, Origem> origem(String texto) {
        try {
            return json.readValue(texto, new TypeReference<>() { });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
