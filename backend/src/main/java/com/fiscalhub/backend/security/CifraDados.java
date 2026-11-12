package com.fiscalhub.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra AES-256-GCM de segredos que o servidor precisa ler de volta (o segredo do TOTP): a chave vem de CHAVE_DADOS
 * (32 bytes em Base64), separada do JWT_SECRET e do banco. Formato gravado: "v1:" + Base64(nonce de 12 bytes + cifra).
 */
@Component
public class CifraDados {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private final SecretKeySpec chave;

    public CifraDados(@Value("${app.chave-dados}") String chaveBase64) {
        byte[] bytes = Base64.getDecoder().decode(chaveBase64.trim());
        if (bytes.length != 32) {
            throw new IllegalStateException("CHAVE_DADOS precisa ter 32 bytes em Base64 (openssl rand -base64 32).");
        }
        this.chave = new SecretKeySpec(bytes, "AES");
    }

    public String cifrar(String texto) {
        try {
            byte[] nonce = new byte[12];
            ALEATORIO.nextBytes(nonce);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(128, nonce));
            byte[] cifrado = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + cifrado.length).put(nonce).put(cifrado).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String decifrar(String gravado) {
        try {
            byte[] tudo = Base64.getDecoder().decode(gravado.substring("v1:".length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(128, tudo, 0, 12));
            return new String(c.doFinal(tudo, 12, tudo.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Segredo cifrado ilegível (CHAVE_DADOS trocada?)", e);
        }
    }
}
