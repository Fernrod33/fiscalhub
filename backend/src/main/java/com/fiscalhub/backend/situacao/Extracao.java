package com.fiscalhub.backend.situacao;

import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.entity.enums.SituacaoDebito;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.fiscal.RepositorioApuracao.SaldoFiscal;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import com.fiscalhub.backend.pdf.DocumentoPdf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * O que um adaptador leu de um documento (PDF ou .dec), antes de gravar: a empresa, o período do documento e os
 * débitos, parcelamentos, pendências, capacidade de pagamento, declarado e pago. Todo item leva a origem.
 */
public final class Extracao {

    /** De onde veio um valor: página (null no .dec, que não tem página), linha e o trecho lido. */
    public record Origem(Integer pagina, int linha, String trecho) {
        public static Origem de(DocumentoPdf.Origem o) {
            return new Origem(o.pagina(), o.linha(), o.trecho());
        }
    }

    /**
     * Débito no órgão. numero: inscrição, CDA ou processo (dívida ativa); competencia/codigoReceita: débito do período
     * (SIEF, conta corrente). periodo: período como escrito no documento ("2019", "03/2022 a 06/2022").
     */
    public record Debito(OrgaoCredor orgao, TipoTributo tributo, String codigoReceita, YearMonth competencia, String periodo,
                         LocalDate vencimento, BigDecimal principal, BigDecimal multa, BigDecimal juros, BigDecimal encargo,
                         BigDecimal total, SituacaoDebito situacao, String situacaoDocumento, String numero, String processo, Boolean ajuizado,
                         String negociacao, Origem origem) {

        /** A mesma dívida em fontes diferentes: inscrição pelo número; débito do período por órgão, tributo e competência. */
        public String chave() {
            return numero != null ? "INSCRICAO|" + numero.replaceAll("\\D", "")
                    : orgao + "|" + tributo + "|" + competencia;
        }
    }

    /** situacao: EM_DIA, EM_ATRASO ou RESCINDIDO. debitos: inscrições/CDAs vinculadas, como no documento. */
    public record Parcelamento(OrgaoCredor orgao, String numero, String modalidade, Integer parcelasPagas,
                               Integer parcelasTotal, BigDecimal valorParcela, BigDecimal saldo, String situacao,
                               List<String> debitos, Origem origem) {
    }

    /** tipo: OMISSAO_DECLARACAO, PROCESSO_SUSPENSO... */
    public record Pendencia(String tipo, String descricao, YearMonth periodo, String situacao, Origem origem) {
    }

    /**
     * fonte: PGFN (Regularize/CAPAG) ou SEFA_PR (RTI). Insumo da Fase 4. classeConsistente: a classe bate com a razão
     * capacidade/passivo da regra do órgão (null: sem regra para conferir).
     */
    public record Capacidade(String fonte, String classe, BigDecimal capacidade, BigDecimal passivo, LocalDate referencia,
                             Boolean classeConsistente, Origem origem) {
    }

    /** Débito declarado (DCTF, DCTFWeb, conta corrente do ICMS) e as vinculações informadas na declaração. */
    public record Declarado(String codigoReceita, YearMonth periodo, LocalDate vencimento, BigDecimal valor,
                            BigDecimal pago, BigDecimal compensado, BigDecimal suspenso, BigDecimal parcelado,
                            Origem origem) {
    }

    /**
     * Item de um documento de arrecadação. documento: DARF, DAS ou GR_PR. parcelamento: número do parcelamento ou
     * transação quando o pagamento é parcela (não é tributo do período). referencia: período como escrito ("12/36").
     */
    public record Pagamento(String documento, String numero, String codigoReceita, YearMonth periodo, String referencia,
                            LocalDate vencimento, LocalDate dataPagamento, BigDecimal principal, BigDecimal multa,
                            BigDecimal juros, BigDecimal total, String parcelamento, Origem origem) {
    }

    public record Alerta(Severidade severidade, String regra, String mensagem, Origem origem) {
    }

    public String cnpj;
    public String nome;
    /** Documento estadual que não traz o CNPJ (relação de GR-PR): o cliente vem da inscrição estadual (CAD/ICMS). */
    public String inscricaoEstadual;
    /** Período da declaração ou, nos relatórios e extratos, a data de referência (inicio = fim). */
    public LocalDate inicio;
    public LocalDate fim;
    /** ORIGINAL ou SUBSTITUTA (declaração retificadora), como nas escriturações. */
    public String finalidade = "ORIGINAL";
    public final List<Debito> debitos = new ArrayList<>();
    public final List<Parcelamento> parcelamentos = new ArrayList<>();
    public final List<Pendencia> pendencias = new ArrayList<>();
    public final List<Capacidade> capacidades = new ArrayList<>();
    public final List<Declarado> declarados = new ArrayList<>();
    public final List<Pagamento> pagamentos = new ArrayList<>();
    /** Saldos credores mensais (RTI), no modelo da apuração. */
    public final List<SaldoFiscal> saldos = new ArrayList<>();
    public final List<Alerta> alertas = new ArrayList<>();

    public void alerta(Severidade severidade, String regra, String mensagem, Origem origem) {
        alertas.add(new Alerta(severidade, regra, mensagem, origem));
    }
}
