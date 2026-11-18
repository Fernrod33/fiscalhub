package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.controller.AuthController;
import com.fiscalhub.backend.entity.Escritorio;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.repository.UsuarioRepository;
import com.fiscalhub.backend.security.TotpTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Convite, recuperação de senha e avisos por e-mail, entregues de verdade a um Mailpit. Sem @Transactional: o e-mail só
 * sai depois do commit. E-mails únicos por teste (o banco é compartilhado).
 */
@TesteIntegracao
class ContaPorEmailTest {

    private static final String SENHA_NOVA = "frase-nova-bem-forte";

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired Mailpit mailpit;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbc;

    private Escritorio escritorio;
    private Usuario admin;
    private String sufixo;

    @BeforeEach
    void setUp() {
        sufixo = UUID.randomUUID().toString().substring(0, 8);
        escritorio = dados.escritorio("Correio " + sufixo);
        admin = dados.usuario(escritorio, "admin-" + sufixo + "@test.com", Papel.ADMIN);
    }

    @Test
    void conviteDefineASenhaUmaVezSoEAuditoriaNaoGuardaOConteudo() throws Exception {
        String email = "convidado-" + sufixo + "@test.com";
        mvc.perform(post("/api/usuarios").with(dados.sessaoReautenticada(admin)).contentType(APPLICATION_JSON)
                        .content("{\"nome\":\"Caio\",\"email\":\"" + email + "\",\"papel\":\"CONSULTOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.convitePendente").value(true));

        Mailpit.Email convite = mailpit.esperar(email, 1).getFirst();
        assertThat(convite.assunto()).isEqualTo("Convite para o FiscalHub");
        assertThat(convite.texto()).contains("Correio " + sufixo, "72 horas", "https://fiscalhub.teste/convite#");
        assertThat(convite.html()).contains("Definir minha senha");

        // antes do aceite não há senha que funcione
        login(email, SENHA_NOVA).andExpect(status().isUnauthorized());
        aceitar(convite.token(), SENHA_NOVA).andExpect(status().isNoContent());
        login(email, SENHA_NOVA).andExpect(status().isOk());
        assertThat(usuarios.findByEmail(email).orElseThrow().getConvitePendente()).isFalse();
        aceitar(convite.token(), "outra-frase-bem-forte").andExpect(status().isBadRequest()); // uso único

        Usuario caio = usuarios.findByEmail(email).orElseThrow();
        List<Map<String, Object>> envios = jdbc.queryForList(
                "SELECT detalhes::text AS d FROM auditoria_evento WHERE acao = 'EMAIL_ENVIAR' AND recurso_id = ?", caio.getId());
        assertThat(envios).singleElement().satisfies(e -> assertThat((String) e.get("d"))
                .contains("\"modelo\": \"CONVITE\"", "\"resultado\": \"SUCESSO\"").doesNotContain(convite.token(), "http"));
        assertThat(jdbc.queryForList("SELECT acao FROM auditoria_evento WHERE recurso_id = ? AND acao LIKE 'CONVITE%'", String.class, caio.getId()))
                .containsExactly("CONVITE_ACEITAR");
    }

    @Test
    void conviteVencidoOuSubstituidoNaoValeEReenvioSoAntesDoAceite() throws Exception {
        String email = "atrasado-" + sufixo + "@test.com";
        mvc.perform(post("/api/usuarios").with(dados.sessaoReautenticada(admin)).contentType(APPLICATION_JSON)
                .content("{\"nome\":\"Lia\",\"email\":\"" + email + "\",\"papel\":\"ASSISTENTE\"}")).andExpect(status().isCreated());
        String primeiro = mailpit.esperar(email, 1).getFirst().token();
        long id = usuarios.findByEmail(email).orElseThrow().getId();

        mvc.perform(post("/api/usuarios/{id}/convite", id).with(dados.sessao(admin))).andExpect(status().isNoContent());
        String segundo = mailpit.esperar(email, 2).getFirst().token();
        aceitar(primeiro, SENHA_NOVA).andExpect(status().isBadRequest()); // o reenvio invalidou o anterior

        jdbc.update("UPDATE token_acesso SET expira_em = now() - interval '1 second' WHERE usuario_id = ?", id);
        aceitar(segundo, SENHA_NOVA).andExpect(status().isBadRequest()) // 72 h passaram
                .andExpect(jsonPath("$.detail").value("Link inválido ou expirado. Peça um novo."));

        mvc.perform(post("/api/usuarios/{id}/convite", id).with(dados.sessao(admin))).andExpect(status().isNoContent());
        aceitar(mailpit.esperar(email, 3).getFirst().token(), SENHA_NOVA).andExpect(status().isNoContent());
        mvc.perform(post("/api/usuarios/{id}/convite", id).with(dados.sessao(admin))).andExpect(status().isBadRequest());
    }

    @Test
    void recuperacaoRespondeIgualExistaOuNaoOEmailEOLinkValeUmaVezPor30Minutos() throws Exception {
        Usuario consultor = dados.usuario(escritorio, "esquecido-" + sufixo + "@test.com", Papel.CONSULTOR);
        RequestPostProcessor sessaoAntiga = dados.sessao(consultor);

        String existente = recuperar(consultor.getEmail(), "10.1.0.1").andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String inexistente = recuperar("ninguem-" + sufixo + "@test.com", "10.1.0.1").andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(existente).isEqualTo(inexistente).contains(AuthController.RESPOSTA_RECUPERACAO);

        Mailpit.Email link = mailpit.esperar(consultor.getEmail(), 1).getFirst();
        assertThat(link.texto()).contains("30 minutos", "https://fiscalhub.teste/redefinir-senha#");
        assertThat(jdbc.queryForObject("SELECT extract(epoch FROM expira_em - criado_em) FROM token_acesso WHERE usuario_id = ? AND tipo = 'RECUPERACAO'",
                Double.class, consultor.getId())).isEqualTo(1800.0);
        assertThat(mailpit.para("ninguem-" + sufixo + "@test.com")).isEmpty();

        // limite por conta: outro pedido em menos de 5 minutos não manda outro e-mail (e a resposta é a mesma)
        recuperar(consultor.getEmail(), "10.1.0.2").andExpect(status().isAccepted());
        Mailpit.dormir(1000);
        assertThat(mailpit.para(consultor.getEmail())).hasSize(1);

        redefinir(link.token(), SENHA_NOVA, null).andExpect(status().isNoContent());
        mvc.perform(get("/api/usuarios/me").with(sessaoAntiga)).andExpect(status().isUnauthorized()); // sessões antigas caem
        login(consultor.getEmail(), SENHA_NOVA).andExpect(status().isOk());
        redefinir(link.token(), "mais-uma-frase-forte", null).andExpect(status().isBadRequest()); // uso único

        assertThat(mailpit.esperar(consultor.getEmail(), 2).getFirst().assunto()).isEqualTo("Sua senha foi alterada");
        assertThat(jdbc.queryForList("SELECT acao FROM auditoria_evento WHERE usuario_id = ? AND acao LIKE 'SENHA%' ORDER BY id", String.class, consultor.getId()))
                .containsExactly("SENHA_RECUPERACAO_SOLICITAR", "SENHA_REDEFINIR");
    }

    @Test
    void recuperacaoNaoDispensaOSegundoFator() throws Exception {
        recuperar(admin.getEmail(), "10.2.0.1").andExpect(status().isAccepted());
        String token = mailpit.esperar(admin.getEmail(), 1).getFirst().token();

        redefinir(token, SENHA_NOVA, null).andExpect(status().isBadRequest());
        redefinir(token, SENHA_NOVA, "000000").andExpect(status().isBadRequest());
        login(admin.getEmail(), SENHA_NOVA).andExpect(status().isUnauthorized()); // nada mudou e o link continua valendo

        String codigo = TotpTest.codigo(DadosTeste.SEGREDO_TOTP, System.currentTimeMillis() / 30_000);
        redefinir(token, SENHA_NOVA, codigo).andExpect(status().isNoContent());
        login(admin.getEmail(), SENHA_NOVA).andExpect(status().isOk()).andExpect(jsonPath("$.desafio").isNotEmpty());
    }

    @Test
    void limitePorIpNaRecuperacao() throws Exception {
        for (int i = 0; i < 5; i++) {
            recuperar("x" + i + "-" + sufixo + "@test.com", "10.3.0.1").andExpect(status().isAccepted());
        }
        recuperar("x-" + sufixo + "@test.com", "10.3.0.1").andExpect(status().isTooManyRequests());
    }

    @Test
    void avisoDePapelETrocaDeSenhaEFalhaDoSmtpFicaNaAuditoriaDepoisDe3Tentativas() throws Exception {
        Usuario consultor = dados.usuario(escritorio, "avisado-" + sufixo + "@test.com", Papel.CONSULTOR);
        mvc.perform(patch("/api/usuarios/{id}/papel", consultor.getId()).with(dados.sessaoReautenticada(admin))
                .contentType(APPLICATION_JSON).content("{\"papel\":\"ASSISTENTE\"}")).andExpect(status().isOk());
        Mailpit.Email aviso = mailpit.esperar(consultor.getEmail(), 1).getFirst();
        assertThat(aviso.assunto()).isEqualTo("Seu papel no escritório mudou");
        assertThat(aviso.texto()).contains("Correio " + sufixo).doesNotContain("#");

        mailpit.recusarTudo(true);
        try {
            mvc.perform(post("/api/usuarios/{id}/convite", consultor.getId()).with(dados.sessao(admin))).andExpect(status().isBadRequest());
            mvc.perform(post("/api/usuarios").with(dados.sessaoReautenticada(admin)).contentType(APPLICATION_JSON)
                    .content("{\"nome\":\"Rui\",\"email\":\"recusado-" + sufixo + "@test.com\",\"papel\":\"CONSULTOR\"}")).andExpect(status().isCreated());
            long rui = usuarios.findByEmail("recusado-" + sufixo + "@test.com").orElseThrow().getId();
            String detalhes = null;
            for (int i = 0; i < 50 && detalhes == null; i++) {
                Mailpit.dormir(200);
                detalhes = jdbc.queryForList("SELECT detalhes::text FROM auditoria_evento WHERE acao = 'EMAIL_ENVIAR' AND recurso_id = ?",
                        String.class, rui).stream().findFirst().orElse(null);
            }
            assertThat(detalhes).contains("\"resultado\": \"FALHA\"", "\"tentativas\": 3");
        } finally {
            mailpit.recusarTudo(false);
        }
    }

    private ResultActions login(String email, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").with(DadosTeste.CSRF).contentType(APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"));
    }

    private ResultActions aceitar(String token, String senha) throws Exception {
        return mvc.perform(post("/api/auth/convite").with(DadosTeste.CSRF).contentType(APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"senha\":\"" + senha + "\"}"));
    }

    private ResultActions recuperar(String email, String ip) throws Exception {
        return mvc.perform(post("/api/auth/senha/recuperar").with(DadosTeste.CSRF).with(r -> {
            r.setRemoteAddr(ip);
            return r;
        }).contentType(APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions redefinir(String token, String senha, String codigo) throws Exception {
        return mvc.perform(post("/api/auth/senha/redefinir").with(DadosTeste.CSRF).contentType(APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"novaSenha\":\"" + senha + "\""
                        + (codigo == null ? "" : ",\"codigo\":\"" + codigo + "\"") + "}"));
    }
}
