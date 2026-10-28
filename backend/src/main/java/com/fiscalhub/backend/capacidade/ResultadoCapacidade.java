package com.fiscalhub.backend.capacidade;

import com.fiscalhub.backend.relatorio.ModeloRelatorio.Formato;
import com.fiscalhub.backend.situacao.SituacaoFiscalService.OrigemValor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Formato único do resultado das metodologias de capacidade (CAPAG oficial e estimada, classificação do PR, análise
 * gerencial): valor, classe, critérios com pontuação, fundamento e origem, parâmetros usados e o rótulo. O frontend e
 * os relatórios só exibem: nenhuma regra é recalculada fora do backend.
 */
public record ResultadoCapacidade(String metodologia, String titulo, Rotulo rotulo, String rotuloTexto, BigDecimal valor,
                                  String classe, List<Criterio> criterios, List<Item> itens, Map<String, Object> parametros,
                                  List<String> normas, List<String> notas, List<String> alertas) {

    /** OFICIAL: do documento do órgão; ESTIMATIVA: calculada pelo FiscalHub; GERENCIAL: análise não normativa. */
    public enum Rotulo {
        OFICIAL("oficial (documento do órgão)"),
        ESTIMATIVA("estimativa do consultor"),
        GERENCIAL("análise do consultor — não é critério oficial");

        public final String texto;

        Rotulo(String texto) {
            this.texto = texto;
        }
    }

    /**
     * Um critério: valor apurado (texto cru, no formato), pontos ou índice, faixa em que caiu, fundamento (norma e artigo)
     * e as origens do insumo. naoDisponivel: o que importar quando falta insumo (aí pontos e faixa ficam nulos).
     */
    public record Criterio(String nome, String valor, Formato formato, String pontos, String faixa, String fundamento,
                           List<OrigemValor> origens, String naoDisponivel) {

        public static Criterio faltando(String nome, String fundamento, String importe) {
            return new Criterio(nome, null, Formato.TEXTO, null, null, fundamento, List.of(), "não disponível — " + importe);
        }
    }

    /** Item classificado (CDA, inscrição, exercício): os próprios critérios, a classe calculada e a oficial, se houver. */
    public record Item(String id, String descricao, String valor, String indice, String classe, String classeOficial,
                       List<Criterio> criterios, String observacao) {
    }

    public static ResultadoCapacidade de(String metodologia, String titulo, Rotulo rotulo, BigDecimal valor, String classe,
                                         List<Criterio> criterios, List<Item> itens, Map<String, Object> parametros,
                                         List<String> normas, List<String> notas, List<String> alertas) {
        return new ResultadoCapacidade(metodologia, titulo, rotulo, rotulo.texto, valor, classe, criterios, itens, parametros,
                normas, notas, alertas);
    }

    public static String cru(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }
}
