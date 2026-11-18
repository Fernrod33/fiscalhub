package com.fiscalhub.backend.email;

/** Porta de envio de e-mail. Adaptador: EnvioSmtp (Amazon SES em produção, Mailpit no desenvolvimento). */
public interface EnvioDeEmail {

    /** logoPng: logotipo do escritório (inline, cid:logo), ou null. */
    record Mensagem(String para, String assunto, String html, String texto, byte[] logoPng) {
    }

    void enviar(Mensagem mensagem);
}
