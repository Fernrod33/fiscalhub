package com.fiscalhub.backend.contabil.ecd;

import com.fiscalhub.backend.contabil.RepositorioContabil;
import com.fiscalhub.backend.contabil.RepositorioContabil.Conta;
import com.fiscalhub.backend.contabil.RepositorioContabil.LinhaDemonstracao;
import com.fiscalhub.backend.contabil.RepositorioContabil.Saldo;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.importacao.ContextoProcessamento;
import com.fiscalhub.backend.importacao.Escrituracoes;
import com.fiscalhub.backend.importacao.FalhaImportacao;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import com.fiscalhub.backend.sped.LayoutSped;
import com.fiscalhub.backend.sped.LeituraSped;
import com.fiscalhub.backend.sped.RegistroSped;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Uma leitura de ECD (estado de uma execução). Grava contas e saldos em lote durante a leitura e guarda em memória só
 * o que é pequeno: plano de contas, linhas das demonstrações publicadas e totais por período. Lançamentos (I200/I250)
 * e demais registros são pulados pelo {@link LeituraSped}.
 */
final class LeituraEcd extends LeituraSped {

    private static final Set<String> LIDOS = Set.of("I010", "I050", "I051", "I150", "I155", "I350", "I355", "J005", "J100", "J150");
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    private final RepositorioContabil repositorio;

    private String planoReferencial;

    private final List<ContaLida> contas = new ArrayList<>();
    private Map<String, Long> idsContas;
    private final Map<String, String> naturezaPorConta = new HashMap<>();
    private ContaLida ultimaConta;
    private RepositorioContabil.LoteSaldos lote;

    private LocalDate periodoInicio;
    private LocalDate periodoFim;
    private long linhaPeriodo;
    private LocalDate dataResultado;
    /** Débitos e créditos somados por fim de período do I150 (fim -> [débitos, créditos, linha do I150]). */
    private final Map<LocalDate, Object[]> somasPorPeriodo = new TreeMap<>();
    /** Saldos finais do último período (fim do exercício) por natureza da conta. */
    private final Map<String, BigDecimal> finalPorNatureza = new TreeMap<>();

    private boolean demonstracaoIndividual;
    private LocalDate inicioDemonstracao;
    private LocalDate fimDemonstracao;
    private final List<LinhaDemonstracao> balanco = new ArrayList<>();
    private final List<LinhaDemonstracao> resultado = new ArrayList<>();

    LeituraEcd(DocumentoFiscal documento, ContextoProcessamento contexto, Escrituracoes escrituracoes,
               RepositorioContabil repositorio, LayoutSped layout) {
        super("ECD", "ECD", layout, LIDOS, documento, contexto, escrituracoes);
        this.repositorio = repositorio;
    }

    /** Resultado da leitura que a montagem referencial usa. */
    record Lida(long escrituracaoId, String planoReferencial, LocalDate inicio, LocalDate fim,
                List<LinhaDemonstracao> balancoPublicado, List<LinhaDemonstracao> resultadoPublicado) {
    }

    Lida ler() {
        executar();
        return new Lida(escrituracaoId, planoReferencial, inicio, fim, balanco, resultado);
    }

    /** I010: |I010|IND_ESC|COD_VER_LC| em todos os leiautes. */
    @Override
    protected String versao(String registro, String[] campos) {
        return "I010".equals(registro) ? (campos.length > 3 ? campos[3].trim() : "") : null;
    }

    @Override
    protected void abertura(RegistroSped r) {
        planoReferencial = r.texto("COD_PLAN_REF");
        abrirEscrituracao(r.texto("CNPJ"), r.texto("NOME"), r.data("DT_INI"), r.data("DT_FIN"),
                "1".equals(r.texto("IND_FIN_ESC")), planoReferencial);
    }

    @Override
    protected void tratar(RegistroSped r) {
        switch (r.registro()) {
            case "I010" -> {
                String livro = r.texto("IND_ESC");
                if (!"G".equals(livro) && !"R".equals(livro)) {
                    contexto.registrar(Severidade.INFO, "I010", r.linha(), "LIVRO_AUXILIAR", "Escrituração do livro " + livro
                            + " (auxiliar): saldos e demonstrações costumam estar no livro principal (G ou R).");
                }
            }
            case "I050" -> {
                ultimaConta = new ContaLida(r);
                contas.add(ultimaConta);
            }
            case "I051" -> {
                if (ultimaConta != null && ultimaConta.referencial == null) {
                    ultimaConta.referencial = r.texto("COD_CTA_REF");
                }
                if (planoReferencial == null && r.texto("COD_PLAN_REF") != null) {
                    planoReferencial = r.texto("COD_PLAN_REF"); // leiaute 7: o plano vem no I051
                    escrituracoes.atualizarPlanoReferencial(escrituracaoId, planoReferencial);
                }
            }
            case "I150" -> {
                garantirContas();
                periodoInicio = r.data("DT_INI");
                periodoFim = r.data("DT_FIN");
                linhaPeriodo = r.linha();
            }
            case "I155" -> saldoPeriodico(r);
            case "I350" -> {
                garantirContas();
                dataResultado = r.data("DT_RES");
            }
            case "I355" -> saldoAntesDoEncerramento(r);
            case "J005" -> {
                inicioDemonstracao = r.data("DT_INI");
                fimDemonstracao = r.data("DT_FIN");
                demonstracaoIndividual = "1".equals(r.texto("ID_DEM")); // 2 = consolidadas: fora da comparação
            }
            case "J100" -> {
                if (demonstracaoIndividual) {
                    balanco.add(linhaPublicada(r, balanco.size() + 1, "IND_GRP_BAL"));
                }
            }
            case "J150" -> {
                if (demonstracaoIndividual) {
                    resultado.add(linhaPublicada(r, resultado.size() + 1, "IND_GRP_DRE"));
                }
            }
            default -> { }
        }
    }

    @Override
    protected void fimDaLeitura() {
        if (lote != null) {
            lote.close();
        }
    }

    /** O plano de contas vem antes dos saldos: grava tudo de uma vez quando o primeiro saldo aparece. */
    private void garantirContas() {
        if (idsContas != null) {
            return;
        }
        Map<String, ContaLida> unicas = new LinkedHashMap<>();
        for (ContaLida c : contas) {
            if (unicas.putIfAbsent(c.codigo, c) != null) {
                contexto.registrar(Severidade.ALERTA, "I050", c.linha, "CONTA_DUPLICADA",
                        "A conta " + c.codigo + " aparece mais de uma vez no plano de contas; vale a primeira.");
            }
        }
        idsContas = repositorio.inserirContas(escrituracaoId, unicas.values().stream().map(ContaLida::paraGravar).toList());
        unicas.values().forEach(c -> naturezaPorConta.put(c.codigo, c.natureza));
        lote = repositorio.lote(escrituracaoId);
    }

    private Long conta(RegistroSped r) {
        garantirContas();
        String codigo = r.texto("COD_CTA");
        Long id = idsContas.get(codigo);
        if (id == null) {
            contexto.registrar(Severidade.ERRO, r.registro(), r.linha(), "CONTA_FORA_DO_PLANO",
                    "A conta " + codigo + " não está no plano de contas (I050). O saldo foi ignorado.");
        }
        return id;
    }

    private void saldoPeriodico(RegistroSped r) {
        Long contaId = conta(r);
        if (contaId == null) {
            return;
        }
        if (periodoFim == null) {
            throw new FalhaImportacao("I155_SEM_I150", "Saldo periódico sem o período (I150) antes dele.", "I155", r.linha());
        }
        BigDecimal inicial = r.saldo("VL_SLD_INI", "IND_DC_INI");
        BigDecimal debitos = valor(r.decimal("VL_DEB"));
        BigDecimal creditos = valor(r.decimal("VL_CRED"));
        BigDecimal saldoFinal = r.saldo("VL_SLD_FIN", "IND_DC_FIN");
        BigDecimal esperado = inicial.add(debitos).subtract(creditos);
        if (esperado.subtract(saldoFinal).abs().compareTo(TOLERANCIA) > 0) {
            contexto.registrar(Severidade.ERRO, "I155", r.linha(), "SALDO_INCONSISTENTE", "Conta " + r.texto("COD_CTA")
                    + ": saldo inicial + débitos − créditos = " + esperado.toPlainString() + ", mas o saldo final é "
                    + saldoFinal.toPlainString() + " (devedor +, credor −).");
        }
        Object[] somas = somasPorPeriodo.computeIfAbsent(periodoFim, p -> new Object[]{BigDecimal.ZERO, BigDecimal.ZERO, linhaPeriodo});
        somas[0] = ((BigDecimal) somas[0]).add(debitos);
        somas[1] = ((BigDecimal) somas[1]).add(creditos);
        if (periodoFim.equals(fim)) {
            String natureza = naturezaPorConta.get(r.texto("COD_CTA"));
            finalPorNatureza.merge(natureza == null ? "--" : natureza, saldoFinal, BigDecimal::add);
        }
        lote.adicionar(new Saldo(contaId, "I155", r.texto("COD_CCUS"), periodoInicio, periodoFim, inicial, debitos,
                creditos, saldoFinal, "I155", r.linha()));
    }

    private void saldoAntesDoEncerramento(RegistroSped r) {
        Long contaId = conta(r);
        if (contaId == null) {
            return;
        }
        if (dataResultado == null) {
            throw new FalhaImportacao("I355_SEM_I350", "Saldo de resultado sem a data de apuração (I350) antes dele.", "I355", r.linha());
        }
        lote.adicionar(new Saldo(contaId, "I355", r.texto("COD_CCUS"), null, dataResultado, null, null, null,
                r.saldo("VL_CTA", "IND_DC"), "I355", r.linha()));
    }

    /** J100/J150. No leiaute 7 o J150 tem um valor só (VL_CTA/IND_DC_CTA): vira o valor final. */
    private static LinhaDemonstracao linhaPublicada(RegistroSped r, int ordem, String campoGrupo) {
        BigDecimal inicial = r.texto("VL_CTA_INI") == null ? null : r.saldo("VL_CTA_INI", "IND_DC_CTA_INI");
        BigDecimal valorFinal = r.texto("VL_CTA_FIN") != null ? r.saldo("VL_CTA_FIN", "IND_DC_CTA_FIN")
                : r.saldo("VL_CTA", "IND_DC_CTA");
        String nivel = r.texto("NIVEL_AGL");
        String numeroOrdem = r.texto("NU_ORDEM");
        return new LinhaDemonstracao(r.texto("COD_AGL"), descricao(r.texto("DESCR_COD_AGL")),
                nivel == null ? null : Integer.valueOf(nivel), r.texto("COD_AGL_SUP"),
                numeroOrdem == null ? ordem : Integer.parseInt(numeroOrdem), "T".equals(r.texto("IND_COD_AGL")),
                r.texto(campoGrupo), inicial, valorFinal, r.registro(), r.linha(), false, null);
    }

    private static String descricao(String texto) {
        String d = texto == null ? "(sem descrição)" : texto;
        return d.length() > 500 ? d.substring(0, 500) : d;
    }

    @Override
    protected void validar() {
        somasPorPeriodo.forEach((periodo, somas) -> {
            BigDecimal debitos = (BigDecimal) somas[0];
            BigDecimal creditos = (BigDecimal) somas[1];
            if (debitos.subtract(creditos).abs().compareTo(TOLERANCIA) > 0) {
                contexto.registrar(Severidade.ERRO, "I150", (Long) somas[2], "DEBITO_DIFERENTE_CREDITO",
                        "No período que termina em " + periodo + ", a soma dos débitos (" + debitos.toPlainString()
                                + ") é diferente da soma dos créditos (" + creditos.toPlainString() + ").");
            }
        });
        validarBalancoDosSaldos();
        validarBalancoPublicado();
        validarMapeamentoReferencial();
    }

    /** Ativo + passivo + PL + resultado ainda não encerrado = 0 nos saldos finais do exercício (devedor +, credor −). */
    private void validarBalancoDosSaldos() {
        if (finalPorNatureza.isEmpty()) {
            return;
        }
        BigDecimal ativo = finalPorNatureza.getOrDefault("01", BigDecimal.ZERO);
        BigDecimal passivoPl = finalPorNatureza.getOrDefault("02", BigDecimal.ZERO)
                .add(finalPorNatureza.getOrDefault("03", BigDecimal.ZERO))
                .add(finalPorNatureza.getOrDefault("04", BigDecimal.ZERO));
        if (ativo.add(passivoPl).abs().compareTo(TOLERANCIA) > 0) {
            contexto.registrar(Severidade.ERRO, "I155", null, "BALANCO_NAO_FECHA_SALDOS", "Nos saldos de " + fim
                    + ", o ativo (" + ativo.toPlainString() + ") não é igual ao passivo + PL + resultado ("
                    + passivoPl.negate().toPlainString() + ").");
        }
    }

    /** No J100, a soma das linhas de nível mais alto do ativo e do passivo + PL tem que zerar (A devedor, P credor). */
    private void validarBalancoPublicado() {
        if (balanco.isEmpty()) {
            return;
        }
        int topo = balanco.stream().filter(l -> l.nivel() != null).mapToInt(LinhaDemonstracao::nivel).min().orElse(1);
        BigDecimal ativo = somaTopo("A", topo);
        BigDecimal passivo = somaTopo("P", topo);
        if (ativo.add(passivo).abs().compareTo(TOLERANCIA) > 0) {
            contexto.registrar(Severidade.ERRO, "J100", balanco.get(0).linha(), "BALANCO_NAO_FECHA_J100",
                    "No balanço publicado (J100), o ativo (" + ativo.toPlainString() + ") não é igual ao passivo + PL ("
                            + passivo.negate().toPlainString() + ").");
        }
    }

    private BigDecimal somaTopo(String grupo, int topo) {
        return balanco.stream().filter(l -> grupo.equals(l.grupo()) && l.nivel() != null && l.nivel() == topo)
                .map(l -> valor(l.valorFinal())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validarMapeamentoReferencial() {
        if (planoReferencial == null) {
            contexto.registrar(Severidade.ALERTA, "0000", linha0000, "SEM_PLANO_REFERENCIAL", "A ECD não informa o plano "
                    + "referencial (COD_PLAN_REF): as demonstrações referenciais não podem ser montadas.");
            return;
        }
        List<String> semMapeamento = contas.stream()
                .filter(c -> !c.sintetica && c.referencial == null && Set.of("01", "02", "03", "04").contains(c.natureza))
                .map(c -> c.codigo).distinct().toList();
        if (!semMapeamento.isEmpty()) {
            contexto.registrar(Severidade.ALERTA, "I051", null, "CONTA_SEM_I051", semMapeamento.size()
                    + " conta(s) analítica(s) sem mapeamento referencial (I051) ficam fora do referencial: "
                    + String.join(", ", semMapeamento.subList(0, Math.min(30, semMapeamento.size())))
                    + (semMapeamento.size() > 30 ? " e outras." : "."));
        }
    }

    @Override
    protected void gravar() {
        LocalDate fimPublicada = fimDemonstracao != null ? fimDemonstracao : fim;
        if (!balanco.isEmpty()) {
            long id = repositorio.inserirDemonstracao(escrituracaoId, "BP", "PUBLICADA", inicioDemonstracao, fimPublicada, null);
            repositorio.inserirLinhas(id, balanco);
        }
        if (!resultado.isEmpty()) {
            long id = repositorio.inserirDemonstracao(escrituracaoId, "DRE", "PUBLICADA", inicioDemonstracao, fimPublicada, null);
            repositorio.inserirLinhas(id, resultado);
        }
    }

    private static BigDecimal valor(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** I050 (+ I051) em memória até a gravação do plano. */
    private static final class ContaLida {
        final String codigo;
        final String superior;
        final Integer nivel;
        final String natureza;
        final boolean sintetica;
        final String nome;
        final long linha;
        String referencial;

        ContaLida(RegistroSped r) {
            codigo = r.texto("COD_CTA");
            if (codigo == null) {
                throw new FalhaImportacao("I050_SEM_CODIGO", "Conta sem código no plano de contas.", "I050", r.linha());
            }
            superior = r.texto("COD_CTA_SUP");
            String n = r.texto("NIVEL");
            nivel = n == null ? null : Integer.valueOf(n);
            natureza = r.texto("COD_NAT");
            sintetica = "S".equals(r.texto("IND_CTA"));
            String cta = r.texto("CTA");
            nome = cta == null ? codigo : (cta.length() > 500 ? cta.substring(0, 500) : cta);
            linha = r.linha();
        }

        Conta paraGravar() {
            return new Conta(codigo, superior, nivel, natureza, sintetica, nome, referencial, "I050", linha);
        }
    }
}
