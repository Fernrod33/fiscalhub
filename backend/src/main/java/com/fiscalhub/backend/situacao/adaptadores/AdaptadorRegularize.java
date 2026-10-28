package com.fiscalhub.backend.situacao.adaptadores;

import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.entity.enums.SituacaoDebito;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.pdf.DocumentoPdf;
import com.fiscalhub.backend.pdf.DocumentoPdf.Registro;
import com.fiscalhub.backend.situacao.Extracao;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrato de inscrições em dívida ativa da União (Regularize/PGFN), versão ficticio-2026-10: inscrições com principal,
 * multa, juros, encargo legal e situação; negociações (transação) com parcelas e saldo; ajuizamentos.
 */
@Component
class AdaptadorRegularize extends AdaptadorFicticio {

    private static final String NEGOCIACOES = "Negociações";
    private static final String AJUIZAMENTOS = "Ajuizamentos";
    private static final String DADOS = "Dados das inscrições";
    private static final String CADASTRO = "Cadastro";
    private static final Set<String> SECOES = Set.of(NEGOCIACOES, AJUIZAMENTOS, DADOS, CADASTRO);
    private static final Pattern PARCELAS = Pattern.compile("Pagas (\\d+) de (\\d+)");

    @Override
    public DocumentoTipo tipo() {
        return DocumentoTipo.EXTRATO_REGULARIZE;
    }

    @Override
    String titulo() {
        return "EXTRATO DE INSCRIÇÕES EM DÍVIDA ATIVA DA UNIÃO";
    }

    @Override
    public void extrair(DocumentoPdf pdf, Extracao e) {
        empresaNaLinha(pdf, "Devedor:", e);
        e.inicio = e.fim = emissao(pdf);

        // Negociação | Modalidade | Inscrições | Parcelas | Valor da parcela | Saldo devedor | Situação
        Map<String, String> negociacaoDaInscricao = new HashMap<>();
        for (Registro r : DocumentoPdf.tabela(pdf.secao(NEGOCIACOES, SECOES), Pattern.compile("\\d{4}\\.\\d{6}-\\d"))) {
            List<String> inscricoes = Arrays.stream(r.get(2).split(";")).map(String::strip).toList();
            Matcher parcelas = PARCELAS.matcher(r.get(3));
            boolean temParcelas = parcelas.find();
            e.parcelamentos.add(new Extracao.Parcelamento(OrgaoCredor.PGFN, r.get(0), r.get(1),
                    temParcelas ? Integer.valueOf(parcelas.group(1)) : null, temParcelas ? Integer.valueOf(parcelas.group(2)) : null,
                    valor(r.get(4)), valor(r.get(5)), situacao(r.get(6)), inscricoes, origem(r)));
            inscricoes.forEach(i -> negociacaoDaInscricao.put(i, r.get(1) + " nº " + r.get(0) + " - " + r.get(6)));
        }
        // Inscrição | Execução fiscal | Vara | Garantia
        Map<String, String> execucao = new HashMap<>();
        for (Registro r : DocumentoPdf.tabela(pdf.secao(AJUIZAMENTOS, SECOES), padraoInscricao())) {
            execucao.put(r.get(0), r.get(1));
        }
        // Inscrição | Data de inscrição | Garantia | Suspensão judicial desde (seção opcional: irrecuperáveis, art. 25)
        for (Registro r : DocumentoPdf.tabela(pdf.secao(DADOS, SECOES), padraoInscricao())) {
            e.detalhesDivida.add(new Extracao.DetalheDivida(OrgaoCredor.PGFN, r.get(0), data(r.get(1)), null, null, null,
                    r.get(2), data(r.get(3)), null, origem(r)));
        }
        situacaoCadastral(pdf, OrgaoCredor.RFB, e);
        // Inscrição | Receita / Natureza | PA | Principal | Multa | Juros | Encargo legal | Total consolidado | Situação
        List<DocumentoPdf.Linha> inscricoes = pdf.secao(Pattern.compile("Devedor: .*"), SECOES);
        for (Registro r : DocumentoPdf.tabela(inscricoes, padraoInscricao())) {
            String situacao = r.get(8);
            boolean negociada = situacao.contains("NEGOCIADA") || negociacaoDaInscricao.containsKey(r.get(0));
            boolean ajuizada = execucao.containsKey(r.get(0)) || (situacao.contains("AJUIZADA") && !situacao.contains("A SER"));
            e.debitos.add(new Extracao.Debito(OrgaoCredor.PGFN, tributo(r.get(1)).orElse(TipoTributo.OUTROS), null,
                    mes(r.get(2)), r.get(2), null, valor(r.get(3)), valor(r.get(4)), valor(r.get(5)), valor(r.get(6)),
                    valor(r.get(7)), negociada ? SituacaoDebito.NEGOCIADO : SituacaoDebito.INSCRITO_DIVIDA_ATIVA, situacao,
                    r.get(0), execucao.get(r.get(0)), ajuizada, negociacaoDaInscricao.get(r.get(0)), origem(r)));
        }
    }

    static String situacao(String texto) {
        String t = texto.toUpperCase();
        return t.contains("RESCIN") ? "RESCINDIDO" : t.contains("ATRASO") ? "EM_ATRASO" : "EM_DIA";
    }
}
