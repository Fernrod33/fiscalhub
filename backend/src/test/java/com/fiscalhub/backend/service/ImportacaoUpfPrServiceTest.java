package com.fiscalhub.backend.service;

import com.fiscalhub.backend.entity.IndiceMonetario;
import com.fiscalhub.backend.entity.enums.TipoIndice;
import com.fiscalhub.backend.repository.IndiceMonetarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Resposta real da página da SEFA-PR (UPF/PR de jan/2025 a nov/2026), gravada em 11/11/2026 como fixture. */
class ImportacaoUpfPrServiceTest {

    private static final String URL = "https://www.arinternet.pr.gov.br/outros/_c_indice2.asp";

    private MockRestServiceServer sefa;
    private IndiceMonetarioRepository repository;
    private ImportacaoUpfPrService importacao;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        sefa = MockRestServiceServer.bindTo(builder).build();
        repository = mock(IndiceMonetarioRepository.class);
        when(repository.findByTipoAndAnoAndMes(any(), anyInt(), anyInt())).thenReturn(Optional.empty());
        importacao = new ImportacaoUpfPrService(builder, URL, "2025-05", repository);
    }

    @Test
    void gravaOsMesesDoIntervaloComAFonteDaResolucao360() throws IOException {
        responder("202505", "202610");

        int gravados = importacao.importarAte(YearMonth.of(2026, 10));

        sefa.verify();
        assertThat(gravados).isEqualTo(18); // maio/2025 a outubro/2026; jan–abr/2025 e nov/2026 da página ficam fora
        ArgumentCaptor<IndiceMonetario> salvos = ArgumentCaptor.forClass(IndiceMonetario.class);
        verify(repository, times(18)).save(salvos.capture());
        assertThat(salvos.getAllValues()).allSatisfy(i -> {
            assertThat(i.getTipo()).isEqualTo(TipoIndice.UPF_PR);
            assertThat(i.getOrigem()).isEqualTo("SEFA_PR_INDICADORES");
            assertThat(i.getFonte()).startsWith("Resolução SEFA 360/2025, art. 1º, I").contains("arinternet.pr.gov.br");
            assertThat(i.getDataColeta()).isNotNull();
        });
        assertThat(salvos.getAllValues().getFirst().getYearMonth()).isEqualTo(YearMonth.of(2025, 5));
        assertThat(salvos.getAllValues().getFirst().getValor()).isEqualByComparingTo("144.51");
        assertThat(salvos.getAllValues().getLast().getYearMonth()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(salvos.getAllValues().getLast().getValor()).isEqualByComparingTo("152.25");
    }

    @Test
    void lerReconheceMesComAcentoEOsDoisAnosDaTabela() throws IOException {
        var valores = ImportacaoUpfPrService.ler(fixture());
        assertThat(valores).hasSize(23).containsEntry(YearMonth.of(2025, 3), new BigDecimal("141.85"))
                .containsEntry(YearMonth.of(2026, 3), new BigDecimal("148.15"));
    }

    @Test
    void idempotenteNaoRegravaOMesIgual() throws IOException {
        IndiceMonetario maio = new IndiceMonetario();
        maio.setTipo(TipoIndice.UPF_PR);
        maio.setValor(new BigDecimal("144.51"));
        maio.setOrigem("SEFA_PR_INDICADORES");
        when(repository.findByTipoAndAnoAndMes(TipoIndice.UPF_PR, 2025, 5)).thenReturn(Optional.of(maio));
        responder("202505", "202505");

        assertThat(importacao.importarAte(YearMonth.of(2025, 5))).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void paginaSemTabelaEhErroENaoGravaNada() {
        sefa.expect(requestTo(URL + "?eUser=&eDetalhe=UPF/PR&eMesini=202505&eMesfim=202610"))
                .andRespond(withSuccess("<html>Serviço indisponível</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> importacao.importarAte(YearMonth.of(2026, 10))).hasMessageContaining("layout mudou");
        verify(repository, never()).save(any());
    }

    private void responder(String de, String ate) throws IOException {
        sefa.expect(requestTo(URL + "?eUser=&eDetalhe=UPF/PR&eMesini=" + de + "&eMesfim=" + ate))
                .andRespond(withSuccess(fixture().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), MediaType.TEXT_HTML));
    }

    private static String fixture() throws IOException {
        try (var in = ImportacaoUpfPrServiceTest.class.getResourceAsStream("/indicadores/sefa-pr-upf-2025-01-a-2026-12.html")) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
        }
    }
}
