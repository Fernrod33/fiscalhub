package com.fiscalhub.backend.sped;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Posição dos campos de cada registro, por versão do leiaute. Único lugar com essa informação: o arquivo
 * .properties é gerado dos manuais oficiais (ver scripts/sped). Nenhum índice de campo espalhado pelo código.
 */
public final class LayoutSped {

    private final Map<String, Map<String, Map<String, Integer>>> porVersao = new HashMap<>();

    private LayoutSped() {
    }

    /** Carrega o recurso "versão.REGISTRO=CAMPO1,CAMPO2,..." (CAMPO1 é o REG, campo 01 do manual). */
    public static LayoutSped carregar(String recurso) {
        Properties p = new Properties();
        try (InputStream in = LayoutSped.class.getClassLoader().getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IllegalStateException("Leiaute não encontrado: " + recurso);
            }
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        LayoutSped layout = new LayoutSped();
        for (String chave : p.stringPropertyNames()) {
            int ponto = chave.lastIndexOf('.');
            String versao = chave.substring(0, ponto);
            String registro = chave.substring(ponto + 1);
            Map<String, Integer> indices = new HashMap<>();
            List<String> campos = List.of(p.getProperty(chave).split(","));
            for (int i = 0; i < campos.size(); i++) {
                indices.put(campos.get(i).trim(), i + 1); // numeração do manual: REG = 1
            }
            layout.porVersao.computeIfAbsent(versao, v -> new HashMap<>()).put(registro, indices);
        }
        return layout;
    }

    public Set<String> versoes() {
        return new TreeSet<>(porVersao.keySet());
    }

    public boolean cobre(String versao) {
        return porVersao.containsKey(versao);
    }

    /** Lê uma linha já quebrada em campos, segundo o leiaute da versão. */
    public RegistroSped registro(String versao, String registro, long linha, String[] campos) {
        Map<String, Integer> indices = porVersao.get(versao).get(registro);
        if (indices == null) {
            throw new IllegalArgumentException("Registro " + registro + " fora do leiaute " + versao);
        }
        return new RegistroSped(registro, linha, campos, indices);
    }
}
