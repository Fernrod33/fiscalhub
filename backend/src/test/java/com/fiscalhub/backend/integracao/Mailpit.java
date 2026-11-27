package com.fiscalhub.backend.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** API do Mailpit dos testes (MailpitTestcontainer): e-mails recebidos e o caos que simula o SMTP recusando o envio. */
public class Mailpit {

    public record Email(String assunto, String texto, String html) {
        /** Token do link (fragmento depois de #). */
        public String token() {
            Matcher m = Pattern.compile("#([A-Za-z0-9_-]{43})").matcher(texto);
            if (!m.find()) {
                throw new AssertionError("e-mail sem link com token: " + assunto);
            }
            return m.group(1);
        }
    }

    private final String api;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    Mailpit(String api) {
        this.api = api;
    }

    /** E-mails já entregues a este destinatário (o envio é assíncrono: use {@link #esperar}). */
    public List<Email> para(String destinatario) {
        JsonNode busca = get("/api/v1/search?query=" + URLEncoder.encode("to:\"" + destinatario + "\"", StandardCharsets.UTF_8));
        List<Email> emails = new ArrayList<>();
        for (JsonNode m : busca.get("messages")) {
            JsonNode completo = get("/api/v1/message/" + m.get("ID").asText());
            emails.add(new Email(completo.get("Subject").asText(), completo.get("Text").asText(), completo.get("HTML").asText()));
        }
        return emails;
    }

    /** Espera (até 15 s) o destinatário ter pelo menos {@code quantos} e-mails; devolve todos, do mais novo ao mais antigo. */
    public List<Email> esperar(String destinatario, int quantos) {
        long limite = System.currentTimeMillis() + 15_000;
        List<Email> emails = para(destinatario);
        while (emails.size() < quantos && System.currentTimeMillis() < limite) {
            dormir(200);
            emails = para(destinatario);
        }
        if (emails.size() < quantos) {
            throw new AssertionError("esperava " + quantos + " e-mail(s) para " + destinatario + ", chegaram " + emails.size());
        }
        return emails;
    }

    /** Liga ou desliga a recusa (451) de todo destinatário. */
    public void recusarTudo(boolean recusar) {
        String corpo = recusar ? "{\"Recipient\":{\"ErrorCode\":451,\"Probability\":100}}" : "{\"Recipient\":{\"ErrorCode\":451,\"Probability\":0}}";
        enviar(HttpRequest.newBuilder(URI.create(api + "/api/v1/chaos")).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(corpo)).build());
    }

    private JsonNode get(String caminho) {
        try {
            return json.readTree(enviar(HttpRequest.newBuilder(URI.create(api + caminho)).GET().build()));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String enviar(HttpRequest req) {
        try {
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() >= 300) {
                throw new IllegalStateException("Mailpit " + r.statusCode() + ": " + r.body());
            }
            return r.body();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    static void dormir(long ms) {
        try {
            Thread.sleep(Duration.ofMillis(ms));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
