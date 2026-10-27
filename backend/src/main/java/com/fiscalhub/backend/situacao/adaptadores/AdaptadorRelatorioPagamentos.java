package com.fiscalhub.backend.situacao.adaptadores;

import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.pdf.DocumentoPdf;
import com.fiscalhub.backend.pdf.DocumentoPdf.Registro;
import com.fiscalhub.backend.situacao.Extracao;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Relatório consolidado de pagamentos (e-CAC), versão ficticio-2026-10: um pagamento por linha. "DCTFWeb" no código é o
 * DARF da DCTFWeb só com o total (sem código: o DARF do mesmo número traz a composição); "TRANS" é parcela de
 * transação (vai para a transação da denominação).
 */
@Component
class AdaptadorRelatorioPagamentos extends AdaptadorFicticio {

    private static final Pattern TRANSACAO = Pattern.compile("\\d{4}\\.\\d{6}-\\d");

    @Override
    public DocumentoTipo tipo() {
        return DocumentoTipo.RELATORIO_PAGAMENTOS;
    }

    @Override
    String titulo() {
        return "RELATÓRIO CONSOLIDADO DE PAGAMENTOS";
    }

    @Override
    public void extrair(DocumentoPdf pdf, Extracao e) {
        empresaNaLinha(pdf, "Contribuinte:", e);
        e.inicio = e.fim = emissao(pdf);
        // Data arrecad. | Nº documento | Código | Denominação | PA | Vencimento | Principal | Multa | Juros | Total
        for (Registro r : DocumentoPdf.tabela(pdf.secao(Pattern.compile("Contribuinte: .*"), Set.of()),
                Pattern.compile("\\d{2}/\\d{2}/\\d{4}"))) {
            String codigo = r.get(2).replace(" ", "");
            Matcher transacao = TRANSACAO.matcher(r.get(3));
            boolean parcela = "TRANS".equals(codigo) && transacao.find();
            e.pagamentos.add(new Extracao.Pagamento("DARF", r.get(1), codigo.matches("\\d{4}.*") ? codigo : null,
                    mes(r.get(4)), r.get(4), data(r.get(5)), data(r.get(0)), valor(r.get(6)), valor(r.get(7)),
                    valor(r.get(8)), valor(r.get(9)), parcela ? transacao.group() : null, origem(r)));
        }
    }
}
