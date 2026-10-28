package com.fiscalhub.backend.capacidade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.exception.BusinessRuleException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Regras das metodologias como dado (tabela regra_capacidade_pagamento): a versão vigente na data do cálculo. Faixas,
 * índices e prazos vêm daqui, nunca de constante no código; cada resultado cita a norma, o artigo e a vigência usados.
 */
@Component
public class RegrasCapacidade {

    public record Regra(String chave, String orgao, String norma, String artigo, LocalDate vigenciaInicio, LocalDate vigenciaFim,
                        JsonNode parametros, String fonteUrl, String fonteSha256, LocalDate fonteData, String observacao) {

        /** "Resolução Conjunta PGE/SEFA 01/2025, art. 8º (redação da RC 15/2026), vigente desde 24/08/2026". */
        public String citacao() {
            return norma + ", " + artigo + " (vigência desde " + vigenciaInicio.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + (vigenciaFim == null ? "" : " até " + vigenciaFim.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))) + ")";
        }

        /** Índice de uma faixa [{"ate": limite inclusivo ou null, "indice": x}]. */
        public BigDecimal faixa(String campo, BigDecimal valor) {
            for (JsonNode f : parametros.get(campo)) {
                if (f.get("ate") == null || f.get("ate").isNull() || valor.compareTo(f.get("ate").decimalValue()) <= 0) {
                    return f.get("indice").decimalValue();
                }
            }
            throw new IllegalStateException("Faixa sem limite aberto em " + chave + "." + campo);
        }

        /** Rótulo da faixa em que o valor caiu ("até 0.10", "acima de 0.50"). */
        public String rotuloFaixa(String campo, BigDecimal valor) {
            BigDecimal anterior = null;
            for (JsonNode f : parametros.get(campo)) {
                if (f.get("ate") == null || f.get("ate").isNull()) {
                    return anterior == null ? "qualquer valor" : "acima de " + anterior.stripTrailingZeros().toPlainString();
                }
                if (valor.compareTo(f.get("ate").decimalValue()) <= 0) {
                    return (anterior == null ? "até " : "acima de " + anterior.stripTrailingZeros().toPlainString() + " até ")
                            + f.get("ate").decimalValue().stripTrailingZeros().toPlainString();
                }
                anterior = f.get("ate").decimalValue();
            }
            return "";
        }
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public RegrasCapacidade(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** A regra vigente em data (a de início mais recente que já valia e ainda não acabou). */
    public Regra vigente(String chave, LocalDate data) {
        return jdbc.query("""
                SELECT chave, orgao, norma, artigo, vigencia_inicio, vigencia_fim, parametros::text, fonte_url, fonte_sha256,
                       fonte_data, observacao FROM regra_capacidade_pagamento
                WHERE chave = ? AND vigencia_inicio <= ? AND (vigencia_fim IS NULL OR vigencia_fim >= ?)
                ORDER BY vigencia_inicio DESC LIMIT 1""", (rs, i) -> new Regra(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getDate(5).toLocalDate(), rs.getDate(6) == null ? null : rs.getDate(6).toLocalDate(),
                ler(rs.getString(7)), rs.getString(8), rs.getString(9), rs.getDate(10).toLocalDate(), rs.getString(11)),
                chave, Date.valueOf(data), Date.valueOf(data)).stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException("Sem regra " + chave + " vigente em " + data + "."));
    }

    /** Todas as versões (tela de regras e README). */
    public List<Regra> todas() {
        return jdbc.query("""
                SELECT chave, orgao, norma, artigo, vigencia_inicio, vigencia_fim, parametros::text, fonte_url, fonte_sha256,
                       fonte_data, observacao FROM regra_capacidade_pagamento ORDER BY orgao, chave, vigencia_inicio""",
                (rs, i) -> new Regra(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getDate(5).toLocalDate(),
                        rs.getDate(6) == null ? null : rs.getDate(6).toLocalDate(), ler(rs.getString(7)), rs.getString(8),
                        rs.getString(9), rs.getDate(10).toLocalDate(), rs.getString(11)));
    }

    private JsonNode ler(String texto) {
        try {
            return json.readTree(texto);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
