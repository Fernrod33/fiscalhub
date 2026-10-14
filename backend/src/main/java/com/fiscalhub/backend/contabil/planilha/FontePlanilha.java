package com.fiscalhub.backend.contabil.planilha;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Planilha lida em stream, linha a linha (.xlsx ou .csv). Células sempre como texto: número no formato bruto (ex.:
 * "1234.56" no .xlsx, "1.234,56 C" no .csv), interpretado depois por {@link NumeroPlanilha}. Fórmulas: só o valor em
 * cache, nunca avaliadas.
 */
public interface FontePlanilha extends Closeable {

    /** Acima disso a planilha não é um balancete (e a leitura para). */
    int LIMITE_LINHAS = 100_000;
    int LIMITE_COLUNAS = 200;

    /** Recebe cada linha (número a partir de 1, como na planilha); devolve false para parar a leitura. */
    interface Leitor {
        boolean linha(long numero, List<String> celulas);
    }

    List<String> abas() throws IOException;

    /** Lê a aba (null = primeira). */
    void ler(String aba, Leitor leitor) throws IOException;

    static FontePlanilha abrir(InputStream in, String extensao) throws IOException {
        return ".xlsx".equals(extensao) ? new PlanilhaXlsx(in) : new PlanilhaCsv(in);
    }

    /** Planilha fora dos limites ou ilegível (vira inconsistência com a linha). */
    class PlanilhaInvalida extends RuntimeException {
        private final long linha;

        public PlanilhaInvalida(long linha, String mensagem) {
            super(mensagem);
            this.linha = linha;
        }

        public long linha() {
            return linha;
        }
    }
}
