package com.fiscalhub.backend.entity.enums;

import java.util.Set;

public enum DocumentoTipo {
    IRPJ,
    CSLL,
    PIS,
    COFINS,
    IPI,
    DCTF,
    DCTF_WEB,
    EFD_CONTRIBUICOES,
    ECD,
    ECF,
    EFD_ICMS_IPI,
    ICMS,
    DEMONSTRACAO_CONTABIL,
    OUTRO;

    public static DocumentoTipo fromTributos(Set<TipoTributo> tributos) {
        if (tributos != null && tributos.contains(TipoTributo.ICMS) && tributos.contains(TipoTributo.IPI)) {
            return EFD_ICMS_IPI;
        }
        return OUTRO;
    }
}
