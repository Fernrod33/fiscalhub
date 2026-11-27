package com.fiscalhub.backend.situacao.adaptadores;

import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.pdf.DocumentoPdf;
import com.fiscalhub.backend.pdf.DocumentoPdf.Registro;
import com.fiscalhub.backend.pdf.LeitorPdf;
import com.fiscalhub.backend.situacao.Extracao;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DCTFWeb - declaração completa, versão ficticio-2026-10: contribuições previdenciárias e de terceiros (eSocial), IRRF
 * (EFD-Reinf) e a parte do MIT (PIS, COFINS, IPI). Créditos vinculados entram como compensação.
 */
@Component
class AdaptadorDctfWeb extends AdaptadorFicticio {

    private static final String DEBITOS = "Resumo dos débitos";
    private static final Pattern PERIODO = Pattern.compile("Período de apuração: (\\d{2}/\\d{4})");

    @Override
    public DocumentoTipo tipo() {
        return DocumentoTipo.DCTFWEB;
    }

    @Override
    String titulo() {
        return "DCTFWeb - DECLARAÇÃO COMPLETA";
    }

    @Override
    public void extrair(DocumentoPdf pdf, Extracao e) {
        empresa(pdf, e);
        Matcher periodo = PERIODO.matcher(pdf.texto());
        if (!periodo.find()) {
            throw new NumberFormatException("DCTFWeb sem \"Período de apuração: mm/aaaa\"");
        }
        YearMonth mes = LeitorPdf.competencia(periodo.group(1));
        e.inicio = mes.atDay(1);
        e.fim = mes.atEndOfMonth();
        e.finalidade = pdf.contem("Retificadora") ? "SUBSTITUTA" : "ORIGINAL";
        // Código | Descrição | Débito apurado | Créditos vinculados | Saldo a pagar | Vencimento
        for (Registro r : DocumentoPdf.tabela(pdf.secao(DEBITOS, Set.of("Bases de cálculo informadas (eSocial)")),
                Pattern.compile("\\d{4}-\\d{2}"))) {
            var creditos = valor(r.get(3));
            e.declarados.add(new Extracao.Declarado(r.get(0), mes, data(r.get(5)), valor(r.get(2)), null,
                    creditos != null && creditos.signum() != 0 ? creditos : null, null, null, origem(r)));
        }
    }
}
