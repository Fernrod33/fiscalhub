package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.Escritorio;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Identidade do escritório (papéis, contraste, logotipo seguro) e relatórios: isolamento entre escritórios e papéis. */
@TesteIntegracao
class RelatoriosEIdentidadeTest {

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired JdbcTemplate jdbc;

    @Test
    void soAdminAlteraAIdentidadeEACorPrecisaDeContrasteAA() throws Exception {
        Escritorio e = dados.escritorio("Identidade " + UUID.randomUUID());
        RequestPostProcessor admin = dados.sessao(dados.usuario(e, "adm-" + UUID.randomUUID() + "@t.com", Papel.ADMIN));
        RequestPostProcessor consultor = dados.sessao(dados.usuario(e, "con-" + UUID.randomUUID() + "@t.com", Papel.CONSULTOR));
        String corpo = """
                {"nomeExibicao":"Silva & Souza Advogados","corPrincipal":"#0B4F6C","rodape":"Rua A, 1 — OAB/PR 123",
                 "responsavelNome":"Ana Silva","responsavelRegistro":"OAB/PR 45.678"}""";
        mvc.perform(put("/api/escritorio/identidade").with(consultor).contentType(APPLICATION_JSON).content(corpo)).andExpect(status().isForbidden());
        mvc.perform(put("/api/escritorio/identidade").with(admin).contentType(APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.corPrincipal").value("#0b4f6c"));
        mvc.perform(get("/api/escritorio/identidade").with(consultor)).andExpect(jsonPath("$.nomeExibicao").value("Silva & Souza Advogados"));
        // amarelo claro: 1,6:1 sobre o branco
        mvc.perform(put("/api/escritorio/identidade").with(admin).contentType(APPLICATION_JSON).content("{\"corPrincipal\":\"#ffd84d\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("4,5:1")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_evento WHERE acao = 'IDENTIDADE_ALTERAR' AND escritorio_id = ?",
                Long.class, e.getId())).isEqualTo(1);
    }

    @Test
    void logotipoSoPngOuJpegAte1MbRedimensionadoNoServidor() throws Exception {
        Escritorio e = dados.escritorio("Logo " + UUID.randomUUID());
        RequestPostProcessor admin = dados.sessao(dados.usuario(e, "adm-" + UUID.randomUUID() + "@t.com", Papel.ADMIN));
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
        mvc.perform(multipart("/api/escritorio/identidade/logo").file(new MockMultipartFile("arquivo", "logo.png", "image/png", svg)).with(admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("PNG ou JPEG")));
        byte[] grande = new byte[1024 * 1024 + 10];
        System.arraycopy(png(10, 10), 0, grande, 0, 8);
        mvc.perform(multipart("/api/escritorio/identidade/logo").file(new MockMultipartFile("arquivo", "logo.png", "image/png", grande)).with(admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("1 MB")));
        mvc.perform(multipart("/api/escritorio/identidade/logo").file(new MockMultipartFile("arquivo", "logo.jpg", "image/jpeg", jpeg(1800, 600))).with(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.temLogo").value(true));
        byte[] gravado = mvc.perform(get("/api/escritorio/identidade/logo").with(admin)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andReturn().getResponse().getContentAsByteArray();
        BufferedImage lido = ImageIO.read(new ByteArrayInputStream(gravado));
        assertThat(lido.getWidth()).isEqualTo(600); // 1800 × 600 -> cabe em 600 × 200
        assertThat(lido.getHeight()).isEqualTo(200);
    }

    @Test
    void relatorioIsoladoPorEscritorioEEmitirExigeConsultorOuAdmin() throws Exception {
        Escritorio e = dados.escritorio("Relatórios " + UUID.randomUUID());
        Usuario consultor = dados.usuario(e, "con-" + UUID.randomUUID() + "@t.com", Papel.CONSULTOR);
        RequestPostProcessor assistente = dados.sessao(dados.usuario(e, "ass-" + UUID.randomUUID() + "@t.com", Papel.ASSISTENTE));
        RequestPostProcessor outro = dados.sessao(dados.usuario(dados.escritorio("Outro " + UUID.randomUUID()),
                "out-" + UUID.randomUUID() + "@t.com", Papel.ADMIN));
        Cliente cliente = dados.cliente(consultor, "11444777000161");
        String pedido = "{\"tipo\":\"SITUACAO_FISCAL\"}";
        String base = "/api/clientes/" + cliente.getId() + "/relatorios";

        mvc.perform(post(base).with(assistente).contentType(APPLICATION_JSON).content(pedido)).andExpect(status().isForbidden());
        mvc.perform(post(base + "/previa").with(assistente).contentType(APPLICATION_JSON).content(pedido))
                .andExpect(status().isOk()).andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.startsWith("default-src 'none'")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PRÉVIA")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM relatorio_emitido WHERE cliente_id = ?", Long.class, cliente.getId())).isZero();

        String emitido = mvc.perform(post(base).with(dados.sessao(consultor)).contentType(APPLICATION_JSON).content(pedido))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.numero").value(1)).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(emitido, "$.id")).longValue();
        mvc.perform(get(base).with(assistente)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get(base + "/" + id + "/pdf").with(assistente)).andExpect(status().isOk());
        // outro escritório: 404 em tudo (nem a existência vaza)
        mvc.perform(get(base).with(outro)).andExpect(status().isNotFound());
        mvc.perform(get(base + "/" + id).with(outro)).andExpect(status().isNotFound());
        mvc.perform(get(base + "/" + id + "/pdf").with(outro)).andExpect(status().isNotFound());
        mvc.perform(post(base + "/previa").with(outro).contentType(APPLICATION_JSON).content(pedido)).andExpect(status().isNotFound());
        mvc.perform(post(base).with(outro).contentType(APPLICATION_JSON).content(pedido)).andExpect(status().isNotFound());
    }

    private static byte[] png(int w, int h) throws Exception {
        ByteArrayOutputStream s = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", s);
        return s.toByteArray();
    }

    private static byte[] jpeg(int w, int h) throws Exception {
        ByteArrayOutputStream s = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "jpg", s);
        return s.toByteArray();
    }
}
