package com.fiscalhub.backend.security;

import io.nayuki.qrcodegen.QrCode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * TOTP da RFC 6238 (HMAC-SHA1, 6 dígitos, passo de 30 s; o que os aplicativos autenticadores entendem), o segredo em
 * Base32 (RFC 4648), a URI otpauth:// e o QR code dela em SVG. Só a JDK e o qrcodegen (uma classe, para o QR).
 */
public final class Totp {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int PASSO = 30;

    private Totp() {
    }

    /** 160 bits aleatórios em Base32 (32 caracteres). */
    public static String novoSegredo() {
        byte[] b = new byte[20];
        ALEATORIO.nextBytes(b);
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < b.length; i += 5) { // 5 bytes = 8 caracteres
            long bloco = 0;
            for (int j = 0; j < 5; j++) {
                bloco = (bloco << 8) | (b[i + j] & 0xff);
            }
            for (int j = 7; j >= 0; j--) {
                s.append(BASE32.charAt((int) (bloco >> (j * 5)) & 31));
            }
        }
        return s.toString();
    }

    static byte[] decodificar(String base32) {
        ByteBuffer out = ByteBuffer.allocate(base32.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : base32.toUpperCase().toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) {
                continue; // espaços e "=" de quem digitou à mão
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return java.util.Arrays.copyOf(out.array(), out.position());
    }

    static String codigo(byte[] segredo, long passo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(segredo, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(passo).array());
            int o = h[h.length - 1] & 0xf;
            int n = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%06d", n % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * O passo de tempo em que o código vale (o atual ou um vizinho, pela folga de relógio do celular), só se for maior
     * que o último aceito: o mesmo código não entra duas vezes (ASVS 2.8.4).
     */
    public static OptionalLong validar(String segredoBase32, String codigo, Long ultimoPassoAceito, Instant agora) {
        if (codigo == null || !codigo.matches("\\d{6}")) {
            return OptionalLong.empty();
        }
        byte[] segredo = decodificar(segredoBase32);
        long atual = agora.getEpochSecond() / PASSO;
        for (long passo = atual - 1; passo <= atual + 1; passo++) {
            if ((ultimoPassoAceito == null || passo > ultimoPassoAceito)
                    && MessageDigest.isEqual(codigo(segredo, passo).getBytes(), codigo.getBytes())) {
                return OptionalLong.of(passo);
            }
        }
        return OptionalLong.empty();
    }

    public static String uri(String emissor, String conta, String segredoBase32) {
        String e = URLEncoder.encode(emissor, StandardCharsets.UTF_8).replace("+", "%20");
        String c = URLEncoder.encode(conta, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + e + ":" + c + "?secret=" + segredoBase32 + "&issuer=" + e + "&algorithm=SHA1&digits=6&period=30";
    }

    /** QR code da URI em SVG (módulos pretos num caminho só, margem de 4 módulos). */
    public static String qrSvg(String texto) {
        QrCode qr = QrCode.encodeText(texto, QrCode.Ecc.MEDIUM);
        int borda = 4;
        int lado = qr.size + borda * 2;
        StringBuilder p = new StringBuilder();
        for (int y = 0; y < qr.size; y++) {
            for (int x = 0; x < qr.size; x++) {
                if (qr.getModule(x, y)) {
                    p.append('M').append(x + borda).append(',').append(y + borda).append("h1v1h-1z");
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + lado + " " + lado + "\" shape-rendering=\"crispEdges\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path d=\"" + p + "\" fill=\"#000\"/></svg>";
    }
}
