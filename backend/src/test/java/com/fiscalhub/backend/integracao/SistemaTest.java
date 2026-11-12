package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Escritorio;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.operacao.AlertasSistema;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

import static com.fiscalhub.backend.integracao.Espera.aguardar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tarefa 6 da Fase 7: layout sem leitura (orientação e métrica) e os alertas do ADMIN do sistema. */
@TesteIntegracao
class SistemaTest {

    @Autowired MockMvc mvc;
    @Autowired DadosTeste dados;
    @Autowired AlertasSistema alertas;
    @Autowired DocumentoFiscalRepository documentos;

    @Test
    void pdfComLayoutDesconhecidoOrientaOEnvioAnonimizadoEEntraNaMetricaSemDigitos() throws Exception {
        Escritorio e = dados.escritorio("Layouts " + System.nanoTime());
        RequestPostProcessor admin = dados.sessao(dados.usuario(e, "layout-" + System.nanoTime() + "@test.com", Papel.ADMIN));
        var mantenedor = dados.usuario(e, "sistema-" + System.nanoTime() + "@test.com", Papel.CONSULTOR);
        RequestPostProcessor sistema = dados.sessao(mantenedor);
        org.springframework.test.util.ReflectionTestUtils.setField(alertas, "admins", java.util.Set.of(mantenedor.getId()));

        String resposta = mvc.perform(multipart("/api/documentos").file(new MockMultipartFile("arquivo", "extrato.pdf", "application/pdf",
                pdf("EXTRATO DE DEBITOS DO MUNICIPIO 11.222.333/0001-81 EM 10/11/2026")))
                .with(admin)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(resposta, "$.id")).longValue();
        aguardar(() -> documentos.findById(id).orElseThrow().getStatus().finalizado());

        mvc.perform(get("/api/documentos/{id}/status", id).with(admin))
                .andExpect(jsonPath("$.status").value("LAYOUT_NAO_RECONHECIDO"))
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("versão anonimizada")));
        mvc.perform(get("/api/sistema/layouts-nao-reconhecidos").with(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/sistema/layouts-nao-reconhecidos").with(sistema)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].assinatura", hasItem("EXTRATO DE DEBITOS DO MUNICIPIO #.#.#/#-# EM #/#/#")));
    }

    @Test
    void editalComAdesaoTerminandoEmAte30DiasViraAlerta() {
        assertThat(alertas.alertas(LocalDate.of(2027, 1, 10))).anySatisfy(a -> {
            assertThat(a.tipo()).isEqualTo("EDITAL");
            assertThat(a.prazo()).isEqualTo(LocalDate.of(2027, 1, 29));
            assertThat(a.mensagem()).contains("Edital PGDAU 6/2026", "19 dia(s)");
        });
        assertThat(alertas.alertas(LocalDate.of(2026, 11, 12))).noneMatch(a -> a.tipo().equals("EDITAL")); // a 78 dias
    }

    private static byte[] pdf(String texto) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage();
            doc.addPage(pagina);
            try (PDPageContentStream c = new PDPageContentStream(doc, pagina)) {
                c.beginText();
                c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                c.newLineAtOffset(50, 700);
                c.showText(texto);
                c.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
