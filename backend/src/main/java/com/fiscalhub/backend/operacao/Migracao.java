package com.fiscalhub.backend.operacao;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.flywaydb.core.Flyway;
import org.slf4j.LoggerFactory;

/**
 * Migração do banco fora da aplicação: o serviço "migracao" do compose roda esta classe com a imagem do backend, como
 * dono do schema, e termina; o backend só sobe depois, sem a senha do dono. Tudo vem de variáveis FLYWAY_* (URL, USER,
 * PASSWORD, LOCATIONS, PLACEHOLDERS_APP_SENHA), sem contexto Spring. Fora do Docker o Flyway continua na aplicação.
 */
public final class Migracao {

    private Migracao() {
    }

    public static void main(String[] args) {
        // sem o logback-spring.xml (não há Spring aqui), o padrão do logback seria DEBUG
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        Flyway.configure().envVars().load().migrate();
    }
}
