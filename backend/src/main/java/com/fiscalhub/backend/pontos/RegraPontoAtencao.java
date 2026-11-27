package com.fiscalhub.backend.pontos;

import com.fiscalhub.backend.situacao.SituacaoFiscalService.OrigemValor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Uma regra de ponto de atenção (bean). Não recalcula nada: consulta os serviços pelo {@link ContextoPontos} e diz o que
 * achou, com as evidências. Limites, severidade padrão, fundamento e recomendação vêm da definição versionada
 * ({@code resources/pontos/regras.json}), sobreposta pelos parâmetros do escritório.
 */
public interface RegraPontoAtencao {

    String codigo();

    /** Achados da regra para o cliente do contexto, ou "não avaliada" com o que falta importar. */
    Avaliacao avaliar(ContextoPontos ctx, Definicao def);

    enum Severidade { CRITICA, ALTA, MEDIA, INFO }

    enum Categoria { CONFORMIDADE, RISCO, OPORTUNIDADE, MONITORAMENTO }

    /** Definição de uma regra (dado), já com os parâmetros e a severidade do escritório aplicados. */
    record Definicao(String codigo, String versao, String titulo, Categoria categoria, Severidade severidade, String fundamento,
                     String recomendacao, Map<String, Object> parametros, boolean conferido) {
        public BigDecimal decimal(String nome) {
            return new BigDecimal(String.valueOf(parametros.get(nome)));
        }

        public int inteiro(String nome) {
            return decimal(nome).intValue();
        }

        @SuppressWarnings("unchecked")
        public List<String> lista(String nome) {
            return (List<String>) parametros.get(nome);
        }
    }

    /**
     * Uma evidência: o dado que sustenta o achado (tipo e id do recurso, valor) e de onde ele veio (documento, página ou
     * registro e linha). link: caminho relativo à tela do cliente onde o dado aparece (aba e filtro).
     */
    record Evidencia(String tipo, String recursoId, String descricao, BigDecimal valor, OrigemValor origem, String link) {
    }

    /** chave: identidade do achado entre execuções (sem o código da regra; o serviço prefixa). severidade nula = a da definição. */
    record Achado(String chave, Severidade severidade, String titulo, String descricao, BigDecimal valor, List<Evidencia> evidencias) {
    }

    record Avaliacao(List<Achado> achados, String naoAvaliada) {
        public static Avaliacao de(List<Achado> achados) {
            return new Avaliacao(achados, null);
        }

        public static Avaliacao naoAvaliada(String falta) {
            return new Avaliacao(List.of(), falta);
        }
    }
}
