package com.fiscalhub.backend.integracao;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** V33 sobre dados reais da V32: quem tinha senha provisória vira convite pendente, sem senha e sem sessão aberta. */
@TesteIntegracao
class FimDaSenhaProvisoriaMigracaoTest {

    private static final String SCHEMA = "v33_teste";

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void senhaProvisoriaViraConvitePendente() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        try {
            migrarAte("32");
            String s = SCHEMA + ".";
            long escritorio = jdbc.queryForObject("INSERT INTO " + s + "escritorio (nome) VALUES ('E') RETURNING id", Long.class);
            long provisorio = usuario(s, escritorio, "provisorio@test.com", true);
            long definido = usuario(s, escritorio, "definido@test.com", false);
            for (long u : new long[]{provisorio, definido}) {
                jdbc.update("INSERT INTO " + s + "sessao (id, usuario_id, criada_em, ultimo_uso, expira_em) "
                        + "VALUES (gen_random_uuid(), ?, now(), now(), now() + interval '1 hour')", u);
            }

            migrarAte("33");

            Map<String, Object> p = linha(s, provisorio);
            assertThat(p).containsEntry("convite_pendente", true).containsEntry("senha", "!").containsEntry("versao_token", 1)
                    .containsEntry("sessoes_abertas", 0L);
            Map<String, Object> d = linha(s, definido);
            assertThat(d).containsEntry("convite_pendente", false).containsEntry("senha", "$2a$hash").containsEntry("versao_token", 0)
                    .containsEntry("sessoes_abertas", 1L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema = ? "
                    + "AND column_name = 'trocar_senha_no_proximo_login'", Long.class, SCHEMA)).isZero();
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    private void migrarAte(String versao) {
        Flyway.configure().dataSource(dataSource).schemas(SCHEMA).locations("classpath:db/migration").target(versao)
                .load().migrate();
    }

    private long usuario(String s, long escritorio, String email, boolean provisoria) {
        return jdbc.queryForObject("INSERT INTO " + s + "usuario (nome, email, senha, ativo, data_criacao, escritorio_id, papel, "
                + "trocar_senha_no_proximo_login) VALUES ('U', ?, '$2a$hash', true, now(), ?, 'CONSULTOR', ?) RETURNING id",
                Long.class, email, escritorio, provisoria);
    }

    private Map<String, Object> linha(String s, long id) {
        return jdbc.queryForMap("SELECT convite_pendente, senha, versao_token, (SELECT count(*) FROM " + s
                + "sessao WHERE usuario_id = u.id AND revogada_em IS NULL) AS sessoes_abertas FROM " + s + "usuario u WHERE id = ?", id);
    }
}
