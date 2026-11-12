package com.fiscalhub.backend.operacao;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.boot.logging.logback.ExtendedWhitespaceThrowableProxyConverter;

import java.util.regex.Pattern;

/**
 * Minimização nos logs (LGPD art. 6º, III): CNPJ, CPF e valores em reais saem mascarados de toda linha de log,
 * mensagem e stack trace (uma exceção da importação pode levar o CNPJ do arquivo). Fica só o fim do documento, para
 * quem investiga achar o registro. Ligada em dois pontos: no padrão de texto (logback-spring.xml, %m e %wEx) e no JSON
 * do perfil prod (logging.structured.json.customizer).
 */
public final class MascaraLog {

    private static final Pattern CNPJ = Pattern.compile("(?<!\\d)\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?(\\d{2})(?!\\d)");
    private static final Pattern CPF = Pattern.compile("(?<!\\d)\\d{3}\\.?\\d{3}\\.?\\d{3}-?(\\d{2})(?!\\d)");
    /** R$ 1.234,56 · 1.234,56 · 1234,56 · 12345.67 (4+ dígitos inteiros: não pega IP, versão nem hora). */
    private static final Pattern VALOR = Pattern.compile("R\\$\\s?-?[\\d.]+(,\\d{1,2})?|(?<![\\d.,])-?\\d{1,3}(\\.\\d{3})+,\\d{2}(?!\\d)|"
            + "(?<![\\d.,])-?\\d+,\\d{2}(?!\\d)|(?<![\\d.,])-?\\d{4,}\\.\\d{2}(?![\\d.])");

    private MascaraLog() {
    }

    public static String aplicar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        String s = CNPJ.matcher(texto).replaceAll("[cnpj …$1]");
        s = CPF.matcher(s).replaceAll("[cpf …$1]");
        return VALOR.matcher(s).replaceAll("[valor]");
    }

    /** %m do logback. */
    public static class Mensagem extends MessageConverter {
        @Override
        public String convert(ILoggingEvent evento) {
            return aplicar(super.convert(evento));
        }
    }

    /** %wEx do logback (stack trace). */
    public static class Excecao extends ExtendedWhitespaceThrowableProxyConverter {
        @Override
        public String convert(ILoggingEvent evento) {
            return aplicar(super.convert(evento));
        }
    }

    /** JSON estruturado (perfil prod): todo valor texto (mensagem, stack trace, MDC) passa pela máscara. */
    public static class Json implements StructuredLoggingJsonMembersCustomizer<Object> {
        @Override
        public void customize(JsonWriter.Members<Object> membros) {
            membros.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, MascaraLog::aplicar));
        }
    }
}
