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

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ImportacaoSelicServiceTest {

    private static final String URL = "https://api.bcb.gov.br/dados/serie/bcdata.sgs.4390/dados";

    private MockRestServiceServer bcb;
    private IndiceMonetarioRepository repository;
    private ImportacaoSelicService importacao;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        bcb = MockRestServiceServer.bindTo(builder).build();
        repository = mock(IndiceMonetarioRepository.class);
        when(repository.findByTipoAndAnoAndMes(any(), anyInt(), anyInt())).thenReturn(Optional.empty());
        importacao = new ImportacaoSelicService(builder, URL, "2024-01", repository);
    }

    @Test
    void gravaMesesFechadosComOrigemEIgnoraOMesCorrenteParcial() {
        bcb.expect(requestTo(URL + "?formato=json&dataInicial=01/01/2024&dataFinal=29/02/2024"))
                .andRespond(withSuccess("""
                        [{"data":"01/01/2024","valor":"0.97"},{"data":"01/02/2024","valor":"0.80"},
                         {"data":"01/03/2024","valor":"0.41"}]""", MediaType.APPLICATION_JSON));

        int gravados = importacao.importarAte(YearMonth.of(2024, 2));

        bcb.verify();
        assertThat(gravados).isEqualTo(2);
        ArgumentCaptor<IndiceMonetario> salvos = ArgumentCaptor.forClass(IndiceMonetario.class);
        verify(repository, times(2)).save(salvos.capture());
        assertThat(salvos.getAllValues()).extracting(IndiceMonetario::getYearMonth)
                .containsExactly(YearMonth.of(2024, 1), YearMonth.of(2024, 2));
        assertThat(salvos.getAllValues()).allSatisfy(i -> {
            assertThat(i.getTipo()).isEqualTo(TipoIndice.SELIC);
            assertThat(i.getOrigem()).isEqualTo("BCB_SGS_4390");
            assertThat(i.getDataColeta()).isNotNull();
        });
        assertThat(salvos.getAllValues().get(0).getValor()).isEqualByComparingTo("0.97");
    }

    @Test
    void naoDuplicaECorrigeValorAproximadoAnterior() {
        IndiceMonetario jaOficial = indice(1, "0.97", "BCB_SGS_4390");
        IndiceMonetario aproximado = indice(2, "0.76", "LEGADO");
        when(repository.findByTipoAndAnoAndMes(TipoIndice.SELIC, 2024, 1)).thenReturn(Optional.of(jaOficial));
        when(repository.findByTipoAndAnoAndMes(TipoIndice.SELIC, 2024, 2)).thenReturn(Optional.of(aproximado));
        bcb.expect(requestTo(URL + "?formato=json&dataInicial=01/01/2024&dataFinal=29/02/2024"))
                .andRespond(withSuccess("[{\"data\":\"01/01/2024\",\"valor\":\"0.97\"},{\"data\":\"01/02/2024\",\"valor\":\"0.80\"}]",
                        MediaType.APPLICATION_JSON));

        int gravados = importacao.importarAte(YearMonth.of(2024, 2));

        assertThat(gravados).isEqualTo(1);
        verify(repository, never()).save(jaOficial);
        verify(repository).save(aproximado);
        assertThat(aproximado.getValor()).isEqualByComparingTo("0.80");
        assertThat(aproximado.getOrigem()).isEqualTo("BCB_SGS_4390");
        verify(repository, never()).save(argThat(i -> i.getId() == null));
    }

    private static IndiceMonetario indice(int mes, String valor, String origem) {
        IndiceMonetario i = new IndiceMonetario();
        i.setId((long) mes);
        i.setTipo(TipoIndice.SELIC);
        i.setAno(2024);
        i.setMes(mes);
        i.setValor(new BigDecimal(valor));
        i.setOrigem(origem);
        return i;
    }
}
