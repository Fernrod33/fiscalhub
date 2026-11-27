package com.fiscalhub.backend.situacao.adaptadores;

import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.pdf.DocumentoPdf;
import com.fiscalhub.backend.pdf.DocumentoPdf.Registro;
import com.fiscalhub.backend.situacao.Extracao;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Relação de pagamentos de ICMS por GR-PR (Receita Estadual do PR), versão ficticio-2026-10. Traz só o CAD/ICMS (sem
 * CNPJ): o cliente vem da inscrição estadual. Parcela de parcelamento vai para o parcelamento.
 */
@Component
class AdaptadorRelacaoGrPr extends AdaptadorFicticio {

    @Override
    public DocumentoTipo tipo() {
        return DocumentoTipo.RELACAO_PAGAMENTOS_GR_PR;
    }

    @Override
    String titulo() {
        return "PAGAMENTOS DE ICMS - GR-PR";
    }

    @Override
    public void extrair(DocumentoPdf pdf, Extracao e) {
        DocumentoPdf.Linha cabecalho = pdf.linha(AdaptadorContaCorrentePr.CAD_ICMS)
                .orElseThrow(() -> new NumberFormatException("Relação de GR-PR sem CAD/ICMS"));
        Matcher ie = AdaptadorContaCorrentePr.CAD_ICMS.matcher(cabecalho.texto());
        e.inscricaoEstadual = ie.find() ? ie.group(1) : null;
        e.inicio = e.fim = emissao(pdf);
        // Data pagto. | Guia | Código receita | Referência | Descrição | Valor
        for (Registro r : DocumentoPdf.tabela(pdf.secao(Pattern.compile("CAD/ICMS .*"), Set.of()),
                Pattern.compile("\\d{2}/\\d{2}/\\d{4}"))) {
            BigDecimal valor = valor(r.get(5));
            e.pagamentos.add(new Extracao.Pagamento("GR_PR", r.get(1).replaceAll("\\D", ""), r.get(2), mes(r.get(3)), r.get(3),
                    null, data(r.get(0)), valor, null, null, valor, AdaptadorGrPr.parcelamento(r.get(4)), origem(r)));
        }
    }
}
