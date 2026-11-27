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

    private record Valor(BigDecimal valor, long documentoId, String arquivo, String origem) {
    }

    /** Um item comparado (receita bruta, LAIR ou resultado líquido): ECD × ECF do exercício, com a origem de cada lado. */
    public record Comparacao(int exercicio, String item, BigDecimal ecd, long documentoEcd, String arquivoEcd, String origemEcd,
                             BigDecimal ecf, long documentoEcf, String arquivoEcf, String origemEcf, BigDecimal diferenca) {
    }

    private final JdbcTemplate jdbc;

    public ConferenciaEcdEcf(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void conferir(long clienteId, int exercicio, ContextoProcessamento contexto) {
        comparacoes(clienteId, exercicio).stream().filter(c -> c.diferenca().abs().compareTo(TOLERANCIA) > 0).forEach(c ->
                contexto.registrar(Severidade.ALERTA, null, null, "CONFERENCIA_ECD_ECF", c.item() + " de " + exercicio + ": ECD "
                        + c.ecd().toPlainString() + " (" + c.arquivoEcd() + ", " + c.origemEcd() + ") × ECF "
                        + c.ecf().toPlainString() + " (" + c.arquivoEcf() + ", " + c.origemEcf() + "); diferença "
                        + c.diferenca().toPlainString() + " (devedor +, credor −)."));
    }

    /** Todos os exercícios com ECD e ECF vigentes do cliente, comparados (sem tolerância: quem usa aplica o limiar). */
    public List<Comparacao> comparacoes(long clienteId) {
        return jdbc.queryForList("""
                SELECT DISTINCT extract(YEAR FROM data_fim)::int FROM escrituracao_contabil
                WHERE cliente_id = ? AND origem = 'ECF' AND situacao = 'VIGENTE' ORDER BY 1""", Integer.class, clienteId)
                .stream().flatMap(ano -> comparacoes(clienteId, ano).stream()).toList();
    }

    private List<Comparacao> comparacoes(long clienteId, int exercicio) {
        Long ecd = vigente(clienteId, "ECD", exercicio);
        Long ecf = vigente(clienteId, "ECF", exercicio);
        List<Comparacao> out = new java.util.ArrayList<>();
        if (ecd == null || ecf == null) {
            return out;
        }
        boolean referencial = false;
        for (Map.Entry<String, String> item : ITENS.entrySet()) {
            Valor daEcd = linhaEcd(ecd, item.getKey());
            referencial |= daEcd != null;
            comparar(out, item.getValue(), exercicio, daEcd, linhaEcf(ecf, item.getKey()));
        }
        if (!referencial) {
            comparar(out, "Resultado líquido do período", exercicio, resultadoI355(ecd), linhaEcf(ecf, "3"));
        }
        return out;
    }

    private Long vigente(long clienteId, String origem, int exercicio) {
        return jdbc.queryForList("""
                SELECT id FROM escrituracao_contabil WHERE cliente_id = ? AND origem = ? AND situacao = 'VIGENTE'
                  AND extract(YEAR FROM data_fim) = ? ORDER BY data_fim DESC, id DESC LIMIT 1""", Long.class,
                clienteId, origem, exercicio).stream().findFirst().orElse(null);
    }

    private Valor linhaEcd(long escrituracaoId, String codigo) {
        return jdbc.query("""
                SELECT l.valor_final, doc.nome_original_arquivo, l.id, doc.id FROM linha_demonstracao l
                JOIN demonstracao d ON d.id = l.demonstracao_id
                JOIN escrituracao_contabil e ON e.id = d.escrituracao_id
                JOIN documento_fiscal doc ON doc.id = e.documento_id
                WHERE d.escrituracao_id = ? AND d.tipo = 'DRE' AND d.origem = 'REFERENCIAL' AND l.codigo = ?""",
                (rs, i) -> new Valor(rs.getBigDecimal(1), rs.getLong(4), rs.getString(2),
                        "DRE referencial montada dos saldos I355, conta " + codigo), escrituracaoId, codigo)
                .stream().findFirst().orElse(null);
    }

    /** Soma dos períodos de apuração da ECF (trimestres ou o anual), com as linhas do L300/P150. */
    private Valor linhaEcf(long escrituracaoId, String codigo) {
        List<Map<String, Object>> linhas = jdbc.queryForList("""
                SELECT l.valor_final, l.registro, l.linha, doc.nome_original_arquivo, doc.id FROM linha_demonstracao l
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
        return new Valor(soma, ((Number) linhas.get(0).get("id")).longValue(), (String) linhas.get(0).get("nome_original_arquivo"), origem);
    }

    private Valor resultadoI355(long escrituracaoId) {
        return jdbc.query("""
                SELECT sum(s.saldo_final), doc.nome_original_arquivo, min(s.linha), max(s.linha), doc.id FROM saldo_conta s
                JOIN escrituracao_contabil e ON e.id = s.escrituracao_id
                JOIN documento_fiscal doc ON doc.id = e.documento_id
                WHERE s.escrituracao_id = ? AND s.origem = 'I355' GROUP BY doc.nome_original_arquivo, doc.id""",
                (rs, i) -> new Valor(rs.getBigDecimal(1), rs.getLong(5), rs.getString(2), "soma do I355, linhas " + rs.getLong(3) + " a "
                        + rs.getLong(4)), escrituracaoId).stream().findFirst().orElse(null);
    }

    private static void comparar(List<Comparacao> out, String item, int exercicio, Valor ecd, Valor ecf) {
        if (ecd != null && ecf != null) {
            out.add(new Comparacao(exercicio, item, ecd.valor(), ecd.documentoId(), ecd.arquivo(), ecd.origem(), ecf.valor(),
                    ecf.documentoId(), ecf.arquivo(), ecf.origem(), ecd.valor().subtract(ecf.valor())));
        }
    }
}
