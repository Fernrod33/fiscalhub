package com.fiscalhub.backend.importacao;

import com.fiscalhub.backend.entity.enums.DocumentoStatus;

/**
 * O processamento para e espera uma decisão do usuário (confirmar o cliente, mapear a planilha). O que foi gravado é
 * desfeito; depois da decisão o documento é reprocessado. cnpj/nome: empresa lida do arquivo, quando houver.
 */
public class AguardandoUsuario extends RuntimeException {

    private final DocumentoStatus status;
    private final String cnpj;
    private final String nome;

    public AguardandoUsuario(DocumentoStatus status, String mensagem, String cnpj, String nome) {
        super(mensagem);
        this.status = status;
        this.cnpj = cnpj;
        this.nome = nome;
    }

    public DocumentoStatus status() {
        return status;
    }

    public String cnpj() {
        return cnpj;
    }

    public String nome() {
        return nome;
    }
}
