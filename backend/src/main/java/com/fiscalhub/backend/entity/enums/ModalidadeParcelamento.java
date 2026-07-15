package com.fiscalhub.backend.entity.enums;

public enum ModalidadeParcelamento {
    // Receita Federal
    ORDINARIO_RFB,          // Parcelamento ordinário (até 60x, Art. 10 Lei 10.522/02)
    PERT_RFB,               // Programa Especial de Regularização Tributária
    REFIS,                  // Programa de Recuperação Fiscal histórico
    // Receita Estadual PR
    ORDINARIO_SEFA_PR,      // Parcelamento ordinário SEFA-PR
    ESPECIAL_SEFA_PR        // Programa especial SEFA-PR (com reduções de multa/juros)
}
