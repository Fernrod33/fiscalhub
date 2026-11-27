package com.fiscalhub.backend.situacao.adaptadores;

import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.entity.enums.SituacaoDebito;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.pdf.DocumentoPdf;
import com.fiscalhub.backend.pdf.DocumentoPdf.Registro;
import com.fiscalhub.backend.pdf.LeitorPdf;
import com.fiscalhub.backend.situacao.CodigosReceita;
import com.fiscalhub.backend.situacao.Extracao;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conta corrente fiscal e dívida ativa do ICMS (Receita Estadual do PR), versão ficticio-2026-10. Conta corrente: o que
 * a EFD declarou e o que foi recolhido por mês (vira declarado; saldo em aberto vira débito na SEFA-PR). Dívida ativa:
 * CDAs (débito na PGE-PR). Parcelamentos estaduais.
 */
@Component
class AdaptadorContaCorrentePr extends AdaptadorFicticio {

    private static final String DIVIDA_ATIVA = "Dívida ativa";
    private static final String PARCELAMENTOS = "Parcelamentos";
    private static final String DETALHAMENTO = "Detalhamento da dívida ativa";
    private static final String CADASTRO = "Cadastro";
    private static final Set<String> SECOES = Set.of(DIVIDA_ATIVA, PARCELAMENTOS, DETALHAMENTO, CADASTRO);
    static final Pattern CAD_ICMS = Pattern.compile("CAD/ICMS (\\d+)");
    private static final Pattern CAD_ICMS_LINHA = Pattern.compile("CAD/ICMS .*");
    private static final Pattern PARCELAS = Pattern.compile("(\\d+) \\((\\d+) pagas\\)");

    @Override
    public DocumentoTipo tipo() {
        return DocumentoTipo.CONTA_CORRENTE_PR;
    }

    @Override
    String titulo() {
        return "CONTA CORRENTE FISCAL E DÍVIDA ATIVA - ICMS";
    }

    @Override
    public void extrair(DocumentoPdf pdf, Extracao e) {
        DocumentoPdf.Linha cabecalho = pdf.linha(CAD_ICMS).orElseThrow(() -> new NumberFormatException("Sem CAD/ICMS"));
        Matcher ie = CAD_ICMS.matcher(cabecalho.texto());
        e.inscricaoEstadual = ie.find() ? ie.group(1) : null;
        e.cnpj = LeitorPdf.cnpj(cabecalho.texto());
        String[] partes = cabecalho.texto().split(" — ");
        e.nome = partes.length > 2 ? partes[partes.length - 1] : null;
        e.inicio = e.fim = emissao(pdf);

        // Referência | Origem | Tributo | Vencimento | Valor declarado | Valor recolhido | Saldo | Situação
        for (Registro r : DocumentoPdf.tabela(pdf.secao(CAD_ICMS_LINHA, SECOES), Pattern.compile("\\d{2}/\\d{4}"))) {
            TipoTributo tributo = tributo(r.get(2)).orElse(TipoTributo.ICMS);
            String codigo = CodigosReceita.codigo(tributo, "ESTADUAL").orElse(null);
            BigDecimal saldo = valor(r.get(6));
            e.declarados.add(new Extracao.Declarado(codigo, mes(r.get(0)), data(r.get(3)), valor(r.get(4)), valor(r.get(5)),
                    null, null, null, origem(r)));
            if (saldo != null && saldo.signum() > 0) {
                e.debitos.add(new Extracao.Debito(OrgaoCredor.SEFA_PR, tributo, codigo, mes(r.get(0)), r.get(0), data(r.get(3)),
                        saldo, null, null, null, saldo, SituacaoDebito.EM_ABERTO, r.get(7), null, null, null, null, origem(r)));
            }
        }
        // CDA | Origem | Tributo / período | Valor original | Valor atualizado | Situação
        for (Registro r : DocumentoPdf.tabela(pdf.secao(DIVIDA_ATIVA, SECOES), Pattern.compile("\\d{4}\\.\\d+-\\d"))) {
            String situacao = r.get(5);
            e.debitos.add(new Extracao.Debito(OrgaoCredor.PGE_PR, tributo(r.get(2)).orElse(TipoTributo.ICMS), null, null,
                    r.get(2).replaceFirst("^.*? - ", ""), null, valor(r.get(3)), null, null, null, valor(r.get(4)),
                    situacao.startsWith("PARCELADA") ? SituacaoDebito.PARCELADO : SituacaoDebito.INSCRITO_DIVIDA_ATIVA,
                    situacao, r.get(0), r.get(1), null, null, origem(r)));
        }
        // CDA | Data de inscrição | Origem da dívida | Ajuizamento | Parcelamentos (seção opcional: insumo da Fase 4)
        for (Registro r : DocumentoPdf.tabela(pdf.secao(DETALHAMENTO, SECOES), Pattern.compile("\\d{4}\\.\\d+-\\d"))) {
            e.detalhesDivida.add(new Extracao.DetalheDivida(OrgaoCredor.PGE_PR, r.get(0), data(r.get(1)), r.get(2), r.get(3),
                    Integer.valueOf(r.get(4).strip()), null, null, null, origem(r)));
        }
        situacaoCadastral(pdf, OrgaoCredor.SEFA_PR, e);
        // Nº parcelamento | Débitos | Parcelas | Valor da parcela | Saldo | Situação
        for (Registro r : DocumentoPdf.tabela(pdf.secao(PARCELAMENTOS, SECOES), Pattern.compile("PR-\\d{4}-\\d+"))) {
            Matcher parcelas = PARCELAS.matcher(r.get(2));
            boolean temParcelas = parcelas.find();
            e.parcelamentos.add(new Extracao.Parcelamento(OrgaoCredor.PGE_PR, r.get(0), "Parcelamento estadual",
                    temParcelas ? Integer.valueOf(parcelas.group(2)) : null, temParcelas ? Integer.valueOf(parcelas.group(1)) : null,
                    valor(r.get(3)), valor(r.get(4)), AdaptadorRegularize.situacao(r.get(5)),
                    List.of(r.get(1).replaceFirst("^CDA ", "")), origem(r)));
        }
    }
}
