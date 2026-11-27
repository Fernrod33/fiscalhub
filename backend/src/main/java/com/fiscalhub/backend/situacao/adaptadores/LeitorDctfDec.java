package com.fiscalhub.backend.situacao.adaptadores;

import com.fiscalhub.backend.importacao.FalhaImportacao;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import com.fiscalhub.backend.situacao.Extracao;
import com.fiscalhub.backend.situacao.Extracao.Origem;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * .dec da DCTF Mensal: leitura de largura fixa pelo layout versionado em resources/dctf (registro -> campos com início e
 * tamanho). Versão única por enquanto: ficticio-2026-10, o layout APROXIMADO da massa fictícia, não o do PGD. O .dec
 * real entra como outro layout e outro reconhecimento, sem mexer neste. R10 = débito; R11 = vinculação do débito do
 * mesmo código (DARF = pagamento, COMP = compensação, SUSP = suspensão, PARC = parcelamento).
 */
public final class LeitorDctfDec {

    public static final String VERSAO = "ficticio-2026-10";
    private static final Properties ARQUIVO_LAYOUT = propriedades("dctf/dec-ficticio-2026-10.properties");
    private static final Map<String, Map<String, int[]>> LAYOUT = layout(ARQUIVO_LAYOUT);
    private static final int TAMANHO_CABECALHO = Integer.parseInt(ARQUIVO_LAYOUT.getProperty("CABECALHO.tamanho"));
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("ddMMuuuu");

    private LeitorDctfDec() {
    }

    public static void ler(byte[] bytes, Extracao extracao) {
        String[] linhas = new String(bytes, StandardCharsets.ISO_8859_1).split("\r?\n");
        if (linhas.length == 0 || !linhas[0].startsWith("DCTFM") || linhas[0].length() != TAMANHO_CABECALHO) {
            throw new FalhaImportacao("LAYOUT_DEC_NAO_RECONHECIDO", "O .dec não segue o layout conhecido (" + VERSAO
                    + ", aproximado). O layout do PGD DCTF ainda não é lido: importe o PDF da impressão da declaração.", null, 1L);
        }
        Map<String, String> cabecalho = campos("CABECALHO", linhas[0]);
        extracao.cnpj = cabecalho.get("cnpj");
        extracao.nome = cabecalho.get("nome");
        YearMonth periodo = mesAno(cabecalho.get("periodo"));
        extracao.inicio = periodo.atDay(1);
        extracao.fim = periodo.atEndOfMonth();
        extracao.finalidade = cabecalho.get("finalidade").startsWith("RETIF") ? "SUBSTITUTA" : "ORIGINAL";

        Map<String, Map<String, String>> debitos = new LinkedHashMap<>();
        Map<String, Origem> origens = new HashMap<>();
        Map<String, Map<String, BigDecimal>> vinculos = new HashMap<>();
        int registros = 0;
        for (int i = 1; i < linhas.length; i++) {
            String linha = linhas[i];
            if (linha.isBlank()) {
                continue;
            }
            registros++;
            String registro = linha.substring(0, Math.min(3, linha.length()));
            Origem origem = new Origem(null, i + 1, linha.strip());
            switch (registro) {
                case "R10" -> {
                    Map<String, String> c = campos(registro, linha);
                    debitos.put(c.get("codigo"), c);
                    origens.put(c.get("codigo"), origem);
                }
                case "R11" -> {
                    Map<String, String> c = campos(registro, linha);
                    vinculos.computeIfAbsent(c.get("codigo"), k -> new HashMap<>()).merge(c.get("tipo"), centavos(c.get("valor")), BigDecimal::add);
                }
                case "R99" -> {
                    int declaradas = Integer.parseInt(campos(registro, linha).get("linhas"));
                    if (declaradas != registros + 1) {
                        extracao.alerta(Severidade.ALERTA, "CONTAGEM_DEC", "O R99 informa " + declaradas
                                + " linhas, mas o arquivo tem " + (registros + 1) + ".", origem);
                    }
                }
                case "R01" -> { }
                default -> extracao.alerta(Severidade.ALERTA, "REGISTRO_DEC_DESCONHECIDO", "Registro " + registro
                        + " fora do layout " + VERSAO + ": ignorado.", origem);
            }
        }
        debitos.forEach((codigo, c) -> {
            Map<String, BigDecimal> v = vinculos.getOrDefault(codigo, Map.of());
            extracao.declarados.add(new Extracao.Declarado(codigo.substring(0, 4) + "-" + codigo.substring(4),
                    mesAno(c.get("periodo")), LocalDate.parse(c.get("vencimento"), DATA), centavos(c.get("valor")),
                    v.get("DARF"), v.get("COMP"), v.get("SUSP"), v.get("PARC"), origens.get(codigo)));
        });
    }

    private static Map<String, String> campos(String registro, String linha) {
        Map<String, String> valores = new HashMap<>();
        LAYOUT.get(registro).forEach((campo, pos) -> {
            if (linha.length() < pos[0] + pos[1]) {
                throw new FalhaImportacao("LINHA_DEC_CURTA", "Registro " + registro + " com " + linha.length()
                        + " caracteres: o campo " + campo + " vai até a posição " + (pos[0] + pos[1]) + ".");
            }
            valores.put(campo, linha.substring(pos[0], pos[0] + pos[1]).strip());
        });
        return valores;
    }

    private static BigDecimal centavos(String digitos) {
        return new BigDecimal(digitos).movePointLeft(2);
    }

    private static YearMonth mesAno(String mmaaaa) {
        return YearMonth.of(Integer.parseInt(mmaaaa.substring(2)), Integer.parseInt(mmaaaa.substring(0, 2)));
    }

    private static Properties propriedades(String recurso) {
        Properties p = new Properties();
        try (InputStream in = new ClassPathResource(recurso).getInputStream()) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return p;
    }

    private static Map<String, Map<String, int[]>> layout(Properties p) {
        Map<String, Map<String, int[]>> layout = new HashMap<>();
        for (String registro : p.stringPropertyNames()) {
            if (registro.contains(".")) {
                continue;
            }
            Map<String, int[]> campos = new LinkedHashMap<>();
            for (String campo : p.getProperty(registro).split(",")) {
                String[] c = campo.split(":");
                campos.put(c[0], new int[]{Integer.parseInt(c[1]), Integer.parseInt(c[2])});
            }
            layout.put(registro, campos);
        }
        return layout;
    }
}
