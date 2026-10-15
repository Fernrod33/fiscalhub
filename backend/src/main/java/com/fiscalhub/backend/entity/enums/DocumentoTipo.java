package com.fiscalhub.backend.entity.enums;

/** Tipo detectado no conteúdo do arquivo (DetectorTipoDocumento). Os do fim ficam reservados para a Fase 2b. */
public enum DocumentoTipo {
    ECD,
    ECF,
    EFD_CONTRIBUICOES,
    EFD_ICMS_IPI,
    /** Balancete em planilha (.xlsx ou .csv). */
    BALANCETE,
    RELATORIO_SITUACAO_FISCAL,
    EXTRATO_PGFN,
    DCTF,
    DCTFWEB,
    COMPROVANTE_ARRECADACAO,
    EXTRATO_DIVIDA_ATIVA_PR,
    /** PDF, SPED não reconhecido ou conteúdo desconhecido. */
    OUTRO
}
