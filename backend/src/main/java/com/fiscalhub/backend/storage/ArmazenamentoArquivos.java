package com.fiscalhub.backend.storage;

import java.io.InputStream;

/**
 * Onde ficam os arquivos importados. Duas implementações reais: local (desenvolvimento) e S3/MinIO,
 * escolhidas por fiscalhub.storage.tipo=local|s3. Tudo por stream: nada é carregado inteiro na memória.
 */
public interface ArmazenamentoArquivos {

    /** Grava o conteúdo sob a chave, lendo o stream até o fim (o chamador fecha o stream). */
    void salvar(String chave, InputStream conteudo, long tamanho, String contentType);

    /** Abre o conteúdo para leitura; quem chama fecha o stream. */
    InputStream abrir(String chave);

    void excluir(String chave);

    boolean existe(String chave);
}
