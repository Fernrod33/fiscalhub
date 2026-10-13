package com.fiscalhub.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Disco local, para desenvolvimento. */
@Component
@ConditionalOnProperty(name = "fiscalhub.storage.tipo", havingValue = "local", matchIfMissing = true)
public class ArmazenamentoLocal implements ArmazenamentoArquivos {

    private final Path raiz;

    public ArmazenamentoLocal(@Value("${fiscalhub.storage.local.diretorio}") String diretorio) {
        this.raiz = Path.of(diretorio).toAbsolutePath().normalize();
    }

    @Override
    public void salvar(String chave, InputStream conteudo, long tamanho, String contentType) {
        Path destino = caminho(chave);
        try {
            Files.createDirectories(destino.getParent());
            Files.copy(conteudo, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar o arquivo " + chave, e);
        }
    }

    @Override
    public InputStream abrir(String chave) {
        try {
            return Files.newInputStream(caminho(chave));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao abrir o arquivo " + chave, e);
        }
    }

    @Override
    public void excluir(String chave) {
        try {
            Files.deleteIfExists(caminho(chave));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao excluir o arquivo " + chave, e);
        }
    }

    @Override
    public boolean existe(String chave) {
        return Files.isRegularFile(caminho(chave));
    }

    /** A chave nunca sai da raiz (defesa contra path traversal). */
    private Path caminho(String chave) {
        Path p = raiz.resolve(chave).normalize();
        if (!p.startsWith(raiz)) {
            throw new IllegalArgumentException("Chave de armazenamento inválida.");
        }
        return p;
    }
}
