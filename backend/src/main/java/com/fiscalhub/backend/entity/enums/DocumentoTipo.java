package com.fiscalhub.backend.entity.enums;

/**
 * Tipo detectado no conteúdo do arquivo. SPED e .dec pelo DetectorTipoDocumento; PDF pelo adaptador que reconhece o
 * layout (ProcessadorPdf): até lá, e quando nenhum reconhece, fica PDF.
 */
public enum DocumentoTipo {
    ECD,
    ECF,
    EFD_CONTRIBUICOES,
    EFD_ICMS_IPI,
    /** Balancete em planilha (.xlsx ou .csv). */
    BALANCETE,
    RELATORIO_SITUACAO_FISCAL,
    /** Extrato de inscrições em dívida ativa da União (Regularize/PGFN). */
    EXTRATO_REGULARIZE,
    CAPAG,
    /** DCTF Mensal: PDF da impressão ou arquivo .dec. */
    DCTF,
    DCTFWEB,
    DARF,
    /** Relatório consolidado de pagamentos (e-CAC). */
    RELATORIO_PAGAMENTOS,
    /** Conta corrente fiscal e dívida ativa do ICMS (Receita Estadual do PR). */
    CONTA_CORRENTE_PR,
    RTI,
    GR_PR,
    RELACAO_PAGAMENTOS_GR_PR,
    /** PDF ainda sem layout reconhecido. */
    PDF,
    /** SPED não reconhecido ou conteúdo desconhecido. */
    OUTRO
}
