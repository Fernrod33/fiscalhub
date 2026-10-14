package com.fiscalhub.backend.importacao;

import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;

/**
 * Leitura de um tipo de documento. O ProcessamentoDocumentoService detecta o tipo pelo conteúdo e despacha para o
 * primeiro processador (na ordem do Spring) que o suporta.
 * <p>
 * Para adicionar um tipo: implemente esta interface como @Component, leia o arquivo por {@link ContextoProcessamento#abrir()},
 * informe etapas e bytes lidos, registre inconsistências e, se o arquivo identificar a empresa, chame
 * {@link ContextoProcessamento#cliente(String, String)}. Roda numa transação só: exceção desfaz tudo o que gravou.
 * Antes de gravar, apague o que um processamento anterior do mesmo documento gerou (reprocessar roda de novo).
 */
public interface ProcessadorDocumento {

    boolean suporta(DocumentoTipo tipo);

    /** Erro que impede o uso do arquivo: lance {@link FalhaImportacao}. Ação do usuário pendente: {@link AguardandoUsuario}. */
    void processar(DocumentoFiscal documento, ContextoProcessamento contexto);
}
