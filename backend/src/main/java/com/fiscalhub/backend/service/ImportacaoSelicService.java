package com.fiscalhub.backend.service;

import com.fiscalhub.backend.entity.IndiceMonetario;
import com.fiscalhub.backend.entity.enums.TipoIndice;
import com.fiscalhub.backend.repository.IndiceMonetarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Importa a SELIC mensal oficial do Banco Central (API SGS, série 4390 — "Taxa de juros - Selic acumulada
 * no mês", em % a.m.; resposta [{"data":"dd/MM/aaaa","valor":"0.97"}]).
 * Só meses fechados: a série traz o mês corrente parcial. Upsert: insere o que falta e corrige o que diverge.
 */
@Service
public class ImportacaoSelicService {

    static final String ORIGEM = "BCB_SGS_4390";
    private static final DateTimeFormatter DATA_BCB = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    record PontoSerie(String data, String valor) {
    }

    private final RestClient http;
    private final IndiceMonetarioRepository repository;
    private final YearMonth inicio;

    public ImportacaoSelicService(RestClient.Builder builder,
                                  @Value("${fiscalhub.selic.url:https://api.bcb.gov.br/dados/serie/bcdata.sgs.4390/dados}") String url,
                                  @Value("${fiscalhub.selic.inicio:2000-01}") String inicio,
                                  IndiceMonetarioRepository repository) {
        this.http = builder.baseUrl(url).build();
        this.inicio = YearMonth.parse(inicio);
        this.repository = repository;
    }

    /** Importa de fiscalhub.selic.inicio até o último mês fechado. Devolve quantos meses foram gravados/corrigidos. */
    @Transactional
    public int importarAte(YearMonth ultimoMesFechado) {
        List<PontoSerie> serie = http.get()
                .uri(u -> u.queryParam("formato", "json")
                        .queryParam("dataInicial", inicio.atDay(1).format(DATA_BCB))
                        .queryParam("dataFinal", ultimoMesFechado.atEndOfMonth().format(DATA_BCB))
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        LocalDateTime agora = LocalDateTime.now();
        int gravados = 0;
        for (PontoSerie ponto : serie == null ? List.<PontoSerie>of() : serie) {
            YearMonth mes = YearMonth.from(LocalDate.parse(ponto.data(), DATA_BCB));
            if (mes.isAfter(ultimoMesFechado)) {
                continue;
            }
            BigDecimal valor = new BigDecimal(ponto.valor());
            IndiceMonetario indice = repository.findByTipoAndAnoAndMes(TipoIndice.SELIC, mes.getYear(), mes.getMonthValue())
                    .orElseGet(IndiceMonetario::new);
            if (ORIGEM.equals(indice.getOrigem()) && indice.getValor().compareTo(valor) == 0) {
                continue;
            }
            indice.setTipo(TipoIndice.SELIC);
            indice.setAno(mes.getYear());
            indice.setMes(mes.getMonthValue());
            indice.setValor(valor);
            indice.setOrigem(ORIGEM);
            indice.setDataColeta(agora);
            repository.save(indice);
            gravados++;
        }
        return gravados;
    }
}
