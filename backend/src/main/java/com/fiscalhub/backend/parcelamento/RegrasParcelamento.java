package com.fiscalhub.backend.parcelamento;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Regras de acréscimos, parcelamento e transação como dado: {@code resources/parcelamento/regras.json} (vigência, artigos
 * e fonte de cada versão) e a UPF/PR oficial em {@code resources/parcelamento/upf-pr.csv}. Versionadas no git: a
 * simulação grava a versão do arquivo e a vigência da regra usada. Nada de percentual, prazo ou limite no código.
 */
@Component
public class RegrasParcelamento {

    public record Fonte(String titulo, String url, LocalDate data, String sha256, boolean secundaria, String observacao) {
    }

    /** multaAposDias/multaAposPrazo: PR (redução do RICMS art. 80); multaInscrito: multa cheia depois da inscrição. */
    public record RegraAcrescimo(String chave, List<OrgaoCredor> orgaos, LocalDate vigenciaInicio, LocalDate vigenciaFim,
                                 BigDecimal multaDiaria, BigDecimal multaMaxima, Integer multaAposDias, BigDecimal multaAposPrazo,
                                 BigDecimal multaInscrito, BigDecimal jurosMesPagamento, BigDecimal encargoLegal, String fundamento,
                                 List<String> fontes) {
    }

    /** Uma versão (vigência) de uma modalidade. Campo nulo = a norma não fixa (ou a modalidade não usa). */
    public record RegraParcelamento(String chave, String nome, OrgaoCredor orgao, Tipo tipo, LocalDate vigenciaInicio,
                                    LocalDate vigenciaFim, LocalDate adesaoInicio, LocalDate adesaoFim, String encerrada,
                                    List<OrgaoCredor> orgaosDebito, Integer prazoMaximo, Integer prazoMaximoSemDesconto,
                                    Integer prazoMaximoPrevidenciario, Integer prazoMaximoEfd, BigDecimal parcelaMinimaPJ,
                                    BigDecimal parcelaMinimaPF, BigDecimal parcelaMinimaUpf, BigDecimal valorMinimoUpf,
                                    BigDecimal entradaPercentual, Integer entradaParcelasMax, BigDecimal descontoAcrescimosMax,
                                    BigDecimal descontoTotalMax, List<String> classesComDesconto, LocalDate dataCorteInscricao,
                                    BigDecimal debitoMaximo, BigDecimal garantiaAcima, BigDecimal garantiaAcimaUpf,
                                    Integer garantiaPrazoAcima, Correcao correcao, PrimeiraParcela primeiraParcela,
                                    BigDecimal reparcelamentoPercentual, BigDecimal reparcelamentoPercentualHistorico,
                                    Integer reparcelamentoParcelasEntrada, Integer reparcelamentoAcimaDe,
                                    List<TipoTributo> vedaTributos, boolean vedaNegociado, boolean vedaGiaSt, String rescisao,
                                    String garantia, String fundamento, String nota, List<String> fontes) {

        public boolean vigente(LocalDate data) {
            return !data.isBefore(vigenciaInicio) && (vigenciaFim == null || !data.isAfter(vigenciaFim));
        }

        /** Identifica a versão usada (chave@início da vigência). */
        public String versao() {
            return chave + "@" + vigenciaInicio;
        }
    }

    public enum Tipo { PARCELAMENTO, TRANSACAO, PROGRAMA }

    /** SELIC acumulada do mês seguinte à consolidação ao anterior ao pagamento + 1% (Lei 10.522, art. 13); PR: soma da SELIC da 2ª em diante (RICMS art. 81 §3º I). */
    public enum Correcao { SELIC_ACUMULADA_MAIS_1, SELIC_SOMA_A_PARTIR_DA_2A }

    public enum PrimeiraParcela { MES_SEGUINTE, MES_DA_CONSOLIDACAO, DATA_BASE }

    public record Upf(YearMonth mes, BigDecimal valor, String norma, String url, String sha256) {
    }

    private record Arquivo(String versao, Map<String, Fonte> fontes, List<RegraAcrescimo> acrescimos,
                           List<RegraParcelamento> modalidades) {
    }

    private final Arquivo arquivo;
    private final Map<YearMonth, Upf> upf = new LinkedHashMap<>();

    public RegrasParcelamento() {
        try (InputStream in = recurso("parcelamento/regras.json")) {
            arquivo = new ObjectMapper().findAndRegisterModules().readValue(in, Arquivo.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(recurso("parcelamento/upf-pr.csv"), StandardCharsets.UTF_8))) {
            r.lines().filter(l -> !l.isBlank() && !l.startsWith("#") && !l.startsWith("mes;")).map(l -> l.split(";")).forEach(c -> {
                YearMonth mes = YearMonth.parse(c[0]);
                upf.put(mes, new Upf(mes, new BigDecimal(c[1]), c[2], c[3], c[4]));
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static InputStream recurso(String caminho) {
        return RegrasParcelamento.class.getClassLoader().getResourceAsStream(caminho);
    }

    public String versao() {
        return arquivo.versao();
    }

    public Fonte fonte(String chave) {
        return arquivo.fontes().get(chave);
    }

    public RegraAcrescimo acrescimo(OrgaoCredor orgao, LocalDate data) {
        return arquivo.acrescimos().stream()
                .filter(a -> a.orgaos().contains(orgao) && !data.isBefore(a.vigenciaInicio())
                        && (a.vigenciaFim() == null || !data.isAfter(a.vigenciaFim())))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("Sem regra de acréscimos de " + orgao + " vigente em " + data + "."));
    }

    /**
     * Uma versão por modalidade: a vigente na data; sem vigente, a última encerrada antes dela (para dizer por que não
     * cabe). Modalidade que só começa depois da data fica de fora.
     */
    public List<RegraParcelamento> modalidades(LocalDate data) {
        Map<String, RegraParcelamento> porChave = new LinkedHashMap<>();
        arquivo.modalidades().stream().filter(m -> !m.vigenciaInicio().isAfter(data))
                .sorted(Comparator.comparing(RegraParcelamento::vigenciaInicio))
                .forEach(m -> porChave.merge(m.chave(), m, (antiga, nova) -> antiga.vigente(data) ? antiga : nova));
        return List.copyOf(porChave.values());
    }

    public Optional<Upf> upf(YearMonth mes) {
        return Optional.ofNullable(upf.get(mes));
    }

    /** Último mês com UPF/PR oficial importada (versão dos índices). */
    public Optional<YearMonth> ultimaUpf() {
        return upf.keySet().stream().max(Comparator.naturalOrder());
    }
}
