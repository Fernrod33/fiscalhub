package com.fiscalhub.backend.capacidade;

import com.fiscalhub.backend.capacidade.DfcDiretaService.Dfc;
import com.fiscalhub.backend.contabil.DemonstracaoService;
import com.fiscalhub.backend.situacao.SituacaoFiscalService;
import com.fiscalhub.backend.situacao.SituacaoFiscalService.OrigemValor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Uma metodologia de capacidade de pagamento: CAPAG federal estimada, classificação do PR ou análise gerencial. Todas
 * recebem os mesmos insumos (montados uma vez pelo CapacidadeService a partir dos serviços que já existem) e devolvem o
 * mesmo formato (ResultadoCapacidade). Metodologia nova = um @Component a mais.
 */
public interface MetodologiaCapacidade {

    /** Ordem de exibição e chave na API (CAPAG_ESTIMADA, PR, GERENCIAL). */
    String chave();

    ResultadoCapacidade calcular(Insumos insumos);

    /** Dados de uma dívida ativa (divida_detalhe), juntando as fontes pelo número. */
    record Detalhe(String numero, LocalDate dataInscricao, String origemDivida, String ajuizamento, Integer parcelamentos,
                   String garantia, LocalDate suspensaoDesde, String classeOficial, List<OrigemValor> origens) {
    }

    record SituacaoCadastral(String orgao, String situacao, LocalDate desde, LocalDate dataReferencia, OrigemValor origem) {
    }

    /** Faturamento declarado na EFD ICMS/IPI (C190 das saídas) nos meses de..ate; faltando: meses sem EFD. */
    record Faturamento(BigDecimal valor, YearMonth de, YearMonth ate, List<OrigemValor> origens, List<YearMonth> faltando) {
    }

    /** Simulação de ações no PR: CDAs a quitar ou parcelar (não altera dado importado). */
    record Simulacao(Set<String> quitar, Set<String> parcelar) {
        public static final Simulacao NENHUMA = new Simulacao(Set.of(), Set.of());

        public boolean vazia() {
            return quitar.isEmpty() && parcelar.isEmpty();
        }
    }

    /**
     * hoje: data do cálculo federal e gerencial; dataCalculoPr: posição dos documentos do PR (a conta corrente mais
     * recente), que é a data do cálculo estadual.
     */
    record Insumos(long clienteId, LocalDate hoje, LocalDate dataCalculoPr, DemonstracaoService.Comparativo dre,
                   DemonstracaoService.Comparativo bp, Map<Integer, Dfc> dfcs, List<SituacaoFiscalService.DebitoConsolidado> debitos,
                   List<SituacaoFiscalService.ParcelamentoFiscal> parcelamentos, List<SituacaoFiscalService.PagamentoConsolidado> pagamentos,
                   List<SituacaoFiscalService.CapacidadeOficial> capacidadesOficiais, Map<String, Detalhe> detalhes,
                   Map<String, SituacaoCadastral> situacoes, Faturamento faturamento, ParametrosCapacidade.Parametros parametros,
                   Simulacao simulacao) {

        /** Valor de uma linha do comparativo (DRE ou BP referencial) no exercício; sem a linha: null. */
        public static BigDecimal linha(DemonstracaoService.Comparativo c, String codigo, int ano) {
            return c.linhas().stream().filter(l -> l.codigo().equals(codigo)).findFirst()
                    .map(l -> l.valores().get(ano)).map(DemonstracaoService.Valor::valor).orElse(null);
        }

        /** Soma das linhas existentes (as ausentes contam zero). */
        public static BigDecimal soma(DemonstracaoService.Comparativo c, List<String> codigos, int ano) {
            return codigos.stream().map(k -> linha(c, k, ano)).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public static OrigemValor origem(DemonstracaoService.Comparativo c, int ano, String codigo) {
            return c.exercicios().stream().filter(e -> e.exercicio() == ano).findFirst()
                    .map(e -> new OrigemValor(e.documentoId(), e.arquivo(), e.origemDados(), null,
                            (c.tipo() + " referencial " + codigo), null, null)).orElse(null);
        }
    }
}
