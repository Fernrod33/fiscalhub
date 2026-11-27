package com.fiscalhub.backend.integracao;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL real para os testes de integração (requer Docker). Como em produção, o Flyway migra como dono (o usuário do
 * contêiner) e a aplicação roda como fiscalhub_app (papel criado pelo afterMigrate.sql).
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    @Bean
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:18-alpine");
    }

    @Bean
    DynamicPropertyRegistrar postgresPropriedades(PostgreSQLContainer<?> postgres) {
        return r -> {
            r.add("spring.datasource.url", postgres::getJdbcUrl);
            r.add("spring.flyway.user", postgres::getUsername);
            r.add("spring.flyway.password", postgres::getPassword);
        };
    }
}
