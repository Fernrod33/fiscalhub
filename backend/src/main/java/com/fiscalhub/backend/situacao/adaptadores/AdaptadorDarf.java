package com.fiscalhub.backend.situacao.adaptadores;

import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import com.fiscalhub.backend.pdf.DocumentoPdf;
import com.fiscalhub.backend.pdf.DocumentoPdf.Registro;
import com.fiscalhub.backend.pdf.LeitorPdf;
import com.fiscalhub.backend.situacao.Extracao;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DARF pago (com a autenticação bancária), versão ficticio-2026-10: um pagamento por código da composição. O mesmo
 * número de documento no relatório consolidado é o mesmo pagamento (duas origens). Acréscimos legais numa linha à
 * parte vão para o código único do DARF.
 */
@Component
class AdaptadorDarf extends AdaptadorFicticio {

    private static final Pattern NUMERO = Pattern.compile("Número do documento: ([\\d.\\-]+)");

    @Override
    public DocumentoTipo tipo() {
        return DocumentoTipo.DARF;
    }

    @Override
    String titulo() {
        return "DOCUMENTO DE ARRECADAÇÃO DE RECEITAS FEDERAIS - DARF";
    }

    @Override
    public void extrair(DocumentoPdf pdf, Extracao e) {
        e.cnpj = pdf.campo("03 CNPJ").map(c -> LeitorPdf.cnpj(c.get(1))).orElse(null);
        e.nome = pdf.campo("01 Contribuinte").map(c -> c.get(1).replaceFirst(" — Tel\\..*$", "")).orElse(null);
        Matcher numero = NUMERO.matcher(pdf.texto());
        String documento = numero.find() ? numero.group(1) : null;
        String periodo = pdf.campo("02 Período de apuração").map(c -> c.get(1)).orElse("");
        LocalDate vencimento = pdf.campo("06 Data de vencimento").map(c -> data(c.get(1))).orElse(null);
        LocalDate pagoEm = pdf.campo("Pago em (autenticação bancária)").map(c -> data(c.get(1)))
                .orElseThrow(() -> new NumberFormatException("DARF sem a data de pagamento (autenticação bancária)"));
        e.inicio = e.fim = pagoEm;
        YearMonth pa = mes(periodo);
        // Código | Denominação | Principal | Multa | Juros | Total
        List<Registro> itens = DocumentoPdf.tabela(pdf.secao("Composição do documento", Set.of()),
                Pattern.compile("\\d{4}(-\\d{2})?"));
        BigDecimal[] acrescimos = acrescimos(pdf);
        for (Registro r : itens) {
            BigDecimal multa = valor(r.get(3));
            BigDecimal juros = valor(r.get(4));
            BigDecimal total = valor(r.get(5));
            if (acrescimos != null && itens.size() == 1) {
                multa = multa.add(acrescimos[0]);
                juros = juros.add(acrescimos[1]);
                total = total.add(acrescimos[0]).add(acrescimos[1]);
            }
            e.pagamentos.add(new Extracao.Pagamento("DARF", documento, r.get(0), pa, periodo, vencimento, pagoEm,
                    valor(r.get(2)), multa, juros, total, null, origem(r)));
        }
        if (acrescimos != null && itens.size() > 1) {
            e.alerta(Severidade.ALERTA, "ACRESCIMOS_SEM_CODIGO", "O DARF tem acréscimos legais numa linha à parte e mais de "
                    + "um código: multa e juros não foram atribuídos a nenhum deles.", null);
        }
    }

    /** Linha "Acréscimos legais" (multa e juros fora dos itens): [multa, juros] ou null. */
    private static BigDecimal[] acrescimos(DocumentoPdf pdf) {
        return DocumentoPdf.tabela(pdf.secao("Composição do documento", Set.of()), Pattern.compile("")).stream()
                .filter(r -> r.get(1).startsWith("Acréscimos legais"))
                .map(r -> new BigDecimal[]{valor(r.get(3)), valor(r.get(4))}).findFirst().orElse(null);
    }
}
