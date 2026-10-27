package com.fiscalhub.backend.pdf;

import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.situacao.Extracao;

/**
 * Leitura de um tipo de documento em PDF, numa versão de layout (camada 2, sobre o {@link LeitorPdf}). Cada adaptador é
 * um @Component; o ProcessadorPdf tenta todos. Layout novo (outro sistema, outra versão, PDF real): adaptador novo,
 * sem mexer nos existentes.
 * <p>
 * reconhece: só pelo conteúdo, com trechos que identificam o documento E a versão do layout (nunca "parece").
 * extrair: lê e preenche a extração, cada valor com a origem (página, linha e trecho); não grava nada.
 */
public interface AdaptadorDocumentoPdf {

    DocumentoTipo tipo();

    /** Versão do layout lido (ex.: ficticio-2026-10). Vai para a escrituração do documento. */
    String versao();

    boolean reconhece(DocumentoPdf pdf);

    void extrair(DocumentoPdf pdf, Extracao extracao);
}
