package com.fiscalhub.backend.situacao.adaptadores;

import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.entity.enums.SituacaoDebito;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.pdf.DocumentoPdf;
import com.fiscalhub.backend.pdf.DocumentoPdf.Registro;
import com.fiscalhub.backend.situacao.Extracao;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Relatório de Situação Fiscal (e-CAC, RFB + PGFN), versão ficticio-2026-10: débitos do SIEF, omissões de declaração,
 * inscrições do SIDA, inscrições negociadas e processos com exigibilidade suspensa. Os valores das inscrições vêm do
 * extrato do Regularize (mesma inscrição = mesma dívida, com as duas origens).
 */
@Component
class AdaptadorRelatorioSituacaoFiscal extends AdaptadorFicticio {

    private static final String SIEF = "Pendência - Débito (SIEF)";
    private static final String OMISSAO = "Pendência - Omissão de Declaração";
    private static final String SIDA = "Pendência - Inscrição (SIDA)";
    private static final String NEGOCIADOS = "Débitos com Exigibilidade Suspensa / Negociados";
    private static final String PROCESSOS = "Processo Fiscal com Exigibilidade Suspensa (SIEF)";
    private static final Set<String> SECOES = Set.of(SIEF, OMISSAO, SIDA, NEGOCIADOS, PROCESSOS);

    @Override
    public DocumentoTipo tipo() {
        return DocumentoTipo.RELATORIO_SITUACAO_FISCAL;
    }

    @Override
    String titulo() {
        return "INFORMAÇÕES DE APOIO PARA EMISSÃO DE CERTIDÃO";
    }

    @Override
    public void extrair(DocumentoPdf pdf, Extracao e) {
        empresa(pdf, e);
        e.inicio = e.fim = emissao(pdf);
        // Receita | PA | Dt. Vcto | Vl. Original | Sdo. Devedor | Multa | Juros | Sdo. Dev. Cons. | Situação
        for (Registro r : DocumentoPdf.tabela(pdf.secao(SIEF, SECOES), Pattern.compile("\\d{4}-\\d{2} - .+"))) {
            e.debitos.add(new Extracao.Debito(OrgaoCredor.RFB, tributo(r.get(0)).orElse(TipoTributo.OUTROS),
                    r.get(0).substring(0, 7), mes(r.get(1)), r.get(1), data(r.get(2)),
                    valor(r.get(4)), valor(r.get(5)), valor(r.get(6)), null, valor(r.get(7)), SituacaoDebito.EM_ABERTO,
                    r.get(8), null, null, null, null, origem(r)));
        }
        // Declaração | Período | Situação
        for (Registro r : DocumentoPdf.tabela(pdf.secao(OMISSAO, SECOES), Pattern.compile(".+"))) {
            e.pendencias.add(new Extracao.Pendencia("OMISSAO_DECLARACAO", "Omissão de " + r.get(0) + " de " + r.get(1),
                    mes(r.get(1)), r.get(2), origem(r)));
        }
        // Inscrição | Receita | Inscrito em | Ajuizado em | Processo | Situação
        for (Registro r : DocumentoPdf.tabela(pdf.secao(SIDA, SECOES), padraoInscricao())) {
            e.debitos.add(inscricao(r.get(0), r.get(1), r.get(5), !"-".equals(r.get(3)), r.get(4), null, origem(r)));
        }
        // Inscrição | Receita | Situação | Negociação
        for (Registro r : DocumentoPdf.tabela(pdf.secao(NEGOCIADOS, SECOES), padraoInscricao())) {
            e.debitos.add(inscricao(r.get(0), r.get(1), r.get(2), r.get(2).contains("AJUIZADA") && !r.get(2).contains("A SER"),
                    null, r.get(3), origem(r)));
        }
        // Processo | Assunto | Situação: débito com exigibilidade suspensa, sem valores no relatório
        for (Registro r : DocumentoPdf.tabela(pdf.secao(PROCESSOS, SECOES), Pattern.compile("\\d{5}\\.\\d{6}/\\d{4}-\\d{2}"))) {
            e.debitos.add(new Extracao.Debito(OrgaoCredor.RFB, tributo(r.get(1)).orElse(TipoTributo.OUTROS), null, null,
                    r.get(1), null, null, null, null, null, null, SituacaoDebito.SUSPENSO, r.get(2), r.get(0), r.get(0),
                    null, null, origem(r)));
        }
    }

    /** Inscrição em dívida ativa da União listada no relatório (os valores ficam no extrato do Regularize). */
    static Extracao.Debito inscricao(String numero, String receita, String situacao, boolean ajuizado, String processo,
                                     String negociacao, Extracao.Origem origem) {
        boolean negociada = situacao.contains("NEGOCIADA") || negociacao != null;
        return new Extracao.Debito(OrgaoCredor.PGFN, tributo(receita).orElse(TipoTributo.OUTROS), null, null, null, null,
                null, null, null, null, null, negociada ? SituacaoDebito.NEGOCIADO : SituacaoDebito.INSCRITO_DIVIDA_ATIVA,
                situacao, numero, processo, ajuizado, negociacao, origem);
    }
}
