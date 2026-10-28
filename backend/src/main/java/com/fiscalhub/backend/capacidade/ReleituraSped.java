package com.fiscalhub.backend.capacidade;

import com.fiscalhub.backend.sped.LeitorSped;
import com.fiscalhub.backend.storage.ArmazenamentoArquivos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Set;

/**
 * Relê registros de um SPED já importado, direto do arquivo guardado no storage (sem reimportar): os lançamentos da
 * ECD (I200/I250) para a DFC direta e os C190 da EFD ICMS/IPI para o faturamento. Em stream, só os registros pedidos.
 */
@Component
public class ReleituraSped {

    /** campos: na numeração do manual (índice 1 = REG); linha: número da linha no arquivo. */
    public interface Leitor {
        void registro(String[] campos, long linha);
    }

    private final JdbcTemplate jdbc;
    private final ArmazenamentoArquivos armazenamento;

    public ReleituraSped(JdbcTemplate jdbc, ArmazenamentoArquivos armazenamento) {
        this.jdbc = jdbc;
        this.armazenamento = armazenamento;
    }

    public void ler(long escrituracaoId, Set<String> registros, Leitor leitor) {
        String chave = jdbc.queryForObject("""
                SELECT d.chave_armazenamento FROM escrituracao_contabil e JOIN documento_fiscal d ON d.id = e.documento_id
                WHERE e.id = ?""", String.class, escrituracaoId);
        try (InputStream in = armazenamento.abrir(chave); LeitorSped sped = new LeitorSped(in, registros)) {
            for (String l = sped.proximaLinha(); l != null; l = sped.proximaLinha()) {
                if (registros.contains(LeitorSped.registro(l))) {
                    leitor.registro(LeitorSped.campos(l), sped.numeroLinha());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
