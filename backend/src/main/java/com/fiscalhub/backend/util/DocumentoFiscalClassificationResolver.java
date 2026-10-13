package com.fiscalhub.backend.util;

import com.fiscalhub.backend.entity.enums.DocumentoEsfera;
import com.fiscalhub.backend.entity.enums.DocumentoJuridicao;
import com.fiscalhub.backend.entity.enums.TipoTributo;

import java.util.Set;

public final class DocumentoFiscalClassificationResolver {

    private DocumentoFiscalClassificationResolver() {
    }

    public static DocumentoEsfera classificarEsfera(TipoTributo tributo) {
        return classificarEsfera(Set.of(tributo));
    }

    public static DocumentoEsfera classificarEsfera(Set<TipoTributo> tributos) {
        if (tributos == null || tributos.isEmpty()) {
            return DocumentoEsfera.FEDERAL;
        }
        if (tributos.contains(TipoTributo.ICMS)) {
            return DocumentoEsfera.ESTADUAL;
        }
        if (tributos.contains(TipoTributo.IRPJ)
                || tributos.contains(TipoTributo.CSLL)
                || tributos.contains(TipoTributo.PIS)
                || tributos.contains(TipoTributo.COFINS)
                || tributos.contains(TipoTributo.IPI)) {
            return DocumentoEsfera.FEDERAL;
        }
        return DocumentoEsfera.CONTABIL;
    }

    public static DocumentoJuridicao classificarJuridicao(TipoTributo tributo) {
        return classificarJuridicao(Set.of(tributo));
    }

    public static DocumentoJuridicao classificarJuridicao(Set<TipoTributo> tributos) {
        if (tributos == null || tributos.isEmpty()) {
            return DocumentoJuridicao.NAO_APLICAVEL;
        }
        if (tributos.contains(TipoTributo.ICMS)) {
            return DocumentoJuridicao.PARANA;
        }
        if (tributos.contains(TipoTributo.IRPJ)
                || tributos.contains(TipoTributo.CSLL)
                || tributos.contains(TipoTributo.PIS)
                || tributos.contains(TipoTributo.COFINS)
                || tributos.contains(TipoTributo.IPI)) {
            return DocumentoJuridicao.UNIAO;
        }
        return DocumentoJuridicao.NAO_APLICAVEL;
    }
}
