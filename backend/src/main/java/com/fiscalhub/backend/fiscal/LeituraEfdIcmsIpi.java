package com.fiscalhub.backend.fiscal;

import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.importacao.ContextoProcessamento;
import com.fiscalhub.backend.importacao.Escrituracoes;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import com.fiscalhub.backend.fiscal.RepositorioApuracao.Apuracao;
import com.fiscalhub.backend.fiscal.RepositorioApuracao.Obrigacao;
import com.fiscalhub.backend.fiscal.RepositorioApuracao.Origem;
import com.fiscalhub.backend.fiscal.RepositorioApuracao.SaldoFiscal;
import com.fiscalhub.backend.sped.LayoutSped;
import com.fiscalhub.backend.sped.LeituraSped;
import com.fiscalhub.backend.sped.RegistroSped;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EFD ICMS/IPI (por estabelecimento): apuração do ICMS próprio (E100/E110/E111/E116), do ICMS-ST por UF
 * (E200/E210/E250), do DIFAL e do FCP por UF (E300/E310/E316) e do IPI (E500/E520/E530). Os blocos C, D, G, H, K e os
 * demais são pulados sem virar campos. Fórmulas conferidas como no Guia Prático 3.2.2 (campos 11, 13 e 14 do E110 e
 * equivalentes; soma das obrigações = a recolher + extra-apuração). Leiautes: resources/sped/efd-icms-ipi-leiautes.properties.
 * ponytail: a descrição dos códigos de ajuste (tabela 5.1.1 de cada UF) não está carregada; o detalhe mostra o código e
 * a descrição complementar do próprio arquivo. Carregar as tabelas das UFs quando houver pedido.
 */
final class LeituraEfdIcmsIpi extends LeituraSped {

    private static final Set<String> LIDOS = Set.of("E100", "E110", "E111", "E116", "E200", "E210", "E250", "E300", "E310",
            "E316", "E500", "E520", "E530");
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");
    /** Descrição dos campos de saldo (Guia Prático), sem o sufixo do tributo (_ST, _DIFAL, _FCP, _IPI). */
    private static final Map<String, String> DESCRICOES = Map.ofEntries(
            Map.entry("VL_TOT_DEBITOS", "Débitos por saídas e prestações"), Map.entry("VL_TOT_DEB", "Débitos"),
            Map.entry("VL_DEB", "Débitos por saídas"), Map.entry("VL_RETENCAO", "ICMS retido por substituição tributária"),
            Map.entry("VL_AJ_DEBITOS", "Ajustes a débito"), Map.entry("VL_TOT_AJ_DEBITOS", "Ajustes a débito da apuração"),
            Map.entry("VL_OUT_DEB", "Outros débitos"), Map.entry("VL_OD", "Outros débitos"),
            Map.entry("VL_ESTORNOS_CRED", "Estornos de créditos"), Map.entry("VL_TOT_CREDITOS", "Créditos por entradas e aquisições"),
            Map.entry("VL_TOT_CRED", "Créditos"), Map.entry("VL_CRED", "Créditos por entradas"),
            Map.entry("VL_AJ_CREDITOS", "Ajustes a crédito"), Map.entry("VL_TOT_AJ_CREDITOS", "Ajustes a crédito da apuração"),
            Map.entry("VL_OUT_CRED", "Outros créditos"), Map.entry("VL_OC", "Outros créditos"),
            Map.entry("VL_DEVOL", "Devoluções"), Map.entry("VL_RESSARC", "Ressarcimentos"),
            Map.entry("VL_ESTORNOS_DEB", "Estornos de débitos"), Map.entry("VL_SLD_CREDOR_ANT", "Saldo credor do período anterior"),
            Map.entry("VL_SLD_CRED_ANT", "Saldo credor do período anterior"), Map.entry("VL_SD_ANT", "Saldo credor do período anterior"),
            Map.entry("VL_SLD_APURADO", "Saldo devedor apurado"), Map.entry("VL_SLD_DEV_ANT", "Saldo devedor antes das deduções"),
            Map.entry("VL_TOT_DED", "Deduções"), Map.entry("VL_DEDUCOES", "Deduções"),
            Map.entry("VL_ICMS_RECOLHER", "ICMS a recolher"), Map.entry("VL_ICMS_RECOL", "ICMS a recolher"),
            Map.entry("VL_RECOL", "Valor a recolher"), Map.entry("VL_SD", "Saldo devedor a recolher"),
            Map.entry("VL_SLD_CREDOR_TRANSPORTAR", "Saldo credor a transportar"), Map.entry("VL_SLD_CRED_TRANSPORTAR", "Saldo credor a transportar"),
            Map.entry("VL_SLD_CRED_ST_TRANSPORTAR", "Saldo credor a transportar"), Map.entry("VL_SC", "Saldo credor a transportar"),
            Map.entry("DEB_ESP", "Débitos especiais (extra-apuração)"));
    /** COD_OR (tabela 5.4 do guia) 006: ICMS da alíquota adicional do Fundo de Combate à Pobreza. */
    private static final String OBRIGACAO_FCP = "006";

    private final RepositorioApuracao apuracoes;
    private final List<RegistroSped> lidos = new ArrayList<>();
    private final List<Apuracao> montadas = new ArrayList<>();
    private final List<SaldoFiscal> saldos = new ArrayList<>();
    private String uf;

    LeituraEfdIcmsIpi(DocumentoFiscal documento, ContextoProcessamento contexto, Escrituracoes escrituracoes,
                      RepositorioApuracao apuracoes, LayoutSped layout) {
        super("EFD_ICMS_IPI", "EFD ICMS/IPI", layout, LIDOS, documento, contexto, escrituracoes);
        this.apuracoes = apuracoes;
    }

    /** |0000|COD_VER|... */
    @Override
    protected String versao(String registro, String[] campos) {
        return "0000".equals(registro) && campos.length > 2 ? campos[2].trim() : null;
    }

    /** COD_FIN 1 = arquivo substituto. O CNPJ é do estabelecimento. */
    @Override
    protected void abertura(RegistroSped r) {
        uf = r.texto("UF");
        abrirEscrituracao(r.texto("CNPJ"), r.texto("NOME"), r.data("DT_INI"), r.data("DT_FIN"), "1".equals(r.texto("COD_FIN")), null);
    }

    /** O bloco E tem poucas linhas por mês: fica em memória até o fim. */
    @Override
    protected void tratar(RegistroSped r) {
        lidos.add(r);
    }

    /** Monta as apurações na ordem do arquivo (cada E100/E200/E300/E500 abre um grupo) e confere as fórmulas. */
    @Override
    protected void validar() {
        LocalDate ini = inicio;
        LocalDate fimPeriodo = fim;
        String ufGrupo = uf;
        Apuracao atual = null;
        Apuracao fcp = null;
        RegistroSped totais = null;
        List<RegistroSped> filhos = new ArrayList<>();
        for (RegistroSped r : lidos) {
            switch (r.registro()) {
                case "E100", "E200", "E300", "E500" -> {
                    fechar(totais, filhos, atual, fcp);
                    totais = null;
                    atual = null;
                    fcp = null;
                    filhos = new ArrayList<>();
                    ini = r.data("DT_INI");
                    fimPeriodo = r.data("DT_FIN");
                    ufGrupo = "E100".equals(r.registro()) || "E500".equals(r.registro()) ? uf : r.texto("UF");
                }
                case "E110" -> {
                    totais = r;
                    atual = icms(r, ini, fimPeriodo, ufGrupo);
                }
                case "E210" -> {
                    totais = r;
                    atual = st(r, ini, fimPeriodo, ufGrupo);
                }
                case "E310" -> {
                    totais = r;
                    atual = difal(r, ini, fimPeriodo, ufGrupo, "DIFAL");
                    fcp = difal(r, ini, fimPeriodo, ufGrupo, "FCP");
                }
                case "E520" -> {
                    totais = r;
                    atual = ipi(r, ini, fimPeriodo);
                }
                default -> filhos.add(r); // E111, E116, E250, E316, E530
            }
        }
        fechar(totais, filhos, atual, fcp);
    }

    @Override
    protected void gravar() {
        apuracoes.gravar(escrituracaoId, cliente.getId(), documento.getId(), montadas);
        apuracoes.gravarSaldos(escrituracaoId, cliente.getId(), saldos);
    }

    // --- ICMS próprio ----------------------------------------------------------------------------------------------

    private Apuracao icms(RegistroSped r, LocalDate ini, LocalDate f, String ufGrupo) {
        BigDecimal debitos = soma(r, "VL_TOT_DEBITOS", "VL_AJ_DEBITOS", "VL_TOT_AJ_DEBITOS", "VL_ESTORNOS_CRED");
        BigDecimal creditos = soma(r, "VL_TOT_CREDITOS", "VL_AJ_CREDITOS", "VL_TOT_AJ_CREDITOS", "VL_ESTORNOS_DEB", "VL_SLD_CREDOR_ANT");
        BigDecimal expressao = debitos.subtract(creditos);
        BigDecimal apurado = v(r, "VL_SLD_APURADO");
        BigDecimal deducoes = v(r, "VL_TOT_DED");
        conferir(r, "VL_SLD_APURADO", expressao.max(BigDecimal.ZERO), "débitos − créditos (campos 02 a 10), se positivo");
        conferir(r, "VL_ICMS_RECOLHER", apurado.subtract(deducoes).max(BigDecimal.ZERO), "saldo devedor − deduções, se positivo");
        conferir(r, "VL_SLD_CREDOR_TRANSPORTAR", expressao.negate().max(BigDecimal.ZERO)
                .add(deducoes.subtract(apurado).max(BigDecimal.ZERO)), "créditos excedentes + deduções acima do saldo devedor");
        Apuracao a = new Apuracao("ICMS", "ESTADUAL", ufGrupo, ini, f, "MENSAL", null, "Operações próprias");
        valores(a, r, debitos, creditos.add(deducoes), soma(r, "VL_ICMS_RECOLHER", "DEB_ESP"), v(r, "VL_SLD_CREDOR_TRANSPORTAR"));
        detalhes(a, r, "VL_TOT_DEBITOS", "VL_AJ_DEBITOS", "VL_TOT_AJ_DEBITOS", "VL_ESTORNOS_CRED", "VL_TOT_CREDITOS",
                "VL_AJ_CREDITOS", "VL_TOT_AJ_CREDITOS", "VL_ESTORNOS_DEB", "VL_SLD_CREDOR_ANT", "VL_SLD_APURADO", "VL_TOT_DED",
                "VL_ICMS_RECOLHER", "VL_SLD_CREDOR_TRANSPORTAR", "DEB_ESP");
        saldo("SALDO_CREDOR_ICMS", a, r, v(r, "VL_SLD_CREDOR_ANT"), "Saldo credor de ICMS a transportar");
        return a;
    }

    // --- ICMS-ST -----------------------------------------------------------------------------------------------------

    private Apuracao st(RegistroSped r, LocalDate ini, LocalDate f, String ufGrupo) {
        BigDecimal debitos = soma(r, "VL_RETENCAO_ST", "VL_OUT_DEB_ST", "VL_AJ_DEBITOS_ST");
        BigDecimal creditos = soma(r, "VL_SLD_CRED_ANT_ST", "VL_DEVOL_ST", "VL_RESSARC_ST", "VL_OUT_CRED_ST", "VL_AJ_CREDITOS_ST");
        BigDecimal expressao = debitos.subtract(creditos);
        conferir(r, "VL_SLD_DEV_ANT_ST", expressao.max(BigDecimal.ZERO), "débitos ST − créditos ST, se positivo");
        conferir(r, "VL_ICMS_RECOL_ST", v(r, "VL_SLD_DEV_ANT_ST").subtract(v(r, "VL_DEDUCOES_ST")), "saldo devedor − deduções");
        conferir(r, "VL_SLD_CRED_ST_TRANSPORTAR", expressao.negate().max(BigDecimal.ZERO), "créditos ST − débitos ST, se positivo");
        Apuracao a = new Apuracao("ICMS_ST", "ESTADUAL", ufGrupo, ini, f, "MENSAL", null, "Substituição tributária");
        valores(a, r, debitos, creditos.add(v(r, "VL_DEDUCOES_ST")), soma(r, "VL_ICMS_RECOL_ST", "DEB_ESP_ST"),
                v(r, "VL_SLD_CRED_ST_TRANSPORTAR"));
        detalhes(a, r, "VL_RETENCAO_ST", "VL_OUT_DEB_ST", "VL_AJ_DEBITOS_ST", "VL_SLD_CRED_ANT_ST", "VL_DEVOL_ST", "VL_RESSARC_ST",
                "VL_OUT_CRED_ST", "VL_AJ_CREDITOS_ST", "VL_SLD_DEV_ANT_ST", "VL_DEDUCOES_ST", "VL_ICMS_RECOL_ST",
                "VL_SLD_CRED_ST_TRANSPORTAR", "DEB_ESP_ST");
        saldo("SALDO_CREDOR_ICMS_ST", a, r, v(r, "VL_SLD_CRED_ANT_ST"), "Saldo credor de ICMS-ST a transportar");
        return a;
    }

    // --- DIFAL e FCP (E310: um registro, duas apurações) ------------------------------------------------------------

    private Apuracao difal(RegistroSped r, LocalDate ini, LocalDate f, String ufGrupo, String parte) {
        boolean ehFcp = "FCP".equals(parte);
        String sufixo = "_" + parte;
        String totDeb = ehFcp ? "VL_TOT_DEB_FCP" : "VL_TOT_DEBITOS_DIFAL";
        String totCred = ehFcp ? "VL_TOT_CRED_FCP" : "VL_TOT_CREDITOS_DIFAL";
        BigDecimal debitos = soma(r, totDeb, "VL_OUT_DEB" + sufixo);
        BigDecimal creditos = soma(r, "VL_SLD_CRED_ANT" + sufixo, totCred, "VL_OUT_CRED" + sufixo);
        BigDecimal expressao = debitos.subtract(creditos);
        conferir(r, "VL_SLD_DEV_ANT" + sufixo, expressao.max(BigDecimal.ZERO), "débitos − créditos " + parte + ", se positivo");
        conferir(r, "VL_RECOL" + sufixo, v(r, "VL_SLD_DEV_ANT" + sufixo).subtract(v(r, "VL_DEDUCOES" + sufixo)).max(BigDecimal.ZERO),
                "saldo devedor − deduções " + parte + ", se positivo");
        conferir(r, "VL_SLD_CRED_TRANSPORTAR" + sufixo, expressao.negate().max(BigDecimal.ZERO), "créditos − débitos " + parte + ", se positivo");
        if (debitos.signum() == 0 && creditos.signum() == 0 && soma(r, "DEB_ESP" + sufixo).signum() == 0) {
            return null; // sem movimento nesta parte
        }
        Apuracao a = new Apuracao(ehFcp ? "ICMS_FCP" : "ICMS_DIFAL", "ESTADUAL", ufGrupo, ini, f, "MENSAL", null,
                ehFcp ? "Fundo de Combate à Pobreza" : "Diferencial de alíquotas");
        valores(a, r, debitos, creditos.add(v(r, "VL_DEDUCOES" + sufixo)), soma(r, "VL_RECOL" + sufixo, "DEB_ESP" + sufixo),
                v(r, "VL_SLD_CRED_TRANSPORTAR" + sufixo));
        detalhes(a, r, totDeb, "VL_OUT_DEB" + sufixo, "VL_SLD_CRED_ANT" + sufixo, totCred, "VL_OUT_CRED" + sufixo,
                "VL_SLD_DEV_ANT" + sufixo, "VL_DEDUCOES" + sufixo, "VL_RECOL" + sufixo, "VL_SLD_CRED_TRANSPORTAR" + sufixo,
                "DEB_ESP" + sufixo);
        saldo(ehFcp ? "SALDO_CREDOR_FCP" : "SALDO_CREDOR_DIFAL", a, r, v(r, "VL_SLD_CRED_ANT" + sufixo),
                "Saldo credor de " + (ehFcp ? "FCP" : "DIFAL") + " a transportar");
        return a;
    }

    // --- IPI -----------------------------------------------------------------------------------------------------------

    private Apuracao ipi(RegistroSped r, LocalDate ini, LocalDate f) {
        BigDecimal debitos = soma(r, "VL_DEB_IPI", "VL_OD_IPI");
        BigDecimal creditos = soma(r, "VL_SD_ANT_IPI", "VL_CRED_IPI", "VL_OC_IPI");
        BigDecimal expressao = debitos.subtract(creditos);
        conferir(r, "VL_SD_IPI", expressao.max(BigDecimal.ZERO), "débitos − créditos de IPI, se positivo");
        conferir(r, "VL_SC_IPI", expressao.negate().max(BigDecimal.ZERO), "créditos − débitos de IPI, se positivo");
        Apuracao a = new Apuracao("IPI", "FEDERAL", null, ini, f, "MENSAL", null, "Apuração do IPI");
        valores(a, r, debitos, creditos, v(r, "VL_SD_IPI"), v(r, "VL_SC_IPI"));
        detalhes(a, r, "VL_SD_ANT_IPI", "VL_DEB_IPI", "VL_CRED_IPI", "VL_OD_IPI", "VL_OC_IPI", "VL_SC_IPI", "VL_SD_IPI");
        saldo("SALDO_CREDOR_IPI", a, r, v(r, "VL_SD_ANT_IPI"), "Saldo credor de IPI a transportar");
        return a;
    }

    // --- filhos de cada apuração: ajustes e obrigações ---------------------------------------------------------------

    /** Fecha um grupo: ajustes no detalhe (e conferidos com os totais), obrigações na apuração e soma das obrigações. */
    private void fechar(RegistroSped totais, List<RegistroSped> filhos, Apuracao principal, Apuracao fcp) {
        if (totais == null) {
            return;
        }
        BigDecimal[] porTipo = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        BigDecimal[] ipi = {BigDecimal.ZERO, BigDecimal.ZERO};
        BigDecimal obrigacoes = BigDecimal.ZERO;
        for (RegistroSped f : filhos) {
            switch (f.registro()) {
                case "E111" -> {
                    String codigo = f.texto("COD_AJ_APUR");
                    BigDecimal valor = v(f, "VL_AJ_APUR");
                    principal.detalhe("AJUSTE", codigo, f.texto("DESCR_COMPL_AJ") == null ? "Ajuste " + codigo
                            : f.texto("DESCR_COMPL_AJ"), valor, f);
                    if (codigo != null && codigo.length() >= 4 && codigo.charAt(2) == '0' && codigo.charAt(3) >= '0' && codigo.charAt(3) <= '4') {
                        porTipo[codigo.charAt(3) - '0'] = porTipo[codigo.charAt(3) - '0'].add(valor);
                    }
                }
                case "E530" -> {
                    BigDecimal valor = v(f, "VL_AJ");
                    principal.detalhe("AJUSTE", f.texto("COD_AJ"), f.texto("DESCR_AJ") == null ? "Ajuste " + f.texto("COD_AJ")
                            : f.texto("DESCR_AJ"), valor, f);
                    if ("0".equals(f.texto("IND_AJ")) || "1".equals(f.texto("IND_AJ"))) {
                        int i = Integer.parseInt(f.texto("IND_AJ"));
                        ipi[i] = ipi[i].add(valor);
                    }
                }
                default -> { // E116, E250, E316
                    BigDecimal valor = v(f, "VL_OR");
                    obrigacoes = obrigacoes.add(valor);
                    Apuracao destino = fcp != null && OBRIGACAO_FCP.equals(f.texto("COD_OR")) ? fcp : principal;
                    if (destino == null) {
                        destino = fcp; // obrigação de DIFAL num período só com FCP (ou o contrário)
                    }
                    if (destino != null) {
                        destino.obrigacoes.add(new Obrigacao(f.texto("COD_OR"), f.texto("COD_REC"), f.data("DT_VCTO"), valor,
                                f.registro(), f.linha()));
                    }
                }
            }
        }
        switch (totais.registro()) {
            case "E110" -> {
                conferir(totais, "VL_TOT_AJ_DEBITOS", porTipo[0], "soma dos E111 de outros débitos (código x x 0 0)");
                conferir(totais, "VL_ESTORNOS_CRED", porTipo[1], "soma dos E111 de estornos de crédito (x x 0 1)");
                conferir(totais, "VL_TOT_AJ_CREDITOS", porTipo[2], "soma dos E111 de outros créditos (x x 0 2)");
                conferir(totais, "VL_ESTORNOS_DEB", porTipo[3], "soma dos E111 de estornos de débito (x x 0 3)");
                conferirObrigacoes(totais, "E116", obrigacoes, soma(totais, "VL_ICMS_RECOLHER", "DEB_ESP"));
            }
            case "E210" -> conferirObrigacoes(totais, "E250", obrigacoes, soma(totais, "VL_ICMS_RECOL_ST", "DEB_ESP_ST"));
            case "E310" -> conferirObrigacoes(totais, "E316", obrigacoes,
                    soma(totais, "VL_RECOL_DIFAL", "DEB_ESP_DIFAL", "VL_RECOL_FCP", "DEB_ESP_FCP"));
            case "E520" -> {
                conferir(totais, "VL_OD_IPI", ipi[0], "soma dos E530 de ajuste a débito");
                conferir(totais, "VL_OC_IPI", ipi[1], "soma dos E530 de ajuste a crédito");
            }
            default -> { }
        }
        if (principal != null) {
            montadas.add(principal);
        }
        if (fcp != null) {
            montadas.add(fcp);
        }
    }

    private void conferirObrigacoes(RegistroSped totais, String registro, BigDecimal obrigacoes, BigDecimal esperado) {
        if (obrigacoes.subtract(esperado).abs().compareTo(TOLERANCIA) > 0) {
            contexto.registrar(Severidade.ERRO, registro, totais.linha(), "SOMA_" + registro, "A soma das obrigações a recolher ("
                    + registro + ": " + obrigacoes.toPlainString() + ") é diferente do valor a recolher + extra-apuração do "
                    + totais.registro() + " (" + esperado.toPlainString() + ").");
        }
    }

    // --- apoio -------------------------------------------------------------------------------------------------------

    private static void valores(Apuracao a, RegistroSped r, BigDecimal apurado, BigDecimal deducoes, BigDecimal aRecolher,
                                BigDecimal saldoCredor) {
        Origem origem = Origem.de(r);
        a.apurado = apurado;
        a.deducoes = deducoes;
        a.aRecolher = aRecolher;
        a.saldoCredor = saldoCredor;
        for (String campo : List.of("valorApurado", "deducoes", "valorARecolher", "saldoCredor")) {
            a.origem.put(campo, origem);
        }
    }

    private static void detalhes(Apuracao a, RegistroSped r, String... campos) {
        for (String campo : campos) {
            String semSufixo = campo.replaceFirst("_(ST|DIFAL|FCP|IPI)$", "");
            a.detalhe("CALCULO", campo, DESCRICOES.getOrDefault(semSufixo, campo), v(r, campo), r);
        }
    }

    private void saldo(String tipo, Apuracao a, RegistroSped r, BigDecimal anterior, String descricao) {
        saldos.add(new SaldoFiscal(tipo, a.uf, null, descricao, a.inicio, a.fim, anterior, a.saldoCredor, r.registro(), r.linha()));
    }

    private void conferir(RegistroSped r, String campo, BigDecimal esperado, String expressao) {
        BigDecimal informado = v(r, campo);
        if (informado.subtract(esperado).abs().compareTo(TOLERANCIA) > 0) {
            contexto.registrar(Severidade.ERRO, r.registro(), r.linha(), "FORMULA_" + r.registro(), r.registro() + "." + campo
                    + " informado " + informado.toPlainString() + ", mas " + expressao + " = " + esperado.toPlainString() + ".");
        }
    }

    private static BigDecimal v(RegistroSped r, String campo) {
        BigDecimal valor = r.decimal(campo);
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private static BigDecimal soma(RegistroSped r, String... campos) {
        BigDecimal total = BigDecimal.ZERO;
        for (String campo : campos) {
            total = total.add(v(r, campo));
        }
        return total;
    }
}
