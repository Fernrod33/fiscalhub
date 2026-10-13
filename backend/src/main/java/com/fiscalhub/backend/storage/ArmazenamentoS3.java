package com.fiscalhub.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.InputStream;
import java.net.URI;

/** S3 ou compatível (MinIO). O bucket precisa existir. Sem credenciais configuradas, usa a cadeia padrão da AWS. */
@Component
@ConditionalOnProperty(name = "fiscalhub.storage.tipo", havingValue = "s3")
public class ArmazenamentoS3 implements ArmazenamentoArquivos {

    private final S3Client s3;
    private final String bucket;

    public ArmazenamentoS3(@Value("${fiscalhub.storage.s3.endpoint:}") String endpoint,
                           @Value("${fiscalhub.storage.s3.regiao:us-east-1}") String regiao,
                           @Value("${fiscalhub.storage.s3.bucket}") String bucket,
                           @Value("${fiscalhub.storage.s3.access-key:}") String accessKey,
                           @Value("${fiscalhub.storage.s3.secret-key:}") String secretKey) {
        AwsCredentialsProvider credenciais = accessKey.isBlank()
                ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        S3ClientBuilder builder = S3Client.builder().region(Region.of(regiao)).credentialsProvider(credenciais);
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true); // MinIO
        }
        this.s3 = builder.build();
        this.bucket = bucket;
    }

    @Override
    public void salvar(String chave, InputStream conteudo, long tamanho, String contentType) {
        s3.putObject(r -> r.bucket(bucket).key(chave).contentType(contentType), RequestBody.fromInputStream(conteudo, tamanho));
    }

    @Override
    public InputStream abrir(String chave) {
        return s3.getObject(r -> r.bucket(bucket).key(chave));
    }

    @Override
    public void excluir(String chave) {
        s3.deleteObject(r -> r.bucket(bucket).key(chave));
    }

    @Override
    public boolean existe(String chave) {
        try {
            s3.headObject(r -> r.bucket(bucket).key(chave));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}
