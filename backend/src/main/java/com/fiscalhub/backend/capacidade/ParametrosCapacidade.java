package com.fiscalhub.backend.capacidade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Parâmetros das estimativas e da análise gerencial, por escritório: tudo o que a norma NÃO fixa (faixas A-D da CAPAG,
 * fórmulas de GRF e PLR, percentual do caixa livre). Visíveis em todo resultado que os usa, com o rótulo de estimativa.
 * Sem linha no banco: os padrões abaixo (os do documento de referência docs/normativas, marcados como parâmetro).
 */
@Service
public class ParametrosCapacidade {

    /**
     * faixasClasse: CAPAG ÷ dívida mínima para A, B e C (abaixo de C: D). metodoConsiderado: qual estimativa define a
     * classe (MENOR, MAIOR, RESULTADO_OPERACIONAL, FLUXO_DE_CAIXA). exercicios: quantos exercícios entram nas médias.
     * realizacao: % de realização no PLR de disponível, créditos, estoques e imobilizado.
     */
    public record ParametrosCapag(@NotNull @DecimalMin("0") BigDecimal faixaA, @NotNull @DecimalMin("0") BigDecimal faixaB,
                        @NotNull @DecimalMin("0") BigDecimal faixaC,
                        @NotNull @Pattern(regexp = "MENOR|MAIOR|RESULTADO_OPERACIONAL|FLUXO_DE_CAIXA") String metodoConsiderado,
                        @NotNull @Min(1) @Max(5) Integer exercicios,
                        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal realizacaoDisponivel,
                        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal realizacaoCreditos,
                        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal realizacaoEstoques,
                        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal realizacaoImobilizado) {
    }

    /** percentualFcoLivre: fração do caixa operacional livre que pode ir para parcelas (padrão 30%). */
    public record ParametrosGerencial(@NotNull @DecimalMin("1") @DecimalMax("100") BigDecimal percentualFcoLivre) {
    }

    public record ParametrosEscritorio(@NotNull @Valid ParametrosCapag capag, @NotNull @Valid ParametrosGerencial gerencial, String atualizadoPor,
                             LocalDateTime atualizadoEm) {
    }

    public static final ParametrosEscritorio PADRAO = new ParametrosEscritorio(
            new ParametrosCapag(new BigDecimal("2"), new BigDecimal("1"), new BigDecimal("0.5"), "MENOR", 2,
                    new BigDecimal("100"), new BigDecimal("80"), new BigDecimal("50"), new BigDecimal("30")),
            new ParametrosGerencial(new BigDecimal("30")), null, null);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ParametrosCapacidade(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public ParametrosEscritorio consultar(long escritorioId) {
        return jdbc.query("""
                SELECT p.parametros::text, u.nome, p.atualizado_em FROM parametro_capacidade p JOIN usuario u ON u.id = p.atualizado_por
                WHERE p.escritorio_id = ?""", (rs, i) -> {
            ParametrosEscritorio p = ler(rs.getString(1));
            return new ParametrosEscritorio(p.capag(), p.gerencial(), rs.getString(2), rs.getTimestamp(3).toLocalDateTime());
        }, escritorioId).stream().findFirst().orElse(PADRAO);
    }

    @Transactional
    public ParametrosEscritorio alterar(ParametrosEscritorio p, UsuarioAutenticado ua) {
        ParametrosCapag c = p.capag();
        if (c.faixaA().compareTo(c.faixaB()) <= 0 || c.faixaB().compareTo(c.faixaC()) <= 0) {
            throw new BusinessRuleException("As faixas da CAPAG precisam ser decrescentes: A > B > C.");
        }
        String texto;
        try {
            texto = json.writeValueAsString(new ParametrosEscritorio(c, p.gerencial(), null, null));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        jdbc.update("""
                INSERT INTO parametro_capacidade (escritorio_id, parametros, atualizado_em, atualizado_por) VALUES (?, ?::jsonb, ?, ?)
                ON CONFLICT (escritorio_id) DO UPDATE SET parametros = EXCLUDED.parametros, atualizado_em = EXCLUDED.atualizado_em,
                    atualizado_por = EXCLUDED.atualizado_por""",
                ua.escritorioId(), texto, Timestamp.valueOf(LocalDateTime.now()), ua.id());
        return consultar(ua.escritorioId());
    }

    private ParametrosEscritorio ler(String texto) {
        try {
            return json.readValue(texto, ParametrosEscritorio.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
