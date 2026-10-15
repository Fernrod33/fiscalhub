package com.fiscalhub.backend.fiscal;

import com.fiscalhub.backend.importacao.ContextoProcessamento;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Conferência ECD × ECF do mesmo cliente e exercício (escriturações vigentes): receita bruta e resultado antes do IRPJ e
 * da CSLL pelo mesmo código referencial (L300A/P150A: 3.01.01.01.01 e 3.01), entre a DRE referencial montada da ECD e a
 * DRE declarada na ECF. Sem DRE referencial na ECD (exercício sem tabela oficial carregada), confere o resultado líquido
 * (soma do I355 da ECD × linha 3 da ECF). Diferença vira alerta com os dois valores e as duas origens, no documento que
 * está sendo processado (quem chega por último). Insumo dos pontos de atenção da Fase 6.
 */
@Component
public class ConferenciaEcdEcf {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");
    private static final Map<String, String> ITENS = Map.of("3.01.01.01.01", "Receita bruta",
            "3.01", "Resultado antes do IRPJ e da CSLL");

    private record Valor(BigDecimal valor, String arquivo, String origem) {
    }

    private final JdbcTemplate jdbc;

    public ConferenciaEcdEcf(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void conferir(long clienteId, int exercicio, ContextoProcessamento contexto) {
        Long ecd = vigente(clienteId, "ECD", exercicio);
        Long ecf = vigente(clienteId, "ECF", exercicio);
        if (ecd == null || ecf == null) {
            return;
        }
        boolean referencial = false;
        for (Map.Entry<String, String> item : ITENS.entrySet()) {
            Valor daEcd = linhaEcd(ecd, item.getKey());
            Valor daEcf = linhaEcf(ecf, item.getKey());
            referencial |= daEcd != null;
            comparar(item.getValue(), exercicio, daEcd, daEcf, contexto);
        }
        if (!referencial) {
            comparar("Resultado líquido do período", exercicio, resultadoI355(ecd), linhaEcf(ecf, "3"), contexto);
        }
    }

    private Long vigente(long clienteId, String origem, int exercicio) {
        return jdbc.queryForList("""
                SELECT id FROM escrituracao_contabil WHERE cliente_id = ? AND origem = ? AND situacao = 'VIGENTE'
                  AND extract(YEAR FROM data_fim) = ? ORDER BY data_fim DESC, id DESC LIMIT 1""", Long.class,
                clienteId, origem, exercicio).stream().findFirst().orElse(null);
    }

    private Valor linhaEcd(long escrituracaoId, String codigo) {
        return jdbc.query("""
                SELECT l.valor_final, doc.nome_original_arquivo, l.id FROM linha_demonstracao l
                JOIN demonstracao d ON d.id = l.demonstracao_id
                JOIN escrituracao_contabil e ON e.id = d.escrituracao_id
                JOIN documento_fiscal doc ON doc.id = e.documento_id
                WHERE d.escrituracao_id = ? AND d.tipo = 'DRE' AND d.origem = 'REFERENCIAL' AND l.codigo = ?""",
                (rs, i) -> new Valor(rs.getBigDecimal(1), rs.getString(2),
                        "DRE referencial montada dos saldos I355, conta " + codigo), escrituracaoId, codigo)
                .stream().findFirst().orElse(null);
    }

    /** Soma dos períodos de apuração da ECF (trimestres ou o anual), com as linhas do L300/P150. */
    private Valor linhaEcf(long escrituracaoId, String codigo) {
        List<Map<String, Object>> linhas = jdbc.queryForList("""
                SELECT l.valor_final, l.registro, l.linha, doc.nome_original_arquivo FROM linha_demonstracao l
                JOIN demonstracao d ON d.id = l.demonstracao_id
                JOIN escrituracao_contabil e ON e.id = d.escrituracao_id
                JOIN documento_fiscal doc ON doc.id = e.documento_id
                WHERE d.escrituracao_id = ? AND d.origem = 'ECF' AND l.codigo = ? ORDER BY l.linha""", escrituracaoId, codigo);
        if (linhas.isEmpty()) {
            return null;
        }
        BigDecimal soma = linhas.stream().map(l -> (BigDecimal) l.get("valor_final")).reduce(BigDecimal.ZERO, BigDecimal::add);
        String origem = linhas.get(0).get("registro") + " linha(s) " + String.join(", ",
                linhas.stream().map(l -> String.valueOf(l.get("linha"))).toList());
        return new Valor(soma, (String) linhas.get(0).get("nome_original_arquivo"), origem);
    }

    private Valor resultadoI355(long escrituracaoId) {
        return jdbc.query("""
                SELECT sum(s.saldo_final), doc.nome_original_arquivo, min(s.linha), max(s.linha) FROM saldo_conta s
                JOIN escrituracao_contabil e ON e.id = s.escrituracao_id
                JOIN documento_fiscal doc ON doc.id = e.documento_id
                WHERE s.escrituracao_id = ? AND s.origem = 'I355' GROUP BY doc.nome_original_arquivo""",
                (rs, i) -> new Valor(rs.getBigDecimal(1), rs.getString(2), "soma do I355, linhas " + rs.getLong(3) + " a "
                        + rs.getLong(4)), escrituracaoId).stream().findFirst().orElse(null);
    }

    private static void comparar(String item, int exercicio, Valor ecd, Valor ecf, ContextoProcessamento contexto) {
        if (ecd == null || ecf == null) {
            return;
        }
        BigDecimal diferenca = ecd.valor().subtract(ecf.valor());
        if (diferenca.abs().compareTo(TOLERANCIA) > 0) {
            contexto.registrar(Severidade.ALERTA, null, null, "CONFERENCIA_ECD_ECF", item + " de " + exercicio + ": ECD "
                    + ecd.valor().toPlainString() + " (" + ecd.arquivo() + ", " + ecd.origem() + ") × ECF "
                    + ecf.valor().toPlainString() + " (" + ecf.arquivo() + ", " + ecf.origem() + "); diferença "
                    + diferenca.toPlainString() + " (devedor +, credor −).");
        }
    }
}
