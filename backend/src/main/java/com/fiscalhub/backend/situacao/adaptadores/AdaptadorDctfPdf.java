package com.fiscalhub.backend.situacao.adaptadores;

import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.pdf.DocumentoPdf;
import com.fiscalhub.backend.pdf.DocumentoPdf.Registro;
import com.fiscalhub.backend.situacao.Extracao;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Impressão da DCTF Mensal (PGD), versão ficticio-2026-10: período, original ou retificadora, débitos apurados e os
 * créditos vinculados (pagamento, compensação, suspensão, parcelamento). O .dec do mesmo período é lido pelo
 * LeitorDctfDec; os dois são conferidos um contra o outro na gravação.
 */
@Component
class AdaptadorDctfPdf extends AdaptadorFicticio {

    private static final String DEBITOS = "Débitos apurados e créditos vinculados";
    private static final Pattern PERIODO = Pattern.compile("Período: (\\p{L}+)/(\\d{4}) — Declaração (Original|Retificadora)");
    private static final List<String> MESES = List.of("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho",
            "agosto", "setembro", "outubro", "novembro", "dezembro");

    @Override
    public DocumentoTipo tipo() {
        return DocumentoTipo.DCTF;
    }

    @Override
    String titulo() {
        return "PGD DCTF Mensal";
    }

    @Override
    public void extrair(DocumentoPdf pdf, Extracao e) {
        empresa(pdf, e);
        Matcher periodo = PERIODO.matcher(pdf.texto());
        if (!periodo.find()) {
            throw new NumberFormatException("DCTF sem a linha \"Período: <mês>/<ano> — Declaração ...\"");
        }
        YearMonth mes = YearMonth.of(Integer.parseInt(periodo.group(2)), MESES.indexOf(periodo.group(1).toLowerCase()) + 1);
        e.inicio = mes.atDay(1);
        e.fim = mes.atEndOfMonth();
        e.finalidade = "Retificadora".equals(periodo.group(3)) ? "SUBSTITUTA" : "ORIGINAL";
        // Código | Tributo | Período | Vencimento | Débito apurado | Créditos vinculados | Tipo
        for (Registro r : DocumentoPdf.tabela(pdf.secao(DEBITOS, Set.of()), Pattern.compile("\\d{4}-\\d{2}"))) {
            BigDecimal vinculado = valor(r.get(5));
            String tipo = r.get(6).toLowerCase();
            e.declarados.add(new Extracao.Declarado(r.get(0), mes(r.get(2)), data(r.get(3)), valor(r.get(4)),
                    tipo.startsWith("pagamento") ? vinculado : null, tipo.startsWith("compensa") ? vinculado : null,
                    tipo.startsWith("suspens") ? vinculado : null, tipo.startsWith("parcelamento") ? vinculado : null,
                    origem(r)));
        }
    }
}
