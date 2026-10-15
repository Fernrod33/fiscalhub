package com.fiscalhub.backend.importacao;

import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Escriturações importadas (ECD, balancete em planilha, ECF, EFD-Contribuições, EFD ICMS/IPI): uma por documento, com
 * cliente, período, versão do leiaute e situação. Tudo o que um arquivo gera fica pendurado nela (ON DELETE CASCADE):
 * reprocessar apaga e grava de novo; a retificação marca a anterior como SUBSTITUIDA, sem apagar o histórico.
 */
@Repository
public class Escrituracoes {

    /** cnpj: do estabelecimento (a EFD ICMS/IPI é por estabelecimento; nas demais, o da empresa). */
    public record Nova(long documentoId, long clienteId, String cnpj, String origem, LocalDate inicio, LocalDate fim,
                       String versaoLeiaute, String planoReferencial, String finalidade) {
    }

    private record Vigente(long id, long documentoId, String finalidade) {
    }

    private final JdbcTemplate jdbc;

    public Escrituracoes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** O que um processamento anterior do documento gerou (cascata para tudo o que pende da escrituração). */
    public void apagarDoDocumento(long documentoId) {
        jdbc.update("DELETE FROM escrituracao_contabil WHERE documento_id = ?", documentoId);
    }

    public long inserir(Nova e) {
        return jdbc.queryForObject("""
                INSERT INTO escrituracao_contabil (documento_id, cliente_id, cnpj, origem, data_inicio, data_fim,
                                                   versao_leiaute, plano_referencial, finalidade, situacao)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'VIGENTE') RETURNING id""", Long.class,
                e.documentoId(), e.clienteId(), e.cnpj(), e.origem(), Date.valueOf(e.inicio()), Date.valueOf(e.fim()),
                e.versaoLeiaute(), e.planoReferencial(), e.finalidade());
    }

    /** ECD leiaute 7: o plano referencial só aparece no I051, depois da escrituração criada. */
    public void atualizarPlanoReferencial(long escrituracaoId, String planoReferencial) {
        jdbc.update("UPDATE escrituracao_contabil SET plano_referencial = ? WHERE id = ?", planoReferencial, escrituracaoId);
    }

    /**
     * Retificação: a escrituração nova do mesmo cliente, estabelecimento (CNPJ), origem e período passa a valer e a
     * anterior fica SUBSTITUIDA (histórico preservado). Uma original importada depois de uma substituta já nasce substituída.
     */
    public void retificar(ContextoProcessamento contexto, long escrituracaoId, String finalidade, String nome, String registro,
                          Long linha) {
        List<Vigente> anteriores = jdbc.query("""
                        SELECT a.id, a.documento_id, a.finalidade FROM escrituracao_contabil a
                        JOIN escrituracao_contabil e ON e.id = ?
                        WHERE a.cliente_id = e.cliente_id AND a.origem = e.origem AND a.data_inicio = e.data_inicio
                          AND a.data_fim = e.data_fim AND a.cnpj IS NOT DISTINCT FROM e.cnpj AND a.situacao = 'VIGENTE'
                          AND a.id <> e.id""",
                (rs, i) -> new Vigente(rs.getLong(1), rs.getLong(2), rs.getString(3)), escrituracaoId);
        for (Vigente anterior : anteriores) {
            if ("ORIGINAL".equals(finalidade) && "SUBSTITUTA".equals(anterior.finalidade())) {
                marcarSituacao(escrituracaoId, "SUBSTITUIDA");
                contexto.registrar(Severidade.ALERTA, registro, linha, "ORIGINAL_JA_SUBSTITUIDA", "Já existe uma " + nome
                        + " substituta para o período (documento " + anterior.documentoId() + "). Esta fica como substituída.");
                return;
            }
            marcarSituacao(anterior.id(), "SUBSTITUIDA");
            contexto.registrar("SUBSTITUTA".equals(finalidade) ? Severidade.INFO : Severidade.ALERTA, registro, linha,
                    "RETIFICACAO", "Esta " + nome + " substitui a do documento " + anterior.documentoId()
                            + " para o mesmo período, que fica como substituída.");
        }
    }

    private void marcarSituacao(long escrituracaoId, String situacao) {
        jdbc.update("UPDATE escrituracao_contabil SET situacao = ? WHERE id = ?", situacao, escrituracaoId);
    }
}
