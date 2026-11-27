package com.fiscalhub.backend.entity.enums;

public enum TipoTributo {
    // Federais
    IRPJ,
    CSLL,
    PIS,
    COFINS,
    IPI,
    ITR,
    IRPF,
    /** Contribuições previdenciárias (segurado, patronal, GILRAT). */
    INSS,
    /** Contribuições de terceiros (salário-educação, INCRA, Sistema S). */
    TERCEIROS,
    IRRF,
    FGTS,
    // Estaduais
    ICMS,
    /** ICMS retido por substituição tributária (EFD ICMS/IPI, E200/E210), por UF. */
    ICMS_ST,
    /** Diferencial de alíquotas da EC 87/2015 (E300/E310), por UF. */
    ICMS_DIFAL,
    /** Fundo de Combate à Pobreza apurado com o DIFAL (E310, campos _FCP), por UF. */
    ICMS_FCP,
    ITCMD,
    IPVA,
    // Municipais
    ISS,
    IPTU,
    ITBI,
    // Outros
    OUTROS
}
