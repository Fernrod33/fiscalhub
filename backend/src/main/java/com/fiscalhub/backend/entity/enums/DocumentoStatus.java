package com.fiscalhub.backend.entity.enums;

/** Situação do documento importado. As etapas do processamento (lendo, validando...) ficam no job. */
public enum DocumentoStatus {
    RECEBIDO,
    PROCESSANDO,
    PROCESSADO,
    PROCESSADO_COM_ALERTAS,
    ERRO,
    /** O CNPJ do arquivo não bate com nenhum cliente do escritório: o usuário confirma criar ou associar. */
    AGUARDANDO_CLIENTE,
    /** Planilha cujo formato ainda não tem mapeamento de colunas salvo para o cliente. */
    AGUARDANDO_MAPEAMENTO;

    public boolean finalizado() {
        return this != RECEBIDO && this != PROCESSANDO;
    }
}
