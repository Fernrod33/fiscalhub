package com.fiscalhub.backend.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** SMTP (Spring Mail): HTML com alternativa em texto e o logotipo inline. */
@Component
public class EnvioSmtp implements EnvioDeEmail {

    private final JavaMailSender mail;
    private final String remetente;

    public EnvioSmtp(JavaMailSender mail, @Value("${fiscalhub.email.remetente}") String remetente) {
        this.mail = mail;
        this.remetente = remetente;
    }

    @Override
    public void enviar(Mensagem m) {
        MimeMessage mime = mail.createMimeMessage();
        try {
            MimeMessageHelper h = new MimeMessageHelper(mime, true, "UTF-8");
            h.setFrom(remetente);
            h.setTo(m.para());
            h.setSubject(m.assunto());
            h.setText(m.texto(), m.html());
            if (m.logoPng() != null) {
                h.addInline("logo", new ByteArrayResource(m.logoPng()), "image/png");
            }
        } catch (MessagingException e) {
            throw new MailPreparationException(e);
        }
        mail.send(mime);
    }
}
