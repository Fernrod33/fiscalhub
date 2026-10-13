package com.fiscalhub.backend.util;

import com.fiscalhub.backend.entity.enums.DocumentoEsfera;
import com.fiscalhub.backend.entity.enums.DocumentoJuridicao;
import com.fiscalhub.backend.entity.enums.TipoTributo;

import java.util.Collections;
import java.util.Set;

public final class DocumentoFiscalClassificationResolver {

    private static final Set<TipoTributo> FEDERAIS = Set.of(
            TipoTributo.IRPJ, TipoTributo.CSLL, TipoTributo.PIS, TipoTributo.COFINS, TipoTributo.IPI);

    private DocumentoFiscalClassificationResolver() {
    }

    public static DocumentoEsfera classificarEsfera(Set<TipoTributo> tributos) {
        if (tributos == null || tributos.isEmpty()) {
            return DocumentoEsfera.FEDERAL;
        }
        if (tributos.contains(TipoTributo.ICMS)) {
            return DocumentoEsfera.ESTADUAL;
        }
        return federal(tributos) ? DocumentoEsfera.FEDERAL : DocumentoEsfera.CONTABIL;
    }

    public static DocumentoJuridicao classificarJuridicao(Set<TipoTributo> tributos) {
        if (tributos == null || tributos.isEmpty()) {
            return DocumentoJuridicao.NAO_APLICAVEL;
        }
        if (tributos.contains(TipoTributo.ICMS)) {
            return DocumentoJuridicao.PARANA;
        }
        return federal(tributos) ? DocumentoJuridicao.UNIAO : DocumentoJuridicao.NAO_APLICAVEL;
    }

    private static boolean federal(Set<TipoTributo> tributos) {
        return !Collections.disjoint(tributos, FEDERAIS);
    }
}
