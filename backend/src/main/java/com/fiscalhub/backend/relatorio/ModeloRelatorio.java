package com.fiscalhub.backend.relatorio;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Modelo de um relatório, montado pelo backend a partir dos serviços (nada recalculado aqui) e gravado como snapshot
 * JSON na emissão. O mesmo modelo gera a prévia (HTML), o PDF e o Excel: o template e a planilha só formatam.
 * Valores das tabelas em texto cru (BigDecimal.toPlainString, datas ISO), formatados pelo Formato da coluna; um texto
 * que não é número numa coluna numérica (ex.: "não disponível") sai como texto.
 */
public record ModeloRelatorio(TipoRelatorio tipo, String titulo, Cabecalho cabecalho, List<Indicador> sumario,
                              List<Secao> secoes, List<String> notas, List<Fonte> fontes, Versoes versoes, List<String> assinaturas) {

    public enum TipoRelatorio {
        TRIBUTOS("Relatório de tributos apurados, declarados e pagos"),
        CARGA_TRIBUTARIA("Carga tributária efetiva"),
        SITUACAO_FISCAL("Situação fiscal resumida"),
        CAPACIDADE("Capacidade de pagamento"),
        DOSSIE_CAPAG("Dossiê de apoio à revisão da CAPAG");

        public final String titulo;

        TipoRelatorio(String titulo) {
            this.titulo = titulo;
        }
    }

    public enum Formato { TEXTO, MOEDA, PERCENTUAL, INTEIRO, COMPETENCIA, DATA, CNPJ }

    /** numero null na prévia; logoChave: o logotipo no storage na hora da emissão (as chaves não são reaproveitadas). */
    public record Cabecalho(String escritorio, String logoChave, String cor, String rodape, String responsavel,
                            String clienteRazaoSocial, String clienteCnpj, String periodo, LocalDateTime emissao, Long numero) {
    }

    public record Indicador(String rotulo, String valor, Formato formato, String nota) {
    }

    /** destaque: seção em evidência (ex.: itens que impedem a certidão). tabela e grafico opcionais. */
    public record Secao(String titulo, String texto, boolean destaque, Tabela tabela, Grafico grafico) {
    }

    /** totais: linha de totais (opcional), com a mesma quantidade de colunas. */
    public record Tabela(List<Coluna> colunas, List<List<String>> linhas, List<String> totais) {
    }

    public record Coluna(String nome, Formato formato) {
    }

    public enum TipoGrafico { BARRAS, EMPILHADAS, LINHA }

    public record Grafico(TipoGrafico tipo, String titulo, Formato formato, List<String> categorias, List<Serie> series) {
    }

    /** valores na ordem das categorias; null = sem dado (a barra ou o ponto não aparece). */
    public record Serie(String nome, List<BigDecimal> valores) {
    }

    public record Fonte(long documentoId, String tipo, String arquivo, String periodo, LocalDateTime importadoEm, String sha256) {
    }

    /** template: versão do layout; normas: tabela de códigos de receita (SHA-256) e base legal dos acréscimos. */
    public record Versoes(String template, String normas) {
    }
}
