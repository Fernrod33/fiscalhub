package com.fiscalhub.backend.importacao;

/** Erro fatal do arquivo: o processamento é desfeito, o documento fica em ERRO e a falha vira inconsistência. */
public class FalhaImportacao extends RuntimeException {

    private final String regra;
    private final String registro;
    private final Long linha;

    public FalhaImportacao(String regra, String mensagem, String registro, Long linha) {
        super(mensagem);
        this.regra = regra;
        this.registro = registro;
        this.linha = linha;
    }

    public FalhaImportacao(String regra, String mensagem) {
        this(regra, mensagem, null, null);
    }

    public String regra() {
        return regra;
    }

    public String registro() {
        return registro;
    }

    public Long linha() {
        return linha;
    }
}
