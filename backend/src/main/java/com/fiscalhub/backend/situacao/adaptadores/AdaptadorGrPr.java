package com.fiscalhub.backend.situacao.adaptadores;

import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.pdf.DocumentoPdf;
import com.fiscalhub.backend.pdf.DocumentoPdf.Registro;
import com.fiscalhub.backend.pdf.LeitorPdf;
import com.fiscalhub.backend.situacao.Extracao;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GR-PR individual (guia de recolhimento do Paraná paga), versão ficticio-2026-10. A guia não traz número: o mesmo
 * pagamento na relação de GR-PR é achado por receita, referência e data. Parcela de parcelamento (o número do
 * parcelamento na descrição da receita) vai para o parcelamento.
 */
@Component
class AdaptadorGrPr extends AdaptadorFicticio {

    static final Pattern PARCELAMENTO_PR = Pattern.compile("PR-\\d{4}-\\d+");

    @Override
    public DocumentoTipo tipo() {
        return DocumentoTipo.GR_PR;
    }

    @Override
    String titulo() {
        return "GUIA DE RECOLHIMENTO DO ESTADO DO PARANÁ - GR-PR";
    }

    @Override
    public void extrair(DocumentoPdf pdf, Extracao e) {
        e.cnpj = pdf.campo("CNPJ").map(c -> LeitorPdf.cnpj(c.get(1))).orElse(null);
        e.nome = pdf.campo("Contribuinte").map(c -> c.get(1)).orElse(null);
        e.inscricaoEstadual = pdf.campo("CAD/ICMS").map(c -> c.get(1)).orElse(null);
        Registro receita = campo(pdf, "Código da receita");
        String referencia = campo(pdf, "Referência").get(1);
        String[] multaJuros = campo(pdf, "Multa / Juros").get(1).split("/");
        BigDecimal principal = valor(campo(pdf, "Valor principal").get(1));
        e.inicio = e.fim = data(campo(pdf, "Pago em").get(1));
        e.pagamentos.add(new Extracao.Pagamento("GR_PR", null, receita.get(1).substring(0, 4), mes(referencia), referencia,
                data(campo(pdf, "Vencimento").get(1)), e.fim, principal, valor(multaJuros[0]), valor(multaJuros[1]),
                valor(campo(pdf, "Valor total").get(1)), parcelamento(receita.get(1)), origem(receita)));
    }

    static String parcelamento(String descricao) {
        Matcher m = PARCELAMENTO_PR.matcher(descricao);
        return m.find() ? m.group() : null;
    }

    private static Registro campo(DocumentoPdf pdf, String rotulo) {
        return pdf.campo(rotulo).orElseThrow(() -> new NumberFormatException("GR-PR sem o campo " + rotulo));
    }
}
