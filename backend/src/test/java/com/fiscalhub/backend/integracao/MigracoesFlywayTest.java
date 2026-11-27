package com.fiscalhub.backend.integracao;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Banco do zero sobe só com as migrações, e o Hibernate (ddl-auto=validate) aceita o schema resultante. */
@TesteIntegracao
class MigracoesFlywayTest {

    @Autowired Flyway flyway;

    @Test
    void contextoSobeComTodasAsMigracoesAplicadas() {
        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(versoes(flyway.info().applied())).isEqualTo(versoes(flyway.info().all()));
    }

    private static List<String> versoes(MigrationInfo[] migracoes) {
        return Arrays.stream(migracoes).map(m -> m.getVersion().toString()).toList();
    }
}
