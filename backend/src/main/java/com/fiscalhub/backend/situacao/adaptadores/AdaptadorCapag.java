package com.fiscalhub.backend.situacao.adaptadores;

import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import com.fiscalhub.backend.pdf.DocumentoPdf;
import com.fiscalhub.backend.pdf.DocumentoPdf.Registro;
import com.fiscalhub.backend.situacao.Extracao;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tela da CAPAG (Regularize/PGFN), versão ficticio-2026-10: classe, capacidade de pagamento estimada e passivo na PGFN.
 * A classe é conferida com a razão capacidade ÷ passivo pelas faixas padrão dos parâmetros de capacidade (A ≥ 2; B ≥ 1;
 * C ≥ 0,5; D < 0,5): o texto da Portaria PGFN 6.757/2022 não fixa os múltiplos (conferido em 28/10/2026).
 */
@Component
class AdaptadorCapag extends AdaptadorFicticio {

    @Override
    public DocumentoTipo tipo() {
        return DocumentoTipo.CAPAG;
    }

    @Override
    String titulo() {
        return "CAPACIDADE DE PAGAMENTO (CAPAG)";
    }

    @Override
    public void extrair(DocumentoPdf pdf, Extracao e) {
        empresaNaLinha(pdf, "Contribuinte:", e);
        Registro classe = pdf.campo("Classificação da CAPAG").orElseThrow(() -> new NumberFormatException("CAPAG sem classe"));
        BigDecimal capacidade = pdf.campo("Capacidade de pagamento estimada").map(c -> valor(c.get(1))).orElse(null);
        BigDecimal passivo = pdf.campo("Passivo fiscal total na PGFN").map(c -> valor(c.get(1))).orElse(null);
        e.inicio = e.fim = pdf.campo("Data de referência do cálculo").map(c -> data(c.get(1))).orElseGet(() -> emissao(pdf));
        String esperada = classePelaPortaria(capacidade, passivo);
        Boolean consistente = esperada == null ? null : esperada.equals(classe.get(1));
        e.capacidades.add(new Extracao.Capacidade("PGFN", classe.get(1), capacidade, passivo, e.fim, consistente, origem(classe)));
        if (Boolean.FALSE.equals(consistente)) {
            e.alerta(Severidade.ALERTA, "CAPAG_INCONSISTENTE", "A CAPAG informa a classe " + classe.get(1)
                    + ", mas capacidade ÷ passivo = " + capacidade.divide(passivo, 2, RoundingMode.HALF_UP).toPlainString()
                    + "x dá a classe " + esperada + " pelas faixas padrão (A ≥ 2x; B ≥ 1x; C ≥ 0,5x; D < 0,5x; não fixadas no texto da Portaria).",
                    origem(classe));
        }
    }

    /** Pelas faixas padrão dos parâmetros de capacidade (as mesmas da CAPAG estimada). Sem capacidade ou passivo: null. */
    static String classePelaPortaria(BigDecimal capacidade, BigDecimal passivo) {
        if (capacidade == null || passivo == null || passivo.signum() <= 0) {
            return null;
        }
        return com.fiscalhub.backend.capacidade.CapagEstimada.classe(capacidade.divide(passivo, 6, RoundingMode.HALF_UP),
                com.fiscalhub.backend.capacidade.ParametrosCapacidade.PADRAO.capag());
    }
}
