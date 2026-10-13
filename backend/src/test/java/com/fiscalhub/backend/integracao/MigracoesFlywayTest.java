package com.fiscalhub.backend.integracao;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Banco do zero sobe só com as migrações, e o Hibernate (ddl-auto=validate) aceita o schema resultante. */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestcontainer.class)
class MigracoesFlywayTest {

    @Autowired Flyway flyway;

    @Test
    void contextoSobeComTodasAsMigracoesAplicadas() {
        assertThat(Arrays.stream(flyway.info().applied()).map(MigrationInfo::getVersion).map(Object::toString))
                .containsExactly("1", "2");
    }
}
