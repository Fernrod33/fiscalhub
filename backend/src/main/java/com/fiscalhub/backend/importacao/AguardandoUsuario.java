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
    private final String assinaturaLayout;

    public AguardandoUsuario(DocumentoStatus status, String mensagem, String cnpj, String nome) {
        this(status, mensagem, cnpj, nome, null);
    }

    public AguardandoUsuario(DocumentoStatus status, String mensagem, String cnpj, String nome, String assinaturaLayout) {
        super(mensagem);
        this.status = status;
        this.cnpj = cnpj;
        this.nome = nome;
        this.assinaturaLayout = assinaturaLayout;
    }

    /** Layout que nenhum adaptador reconhece (LAYOUT_NAO_RECONHECIDO), para as métricas. */
    public String assinaturaLayout() {
        return assinaturaLayout;
    }

    /** O começo do documento sem dígitos (some CNPJ, datas e valores) e sem espaços repetidos, até 200 caracteres. */
    public static String assinatura(String texto) {
        String s = texto.replaceAll("\\d", "#").replaceAll("#+", "#").replaceAll("\\s+", " ").strip();
        return s.length() > 200 ? s.substring(0, 200) : s;
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
