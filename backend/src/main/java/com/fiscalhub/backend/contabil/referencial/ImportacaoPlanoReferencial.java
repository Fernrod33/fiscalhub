package com.fiscalhub.backend.contabil.referencial;

import com.fiscalhub.backend.sped.LeitorSped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Carrega as tabelas oficiais do Plano de Contas Referencial (src/main/resources/plano-referencial, fonte de cada uma
 * em tabelas.txt) em conta_referencial. Reexecutável: roda a cada subida e só recarrega a tabela cujo SHA-256 mudou.
 * Nenhuma conta referencial é digitada no código.
 */
@Component
public class ImportacaoPlanoReferencial {

    private static final Logger log = LoggerFactory.getLogger(ImportacaoPlanoReferencial.class);
    private static final String PASTA = "plano-referencial/";
    private static final String FONTE = "Sped Tabelas (sped.fazenda.gov.br/spedtabelas), pacote %s, tabela %s, arquivo %s";
    /** 2022 em diante: planilha oficial da ECF (URL, data de atualização no portal, SHA-256 da planilha e aba). */
    private static final String FONTE_PLANILHA = "Tabelas Dinâmicas e Planos de Contas Referenciais da ECF, %s (atualização %s), %s";
    private static final Pattern VERSAO = Pattern.compile("vers\\S*=(\\d+)");

    private final JdbcTemplate jdbc;

    public ImportacaoPlanoReferencial(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void importar() {
        for (String linha : ler("tabelas.txt", StandardCharsets.UTF_8)) {
            if (linha.isBlank() || linha.startsWith("#")) {
                continue;
            }
            String[] f = linha.split("\\|");
            String fonte = (f[4].startsWith("http") ? FONTE_PLANILHA : FONTE).formatted(f[4], f[5], f[6]);
            importarTabela(f[0], Integer.parseInt(f[1]), f[2], f[3], fonte, f[7]);
        }
    }

    private void importarTabela(String tabela, int ano, String plano, String demonstracao, String fonte, String shaEsperado) {
        byte[] conteudo = bytes(tabela + ".txt");
        String sha = sha256(conteudo);
        if (!sha.equals(shaEsperado)) {
            throw new IllegalStateException("Tabela referencial " + tabela + " diferente da registrada em tabelas.txt");
        }
        List<String> atual = jdbc.queryForList("SELECT sha256 FROM tabela_referencial WHERE tabela = ?", String.class, tabela);
        if (atual.equals(List.of(sha))) {
            return;
        }
        String[] linhas = new String(conteudo, StandardCharsets.ISO_8859_1).split("\r?\n");
        Matcher versao = VERSAO.matcher(linhas[0]);
        jdbc.update("DELETE FROM tabela_referencial WHERE tabela = ?", tabela); // cascata nas contas
        jdbc.update("INSERT INTO tabela_referencial (tabela, ano, plano, demonstracao, versao, sha256, fonte) VALUES (?, ?, ?, ?, ?, ?, ?)",
                tabela, ano, plano, demonstracao, versao.find() ? Integer.valueOf(versao.group(1)) : null, sha, fonte);
        List<Object[]> contas = new ArrayList<>();
        for (int i = 1; i < linhas.length; i++) {
            if (linhas[i].isBlank()) {
                continue;
            }
            // CODIGO|DESCRICAO|DT_INI|DT_FIM|ORDEM|TIPO|COD_SUP|NIVEL|NATUREZA (cabeçalho da própria tabela)
            String[] c = linhas[i].split("\\|", -1);
            contas.add(new Object[]{tabela, c[0].trim(), c[1].trim(), data(c[2]), data(c[3]), Integer.parseInt(c[4].trim()),
                    "S".equals(c[5].trim()), c[6].isBlank() ? null : c[6].trim(), Integer.parseInt(c[7].trim()),
                    c.length > 8 ? c[8].trim() : null});
        }
        jdbc.batchUpdate("""
                INSERT INTO conta_referencial (tabela, codigo, descricao, data_inicio, data_fim, ordem, sintetica,
                                               codigo_superior, nivel, natureza) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", contas);
        log.info("Plano referencial {} carregado: {} contas", tabela, contas.size());
    }

    private static Date data(String ddmmaaaa) {
        LocalDate d = LeitorSped.data(ddmmaaaa.trim());
        return d == null ? null : Date.valueOf(d);
    }

    private static List<String> ler(String arquivo, java.nio.charset.Charset charset) {
        return List.of(new String(bytes(arquivo), charset).split("\r?\n"));
    }

    private static byte[] bytes(String arquivo) {
        try {
            return new ClassPathResource(PASTA + arquivo).getContentAsByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
