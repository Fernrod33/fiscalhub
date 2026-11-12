package com.fiscalhub.backend.service;

import com.fiscalhub.backend.entity.enums.TipoIndice;
import com.fiscalhub.backend.repository.IndiceMonetarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Importa a UPF/PR da página de Indicadores Econômicos da SEFA-PR, onde ela é publicada mensalmente desde 1º/05/2025
 * (Resolução SEFA 360/2025, art. 1º, I). A página de consulta abre, num iframe, um ASP com GET simples
 * ({@code _c_indice2.asp?eDetalhe=UPF/PR&eMesini=AAAAMM&eMesfim=AAAAMM}) que devolve uma tabela HTML em ISO-8859-1
 * com "Mês/Ano" e "Índice (R$)". Upsert idempotente como a SELIC, com a fonte gravada por mês.
 */
@Service
public class ImportacaoUpfPrService {

    static final String ORIGEM = "SEFA_PR_INDICADORES";
    static final String FONTE = "Resolução SEFA 360/2025, art. 1º, I; Indicadores Econômicos da SEFA-PR: "
            + "https://www.arinternet.pr.gov.br/outros/_c_indice2.asp?eDetalhe=UPF/PR";
    private static final DateTimeFormatter AAAAMM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final List<String> MESES = List.of("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho",
            "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro");
    private static final Pattern LINHA = Pattern.compile("(" + String.join("|", MESES) + ")/(\\d{4})\\s+([\\d.]+,\\d+)");

    private final RestClient http;
    private final IndiceMonetarioRepository repository;
    private final YearMonth inicio;

    public ImportacaoUpfPrService(RestClient.Builder builder,
                                  @Value("${fiscalhub.upf.url:https://www.arinternet.pr.gov.br/outros/_c_indice2.asp}") String url,
                                  @Value("${fiscalhub.upf.inicio:2025-05}") String inicio,
                                  IndiceMonetarioRepository repository) {
        this.http = builder.baseUrl(url).build();
        this.inicio = YearMonth.parse(inicio);
        this.repository = repository;
    }

    /** Importa de fiscalhub.upf.inicio até {@code ate}. Devolve quantos meses foram gravados ou corrigidos. */
    @Transactional
    public int importarAte(YearMonth ate) {
        byte[] corpo = http.get()
                .uri(u -> u.queryParam("eUser", "").queryParam("eDetalhe", "UPF/PR")
                        .queryParam("eMesini", inicio.format(AAAAMM)).queryParam("eMesfim", ate.format(AAAAMM)).build())
                .retrieve()
                .body(byte[].class);
        Map<YearMonth, BigDecimal> valores = ler(corpo == null ? "" : new String(corpo, StandardCharsets.ISO_8859_1));
        if (valores.isEmpty()) {
            throw new IllegalStateException("página de Indicadores Econômicos da SEFA-PR sem nenhuma UPF/PR (layout mudou?)");
        }
        LocalDateTime agora = LocalDateTime.now();
        int gravados = 0;
        for (var e : valores.entrySet()) {
            if (!e.getKey().isBefore(inicio) && !e.getKey().isAfter(ate)
                    && ImportacaoSelicService.gravar(repository, TipoIndice.UPF_PR, e.getKey(), e.getValue(), ORIGEM, FONTE, agora)) {
                gravados++;
            }
        }
        return gravados;
    }

    /** "Janeiro/2026 ... 147,17" de cada célula da tabela, sem as tags. */
    static Map<YearMonth, BigDecimal> ler(String html) {
        String texto = html.replaceAll("<[^>]*>", " ").replace("&nbsp;", " ");
        Map<YearMonth, BigDecimal> valores = new TreeMap<>();
        Matcher m = LINHA.matcher(texto);
        while (m.find()) {
            YearMonth mes = YearMonth.of(Integer.parseInt(m.group(2)), MESES.indexOf(m.group(1)) + 1);
            valores.put(mes, new BigDecimal(m.group(3).replace(".", "").replace(',', '.')));
        }
        return valores;
    }
}
