package com.fiscalhub.backend.fiscal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.sped.RegistroSped;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gravação do modelo único de apuração (qualquer tributo, qualquer escrituração fiscal): apuração por período, linhas
 * que compõem o cálculo, obrigações a recolher e saldos que passam de um período para outro. Todo valor leva o registro
 * e a linha do arquivo.
 */
@Repository
public class RepositorioApuracao {

    /** Registro e linha do arquivo de onde veio um valor. */
    public record Origem(String registro, long linha) {
        public static Origem de(RegistroSped r) {
            return new Origem(r.registro(), r.linha());
        }
    }

    public record Detalhe(String grupo, String codigo, String descricao, BigDecimal valor, String registro, long linha) {
    }

    public record Obrigacao(String codigoObrigacao, String codigoReceita, LocalDate vencimento, BigDecimal valor,
                            String registro, long linha) {
    }

    public record SaldoFiscal(String tipo, String uf, String codigo, String descricao, LocalDate inicio, LocalDate fim,
                              BigDecimal saldoInicial, BigDecimal saldoFinal, String registro, long linha) {
    }

    /**
     * Uma apuração lida. Os valores são montados pelo leitor do arquivo; {@link #origem} diz de onde veio cada um
     * (campo -> registro e linha). Deduções e créditos utilizados ficam juntos em {@code deducoes}.
     */
    public static final class Apuracao {
        public final String tributo;
        public final String esfera;
        public final String uf;
        public final LocalDate inicio;
        public final LocalDate fim;
        public final String periodicidade;
        public final String regime;
        public final String descricao;
        public BigDecimal base;
        public BigDecimal apurado;
        public BigDecimal deducoes;
        public BigDecimal aRecolher = BigDecimal.ZERO;
        public BigDecimal saldoCredor;
        public final Map<String, Origem> origem = new LinkedHashMap<>();
        public final List<Detalhe> detalhes = new ArrayList<>();
        public final List<Obrigacao> obrigacoes = new ArrayList<>();

        public Apuracao(String tributo, String esfera, String uf, LocalDate inicio, LocalDate fim, String periodicidade,
                        String regime, String descricao) {
            this.tributo = tributo;
            this.esfera = esfera;
            this.uf = uf;
            this.inicio = inicio;
            this.fim = fim;
            this.periodicidade = periodicidade;
            this.regime = regime;
            this.descricao = descricao;
        }

        public void detalhe(String grupo, String codigo, String descricao, BigDecimal valor, RegistroSped r) {
            detalhes.add(new Detalhe(grupo, codigo, descricao == null ? "(sem descrição)" : truncar(descricao),
                    valor == null ? BigDecimal.ZERO : valor, r.registro(), r.linha()));
        }
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public RepositorioApuracao(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void gravar(long escrituracaoId, long clienteId, long documentoId, List<Apuracao> apuracoes) {
        for (Apuracao a : apuracoes) {
            long id = jdbc.queryForObject("""
                    INSERT INTO apuracao_tributo (escrituracao_id, cliente_id, documento_id, tributo, esfera, uf, data_inicio,
                        data_fim, periodicidade, regime, descricao, base_calculo, valor_apurado, deducoes, valor_a_recolher,
                        saldo_credor, origem)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) RETURNING id""", Long.class,
                    escrituracaoId, clienteId, documentoId, a.tributo, a.esfera, a.uf, Date.valueOf(a.inicio),
                    Date.valueOf(a.fim), a.periodicidade, a.regime, a.descricao, a.base, a.apurado, a.deducoes, a.aRecolher,
                    a.saldoCredor, paraJson(a.origem));
            List<Object[]> detalhes = new ArrayList<>();
            for (int i = 0; i < a.detalhes.size(); i++) {
                Detalhe d = a.detalhes.get(i);
                detalhes.add(new Object[]{id, i + 1, d.grupo(), d.codigo(), d.descricao(), d.valor(), d.registro(), d.linha()});
            }
            jdbc.batchUpdate("""
                    INSERT INTO detalhe_apuracao (apuracao_id, ordem, grupo, codigo, descricao, valor, registro, linha)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", detalhes);
            jdbc.batchUpdate("""
                    INSERT INTO obrigacao_recolher (apuracao_id, codigo_obrigacao, codigo_receita, vencimento, valor, registro, linha)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""", a.obrigacoes.stream().map(o -> new Object[]{id, o.codigoObrigacao(),
                    o.codigoReceita(), o.vencimento() == null ? null : Date.valueOf(o.vencimento()), o.valor(), o.registro(),
                    o.linha()}).toList());
        }
    }

    public void gravarSaldos(long escrituracaoId, long clienteId, List<SaldoFiscal> saldos) {
        jdbc.batchUpdate("""
                INSERT INTO saldo_fiscal (escrituracao_id, cliente_id, tipo, uf, codigo, descricao, data_inicio, data_fim,
                                          saldo_inicial, saldo_final, registro, linha)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", saldos.stream().map(s -> new Object[]{escrituracaoId, clienteId,
                s.tipo(), s.uf(), s.codigo(), truncar(s.descricao()), Date.valueOf(s.inicio()), Date.valueOf(s.fim()),
                s.saldoInicial(), s.saldoFinal(), s.registro(), s.linha()}).toList());
    }

    private String paraJson(Map<String, Origem> origem) {
        try {
            return json.writeValueAsString(origem);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncar(String texto) {
        return texto.length() > 500 ? texto.substring(0, 500) : texto;
    }
}
