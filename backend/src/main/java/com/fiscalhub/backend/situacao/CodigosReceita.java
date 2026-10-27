package com.fiscalhub.backend.situacao;

import com.fiscalhub.backend.entity.enums.TipoTributo;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tabela de códigos de receita (receitas/codigos-receita.txt, versionada como dado, com a fonte de cada código):
 * código -> esfera, órgão e tributo. Única tradução de código para tributo (DARF, DCTF, DCTFWeb, GR-PR, situação fiscal).
 */
public final class CodigosReceita {

    /** tributo null: parcela de parcelamento ou transação (vai para o parcelamento). */
    public record Receita(String codigo, String esfera, String orgao, TipoTributo tributo, String descricao, String fonte) {
        public boolean parcelamento() {
            return tributo == null;
        }
    }

    private static final Map<String, Receita> RECEITAS = carregar();

    private CodigosReceita() {
    }

    /** "5123-01", "5123 - IPI" ou "5123": pelos 4 primeiros dígitos. */
    public static Optional<Receita> receita(String codigo) {
        String digitos = codigo == null ? "" : codigo.trim().replaceAll("^(\\d{4}).*", "$1");
        return Optional.ofNullable(RECEITAS.get(digitos));
    }

    public static Optional<TipoTributo> tributo(String codigo) {
        return receita(codigo).map(Receita::tributo);
    }

    /** Código de um tributo quando o documento não traz o código (conta corrente do ICMS): o primeiro da tabela. */
    public static Optional<String> codigo(TipoTributo tributo, String esfera) {
        return RECEITAS.values().stream().filter(r -> r.tributo() == tributo && r.esfera().equals(esfera))
                .map(Receita::codigo).sorted().findFirst();
    }

    private static Map<String, Receita> carregar() {
        Map<String, Receita> receitas = new HashMap<>();
        try {
            String texto = new ClassPathResource("receitas/codigos-receita.txt").getContentAsString(StandardCharsets.UTF_8);
            for (String linha : texto.split("\r?\n")) {
                if (linha.isBlank() || linha.startsWith("#")) {
                    continue;
                }
                String[] f = linha.split("\\|");
                receitas.put(f[0], new Receita(f[0], f[1], f[2], "PARCELAMENTO".equals(f[3]) ? null : TipoTributo.valueOf(f[3]),
                        f[4], f[5]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Map.copyOf(receitas);
    }
}
