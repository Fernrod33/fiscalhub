package com.fiscalhub.backend.storage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O mesmo contrato para as duas implementações: local (disco) e S3 (MinIO real via Testcontainers). */
class ArmazenamentoArquivosTest {

    // As imagens oficiais minio/minio saíram do Docker Hub e do quay.io; a da Chainguard é o mesmo binário,
    // fixada por digest para o teste não mudar sozinho.
    private static final MinIOContainer MINIO = new MinIOContainer(DockerImageName
            .parse("cgr.dev/chainguard/minio@sha256:2136d593beacf4461187035eca949fe49abd589d5079735025c711ca58fee0c8")
            .asCompatibleSubstituteFor("minio/minio"));

    @TempDir
    Path diretorio;

    @BeforeAll
    static void subirMinio() {
        MINIO.start();
        try (S3Client admin = S3Client.builder().region(Region.US_EAST_1).forcePathStyle(true)
                .endpointOverride(URI.create(MINIO.getS3URL()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
                .build()) {
            admin.createBucket(r -> r.bucket("teste"));
        }
    }

    @AfterAll
    static void pararMinio() {
        MINIO.stop();
    }

    @Test
    void local() throws IOException {
        verificarContrato(new ArmazenamentoLocal(diretorio.toString()));
    }

    @Test
    void s3MinIO() throws IOException {
        verificarContrato(new ArmazenamentoS3(MINIO.getS3URL(), "us-east-1", "teste", MINIO.getUserName(), MINIO.getPassword()));
    }

    @Test
    void localNaoSaiDaRaiz() {
        ArmazenamentoLocal local = new ArmazenamentoLocal(diretorio.toString());
        assertThatThrownBy(() -> local.existe("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
    }

    private static void verificarContrato(ArmazenamentoArquivos armazenamento) throws IOException {
        byte[] conteudo = "0|SPED|teste".getBytes(StandardCharsets.UTF_8);
        String chave = "clientes/1/arquivo.txt";

        assertThat(armazenamento.existe(chave)).isFalse();
        armazenamento.salvar(chave, new ByteArrayInputStream(conteudo), conteudo.length, "text/plain");
        assertThat(armazenamento.existe(chave)).isTrue();
        try (InputStream in = armazenamento.abrir(chave)) {
            assertThat(in.readAllBytes()).isEqualTo(conteudo);
        }
        armazenamento.excluir(chave);
        assertThat(armazenamento.existe(chave)).isFalse();
    }
}
