package com.fiscalhub.backend.auditoria;

/** Ações auditadas e o tipo de recurso de cada uma. */
public enum AcaoAuditoria {
    LOGIN("USUARIO"),
    DOCUMENTO_IMPORTAR("DOCUMENTO"),
    DOCUMENTO_BAIXAR("DOCUMENTO"),
    DOCUMENTO_EXCLUIR("DOCUMENTO"),
    DOCUMENTO_REPROCESSAR("DOCUMENTO"),
    DOCUMENTO_CONFIRMAR_CLIENTE("DOCUMENTO"),
    PLANILHA_MAPEAR_COLUNAS("DOCUMENTO"),
    PLANILHA_SALVAR_DEPARA("DOCUMENTO"),
    CLIENTE_CRIAR("CLIENTE"),
    CLIENTE_EDITAR("CLIENTE"),
    CLIENTE_EXCLUIR("CLIENTE"),
    SIMULACAO_CRIAR("SIMULACAO"),
    USUARIO_CRIAR("USUARIO"),
    USUARIO_ALTERAR_PAPEL("USUARIO"),
    USUARIO_DESATIVAR("USUARIO"),
    IDENTIDADE_ALTERAR("ESCRITORIO"),
    IDENTIDADE_LOGO("ESCRITORIO"),
    RELATORIO_EMITIR("RELATORIO"),
    RELATORIO_BAIXAR("RELATORIO");

    private final String recurso;

    AcaoAuditoria(String recurso) {
        this.recurso = recurso;
    }

    public String recurso() {
        return recurso;
    }
}
