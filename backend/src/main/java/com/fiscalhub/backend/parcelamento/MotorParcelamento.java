package com.fiscalhub.backend.parcelamento;

import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.entity.enums.TipoIndice;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.parcelamento.RegrasParcelamento.Correcao;
import com.fiscalhub.backend.parcelamento.RegrasParcelamento.Fonte;
import com.fiscalhub.backend.parcelamento.RegrasParcelamento.RegraParcelamento;
import com.fiscalhub.backend.parcelamento.RegrasParcelamento.Tipo;
import com.fiscalhub.backend.service.IndiceMonetarioService;
import com.fiscalhub.backend.situacao.AcrescimosMoratorios;
import com.fiscalhub.backend.situacao.AcrescimosMoratorios.Acrescimos;
import com.fiscalhub.backend.situacao.SituacaoFiscalService.OrigemValor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Motor único de parcelamento e transação: as diferenças entre órgãos e modalidades estão na regra (dado), não em
 * classes. Consolida cada débito com o seu vencimento (acréscimos só pelo {@link AcrescimosMoratorios}), checa a
 * elegibilidade débito × modalidade, aplica desconto, entrada, parcela mínima e garantia da regra e monta o cronograma
 * mês a mês com a correção da regra: SELIC carregada (real) ou, para mês ainda sem SELIC a partir do mês da data-base,
 * a taxa projetada (rotulada). Mês anterior à data-base sem SELIC é erro, nunca estimativa.
 */
@Component
public class MotorParcelamento {

    static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Set<TipoTributo> PREVIDENCIARIOS = Set.of(TipoTributo.INSS, TipoTributo.TERCEIROS);
    public static final String ROTULO_PROJECAO = "projeção — SELIC futura desconhecida";
    public static final String ROTULO_REVISAO = "se a revisão da CAPAG for deferida";

    /**
     * Débito como importado. Inscrito (PGFN, PGE_PR): principal e acréscimos do documento na posição; senão, principal e
     * vencimento. negociacao: parcelamento ou transação em curso (como no documento). saldoParcelamento: saldo do
     * parcelamento em curso (base do reparcelamento).
     */
    public record DebitoSimulado(long id, OrgaoCredor orgao, TipoTributo tributo, String descricao, LocalDate vencimento,
                                 BigDecimal principal, BigDecimal multa, BigDecimal juros, BigDecimal encargo, BigDecimal total,
                                 LocalDate posicao, String inscricao, LocalDate dataInscricao, String negociacao,
                                 BigDecimal saldoParcelamento, boolean historicoReparcelamento, boolean giaSt, OrigemValor origem) {
        boolean inscrito() {
            return orgao == OrgaoCredor.PGFN || orgao == OrgaoCredor.PGE_PR;
        }
    }

    /**
     * parcelas: nulo = o máximo que a regra e a parcela mínima permitem. selicProjetada: % a.m. (nulo = média das
     * últimas 12 SELIC carregadas). parcelaSustentavel: da análise gerencial (nulo = sem selo).
     */
    public record Pedido(LocalDate dataBase, Integer parcelas, boolean pessoaFisica, String capagOficial, String capagEstimada,
                         BigDecimal selicProjetada, BigDecimal parcelaSustentavel, boolean reparcelamento) {
    }

    public record Etapa(String descricao, String formula, BigDecimal valor, String unidade, String fundamento) {
    }

    public record Consolidado(long debitoId, String descricao, OrgaoCredor orgao, LocalDate vencimento, BigDecimal principal,
                              BigDecimal percentualMulta, BigDecimal multa, BigDecimal percentualJuros, BigDecimal juros,
                              BigDecimal encargo, BigDecimal total, BigDecimal desconto, String posicao, String fundamento,
                              OrigemValor origem) {
    }

    public record Elegibilidade(long debitoId, String descricao, boolean elegivel, List<String> motivos) {
    }

    public record Desconto(BigDecimal valor, String classe, BigDecimal limiteAcrescimos, BigDecimal limiteTotal, String regra) {
    }

    /** nominal: sem correção; valor: com a correção (real ou projetada). tipo: ENTRADA | PARCELA. */
    public record Parcela(int numero, String tipo, LocalDate vencimento, BigDecimal nominal, BigDecimal percentualCorrecao,
                          BigDecimal valor, boolean projetada, BigDecimal saldoNominal, BigDecimal saldoProjetado) {
    }

    public record Selo(boolean cabe, BigDecimal maiorParcela, BigDecimal parcelaSustentavel, BigDecimal percentualComprometido,
                       String texto) {
    }

    public record FonteUsada(String chave, String titulo, String url, LocalDate data, String sha256, boolean secundaria,
                             String observacao) {
    }

    /**
     * Resultado de uma modalidade num cenário (OFICIAL, SE_REVISAO_DEFERIDA, A_VISTA, REPARCELAMENTO, BASE). bloqueio:
     * por que o resultado não vale como proposta (insumo oficial faltando); avisos: o que considerar.
     */
    public record ResultadoModalidade(String chave, String versaoRegra, String nome, Tipo tipo, OrgaoCredor orgao, String cenario,
                                      String cenarioRotulo, boolean elegivel, String bloqueio, List<String> avisos,
                                      List<Elegibilidade> elegibilidade, List<Consolidado> debitos, BigDecimal totalConsolidado,
                                      Desconto desconto, BigDecimal entrada, int prestacoesEntrada, int parcelas,
                                      BigDecimal valorParcela, String indice, BigDecimal selicProjetada, String selicProjetadaOrigem,
                                      List<Parcela> cronograma, BigDecimal custoNominal, BigDecimal custoProjetado,
                                      BigDecimal economiaVsAVista, Selo selo, List<Etapa> memoria, String fundamento,
                                      String rescisao, String garantia, String nota, boolean fonteSecundaria,
                                      List<FonteUsada> fontes) {
    }

    private final AcrescimosMoratorios acrescimos;
    private final IndiceMonetarioService indices;
    private final RegrasParcelamento regras;

    public MotorParcelamento(AcrescimosMoratorios acrescimos, IndiceMonetarioService indices, RegrasParcelamento regras) {
        this.acrescimos = acrescimos;
        this.indices = indices;
        this.regras = regras;
    }

    public RegrasParcelamento regras() {
        return regras;
    }

    /** Todas as modalidades da data-base para os débitos, com os cenários de cada uma. */
    public List<ResultadoModalidade> simular(List<DebitoSimulado> debitos, Pedido pedido) {
        List<ResultadoModalidade> out = new ArrayList<>();
        for (OrgaoCredor orgao : debitos.stream().map(DebitoSimulado::orgao).distinct().toList()) {
            List<DebitoSimulado> doOrgao = debitos.stream().filter(d -> d.orgao() == orgao).toList();
            out.add(aVista(doOrgao, pedido));
        }
        for (RegraParcelamento r : regras.modalidades(pedido.dataBase())) {
            List<DebitoSimulado> doOrgao = debitos.stream().filter(d -> r.orgaosDebito().contains(d.orgao())).toList();
            if (doOrgao.isEmpty()) {
                continue;
            }
            if (r.tipo() == Tipo.PROGRAMA || !r.vigente(pedido.dataBase())) {
                out.add(inelegivel(r, doOrgao, r.encerrada() != null ? r.encerrada()
                        : "Fora da vigência: " + periodo(r) + "."));
                continue;
            }
            if (r.tipo() == Tipo.TRANSACAO) {
                out.add(calcular(r, doOrgao, pedido, "OFICIAL", pedido.capagOficial(), false, false));
                out.add(calcular(r, doOrgao, pedido, "A_VISTA", pedido.capagOficial(), true, false));
                if (pedido.capagEstimada() != null && !pedido.capagEstimada().equals(pedido.capagOficial())) {
                    out.add(calcular(r, doOrgao, pedido, "SE_REVISAO_DEFERIDA", pedido.capagEstimada(), false, false));
                }
            } else {
                out.add(calcular(r, doOrgao, pedido, "OFICIAL", null, false, false));
                if (pedido.reparcelamento() && doOrgao.stream().anyMatch(d -> d.negociacao() != null)) {
                    out.add(calcular(r, doOrgao, pedido, "REPARCELAMENTO", null, false, true));
                }
            }
        }
        return out;
    }

    /** Uma modalidade (os testes do motor usam direto). */
    public ResultadoModalidade calcular(String chave, List<DebitoSimulado> debitos, Pedido pedido) {
        return simular(debitos, pedido).stream().filter(m -> m.chave().equals(chave) && !"A_VISTA".equals(m.cenario()))
                .findFirst().orElseThrow();
    }

    // --------------------------------------------------------------- à vista (base da comparação)

    private ResultadoModalidade aVista(List<DebitoSimulado> debitos, Pedido pedido) {
        OrgaoCredor orgao = debitos.getFirst().orgao();
        List<Consolidado> cons = new ArrayList<>();
        List<Elegibilidade> eleg = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        List<Etapa> memoria = new ArrayList<>();
        for (DebitoSimulado d : debitos) {
            List<String> motivos = new ArrayList<>();
            Consolidado c = consolidar(d, pedido.dataBase(), false, null, motivos, avisos, memoria);
            eleg.add(new Elegibilidade(d.id(), d.descricao(), c != null, motivos));
            if (c != null) {
                cons.add(c);
            }
        }
        BigDecimal total = soma(cons.stream().map(Consolidado::total).toList());
        String chave = "A_VISTA_" + orgao;
        memoria.add(new Etapa("Pagamento integral na data-base", "soma dos débitos consolidados", total, "R$",
                "Sem programa: principal e acréscimos até " + pedido.dataBase().format(DATA)));
        List<Parcela> cron = cons.isEmpty() ? List.of()
                : List.of(new Parcela(1, "PARCELA", pedido.dataBase(), total, BigDecimal.ZERO, total, false, BigDecimal.ZERO, BigDecimal.ZERO));
        return new ResultadoModalidade(chave, regras.versao(), "À vista, sem programa — " + orgao, null, orgao, "BASE",
                "pagamento integral na data-base", !cons.isEmpty(), null, avisos, eleg, cons, total, null, BigDecimal.ZERO, 0,
                cons.isEmpty() ? 0 : 1, total, "SELIC", null, null, cron, total, total, BigDecimal.ZERO,
                selo(cron, pedido.parcelaSustentavel()), memoria, regras.acrescimo(orgao, pedido.dataBase()).fundamento(), null,
                null, null, false, List.of());
    }

    // --------------------------------------------------------------- modalidade

    private ResultadoModalidade inelegivel(RegraParcelamento r, List<DebitoSimulado> debitos, String motivo) {
        List<Elegibilidade> eleg = debitos.stream().map(d -> new Elegibilidade(d.id(), d.descricao(), false, List.of(motivo))).toList();
        return new ResultadoModalidade(r.chave(), r.versao(), r.nome(), r.tipo(), r.orgao(), "OFICIAL", null, false, motivo,
                List.of(), eleg, List.of(), BigDecimal.ZERO, null, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, "SELIC", null, null,
                List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, List.of(), r.fundamento(), r.rescisao(),
                r.garantia(), r.nota(), fontes(r).stream().anyMatch(FonteUsada::secundaria), fontes(r));
    }

    private ResultadoModalidade calcular(RegraParcelamento r, List<DebitoSimulado> debitos, Pedido pedido, String cenario,
                                         String classe, boolean aVista, boolean reparcelamento) {
        LocalDate base = pedido.dataBase();
        YearMonth mesBase = YearMonth.from(base);
        List<String> avisos = new ArrayList<>();
        List<Etapa> memoria = new ArrayList<>();
        String bloqueio = null;
        String rotulo = switch (cenario) {
            case "SE_REVISAO_DEFERIDA" -> ROTULO_REVISAO + " (CAPAG estimada " + classe + ")";
            case "A_VISTA" -> "à vista";
            case "REPARCELAMENTO" -> "reparcelamento";
            default -> r.tipo() == Tipo.TRANSACAO ? "CAPAG oficial " + (classe == null ? "não importada" : classe) : null;
        };
        if (r.tipo() == Tipo.TRANSACAO && classe == null) {
            bloqueio = "CAPAG oficial não importada: o desconto da transação depende da classe (Portaria PGFN 6.757/2022, arts. 21 e 24). Importe a tela da CAPAG do Regularize.";
        }
        BigDecimal upf = null;
        if (r.parcelaMinimaUpf() != null || r.valorMinimoUpf() != null || r.garantiaAcimaUpf() != null) {
            upf = regras.upf(mesBase).map(RegrasParcelamento.Upf::valor).orElse(null);
            if (upf == null) {
                bloqueio = "UPF/PR de " + mesBase + " sem valor oficial importado: parcela mínima (" + texto(r.parcelaMinimaUpf())
                        + " UPF/PR), valor mínimo (" + texto(r.valorMinimoUpf()) + " UPF/PR) e garantia (" + texto(r.garantiaAcimaUpf())
                        + " UPF/PR) não verificados (RICMS/PR, arts. 82, §7º, e 83, §1º). Inclua a resolução da SEFA-PR do mês em parcelamento/upf-pr.csv.";
            }
        }

        // ---- elegibilidade e consolidação por débito
        List<Elegibilidade> eleg = new ArrayList<>();
        List<Consolidado> cons = new ArrayList<>();
        List<DebitoSimulado> usados = new ArrayList<>();
        for (DebitoSimulado d : debitos) {
            List<String> motivos = motivos(r, d, base, reparcelamento);
            Consolidado c = motivos.isEmpty() ? consolidar(d, base, r.orgao() == OrgaoCredor.SEFA_PR, reparcelamento ? r : null,
                    motivos, avisos, memoria) : null;
            eleg.add(new Elegibilidade(d.id(), d.descricao(), c != null, motivos));
            if (c != null) {
                cons.add(c);
                usados.add(d);
            }
        }
        BigDecimal total = soma(cons.stream().map(Consolidado::total).toList());
        if (cons.isEmpty()) {
            return new ResultadoModalidade(r.chave(), r.versao(), r.nome(), r.tipo(), r.orgao(), cenario, rotulo, false,
                    bloqueio != null ? bloqueio : "Nenhum débito elegível.", avisos, eleg, List.of(), BigDecimal.ZERO, null,
                    BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, "SELIC", null, null, List.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, null, memoria, r.fundamento(), r.rescisao(), r.garantia(), r.nota(),
                    fontes(r).stream().anyMatch(FonteUsada::secundaria), fontes(r));
        }
        boolean elegivel = true;
        if (r.debitoMaximo() != null && total.compareTo(r.debitoMaximo()) > 0) {
            elegivel = false;
            avisos.add("Total de " + moeda(total) + " acima do limite de " + moeda(r.debitoMaximo()) + " por sujeito passivo.");
        }
        if (r.valorMinimoUpf() != null && upf != null && total.compareTo(r.valorMinimoUpf().multiply(upf)) < 0) {
            elegivel = false;
            avisos.add("Valor a parcelar abaixo de " + texto(r.valorMinimoUpf()) + " UPF/PR (" + moeda(r.valorMinimoUpf().multiply(upf)) + ").");
        }

        // ---- desconto (transação): máximo do edital, por inscrição, nunca sobre o principal
        Desconto desconto = null;
        BigDecimal valorDesconto = BigDecimal.ZERO;
        boolean comDesconto = r.classesComDesconto() != null && classe != null && r.classesComDesconto().contains(classe);
        if (r.tipo() == Tipo.TRANSACAO) {
            List<Consolidado> comDesc = new ArrayList<>();
            for (Consolidado c : cons) {
                BigDecimal d = BigDecimal.ZERO;
                if (comDesconto) {
                    BigDecimal acresc = c.total().subtract(c.principal());
                    d = pct(acresc, r.descontoAcrescimosMax()).min(pct(c.total(), r.descontoTotalMax())).min(acresc);
                }
                valorDesconto = valorDesconto.add(d);
                comDesc.add(new Consolidado(c.debitoId(), c.descricao(), c.orgao(), c.vencimento(), c.principal(), c.percentualMulta(),
                        c.multa(), c.percentualJuros(), c.juros(), c.encargo(), c.total(), d, c.posicao(), c.fundamento(), c.origem()));
            }
            cons = comDesc;
            String regraDesc = comDesconto
                    ? "até " + texto(r.descontoAcrescimosMax()) + "% de juros, multas e encargos, limitado a " + texto(r.descontoTotalMax())
                    + "% do total de cada inscrição; vedado sobre o principal (Lei 13.988/2020, art. 11, §2º). Máximo do edital: o efetivo é graduado pela PGFN"
                    : "sem desconto: classe " + (classe == null ? "—" : classe) + " (desconto só para " + String.join(" e ", r.classesComDesconto())
                    + ": capacidade insuficiente, Portaria PGFN 6.757/2022, arts. 21 e 24)";
            desconto = new Desconto(valorDesconto, classe, r.descontoAcrescimosMax(), r.descontoTotalMax(), regraDesc);
            memoria.add(new Etapa("Desconto da transação", regraDesc, valorDesconto.negate(), "R$", r.fundamento()));
        }

        // ---- prazo máximo
        int prazo = r.prazoMaximo();
        List<String> limites = new ArrayList<>();
        if (r.tipo() == Tipo.TRANSACAO && !comDesconto && r.prazoMaximoSemDesconto() != null) {
            prazo = Math.min(prazo, r.prazoMaximoSemDesconto());
            limites.add(r.prazoMaximoSemDesconto() + " meses sem desconto");
        }
        if (r.prazoMaximoPrevidenciario() != null && usados.stream().anyMatch(d -> PREVIDENCIARIOS.contains(d.tributo()))) {
            prazo = Math.min(prazo, r.prazoMaximoPrevidenciario());
            limites.add(r.prazoMaximoPrevidenciario() + " para contribuições previdenciárias (CF, art. 195, §11)");
        }
        if (r.prazoMaximoEfd() != null && usados.stream().anyMatch(d -> !d.inscrito())) {
            prazo = Math.min(prazo, r.prazoMaximoEfd());
            limites.add(r.prazoMaximoEfd() + " para imposto declarado na EFD (RICMS/PR, art. 81, §8º)");
        }

        // ---- entrada
        BigDecimal entrada = BigDecimal.ZERO;
        int prestacoesEntrada = 0;
        BigDecimal liquido = total.subtract(valorDesconto);
        if (aVista) {
            prazo = 1;
        } else if (r.entradaPercentual() != null) {
            entrada = pct(total, r.entradaPercentual());
            prestacoesEntrada = Math.min(r.entradaParcelasMax(), Math.max(1, prazo - 1));
            memoria.add(new Etapa("Entrada", texto(r.entradaPercentual()) + "% do total consolidado em até " + r.entradaParcelasMax()
                    + " prestações", entrada, "R$", r.fundamento()));
        } else if (reparcelamento && r.reparcelamentoPercentual() != null) {
            boolean historico = usados.stream().anyMatch(DebitoSimulado::historicoReparcelamento);
            BigDecimal p = historico ? r.reparcelamentoPercentualHistorico() : r.reparcelamentoPercentual();
            entrada = pct(liquido, p);
            prestacoesEntrada = 1;
            memoria.add(new Etapa("1ª prestação do reparcelamento", texto(p) + "% do total consolidado"
                    + (historico ? " (débito com histórico de reparcelamento)" : " (débito com histórico de parcelamento)"), entrada, "R$", r.fundamento()));
        }

        // ---- parcelas e parcela mínima
        int parcelasSaldo = aVista ? 1 : pedido.parcelas() != null ? Math.min(pedido.parcelas(), prazo) - prestacoesEntrada
                : prazo - prestacoesEntrada;
        parcelasSaldo = Math.max(1, parcelasSaldo);
        if (pedido.parcelas() != null && pedido.parcelas() > prazo) {
            avisos.add("Prazo pedido de " + pedido.parcelas() + " acima do máximo de " + prazo + (limites.isEmpty() ? "" : " (" + String.join("; ", limites) + ")") + ": usado o máximo.");
        }
        BigDecimal saldo = liquido.subtract(entrada);
        BigDecimal minimo = minimo(r, pedido.pessoaFisica(), upf);
        BigDecimal valorParcela = saldo.divide(BigDecimal.valueOf(parcelasSaldo), 2, RoundingMode.HALF_UP);
        if (minimo != null && !aVista && valorParcela.compareTo(minimo) < 0) {
            int ajustadas = Math.max(1, saldo.divide(minimo, 0, RoundingMode.FLOOR).intValue());
            if (ajustadas < parcelasSaldo) {
                if (pedido.parcelas() != null) {
                    avisos.add("Parcelas reduzidas de " + parcelasSaldo + " para " + ajustadas + " pela parcela mínima de " + moeda(minimo) + ".");
                }
                parcelasSaldo = ajustadas;
                valorParcela = saldo.divide(BigDecimal.valueOf(parcelasSaldo), 2, RoundingMode.HALF_UP);
            }
        }
        // PR: reparcelamento entre 37 e 60 parcelas exige 6 parcelas na assinatura do TAP (RICMS/PR, art. 84, §3º)
        if (reparcelamento && r.reparcelamentoParcelasEntrada() != null && parcelasSaldo > r.reparcelamentoAcimaDe()) {
            int n = r.reparcelamentoParcelasEntrada();
            entrada = valorParcela.multiply(BigDecimal.valueOf(n));
            prestacoesEntrada = 1; // as 6 parcelas são pagas juntas, na assinatura
            parcelasSaldo -= n;
            saldo = saldo.subtract(entrada);
            memoria.add(new Etapa("Recolhimento na assinatura do novo TAP", n + " parcelas (prazo acima de "
                    + r.reparcelamentoAcimaDe() + ")", entrada, "R$", "RICMS/PR, art. 84, §3º"));
            avisos.add("Reparcelamento no PR só depois da rescisão do parcelamento e da inscrição do saldo (RICMS/PR, art. 84, §3º).");
        }
        memoria.add(new Etapa("Valor de cada parcela", "saldo ÷ " + parcelasSaldo + (minimo == null ? "" : "; mínimo " + moeda(minimo)),
                valorParcela, "R$", r.fundamento()));

        // ---- garantia
        if (r.garantiaAcima() != null && total.compareTo(r.garantiaAcima()) > 0) {
            avisos.add("Exige garantia: " + r.garantia());
        }
        if (r.garantiaAcimaUpf() != null && upf != null && total.compareTo(r.garantiaAcimaUpf().multiply(upf)) > 0
                && prestacoesEntrada + parcelasSaldo > r.garantiaPrazoAcima()) {
            avisos.add("Exige garantia: " + r.garantia());
        }
        if (r.nota() != null) {
            avisos.add(r.nota());
        }

        // ---- cronograma
        Projecao projecao = new Projecao(mesBase, pedido.selicProjetada());
        List<Parcela> cron = cronograma(r, base, entrada, prestacoesEntrada, saldo, valorParcela, parcelasSaldo,
                fatorImpostoMulta(cons), projecao, aVista);
        BigDecimal custoNominal = soma(cron.stream().map(Parcela::nominal).toList());
        BigDecimal custoProjetado = soma(cron.stream().map(Parcela::valor).toList());
        memoria.add(new Etapa("Correção das parcelas", correcaoTexto(r.correcao()), null, null, r.fundamento()));
        if (projecao.usada) {
            avisos.add("Parcelas futuras com " + ROTULO_PROJECAO + ": " + texto(projecao.taxa) + "% a.m. (" + projecao.origem + ").");
            memoria.add(new Etapa("SELIC projetada", projecao.origem, projecao.taxa, "% a.m.", ROTULO_PROJECAO));
        }
        BigDecimal aVistaSemPrograma = total;
        memoria.add(new Etapa("Custo total", "soma das prestações (nominal " + moeda(custoNominal) + ")", custoProjetado, "R$", null));
        if (fontes(r).stream().anyMatch(FonteUsada::secundaria)) {
            avisos.add("Regra com fonte secundária: o texto oficial não pôde ser baixado (ver fontes).");
        }
        return new ResultadoModalidade(r.chave(), r.versao(), r.nome(), r.tipo(), r.orgao(), cenario, rotulo, elegivel && bloqueio == null,
                bloqueio, avisos, eleg, cons, total, desconto, entrada, prestacoesEntrada, parcelasSaldo, valorParcela, "SELIC",
                projecao.usada ? projecao.taxa : null, projecao.usada ? projecao.origem : null, cron, custoNominal, custoProjetado,
                aVistaSemPrograma.subtract(custoProjetado), selo(cron, pedido.parcelaSustentavel()), memoria, r.fundamento(),
                r.rescisao(), r.garantia(), r.nota(), fontes(r).stream().anyMatch(FonteUsada::secundaria), fontes(r));
    }

    private List<String> motivos(RegraParcelamento r, DebitoSimulado d, LocalDate base, boolean reparcelamento) {
        List<String> m = new ArrayList<>();
        if (r.vedaTributos().contains(d.tributo())) {
            m.add("Tributo vedado nesta modalidade: " + d.tributo() + " (" + r.fundamento().split(":")[0] + ").");
        }
        if (d.negociacao() != null) {
            if (r.tipo() == Tipo.TRANSACAO) {
                m.add("Já negociado (" + d.negociacao() + "): a adesão exige a desistência do acordo em curso (edital, art. 13, §2º).");
            } else if (r.vedaNegociado() && !reparcelamento) {
                m.add("Já negociado (" + d.negociacao() + "): só por reparcelamento.");
            }
        } else if (reparcelamento) {
            m.add("Sem parcelamento anterior: não é reparcelamento.");
        }
        if (r.vedaGiaSt() && d.giaSt()) {
            m.add("ICMS declarado em GIA-ST ou DeSTDA: vedado o parcelamento (RICMS/PR, art. 81, §10).");
        }
        if (r.dataCorteInscricao() != null) {
            if (d.dataInscricao() == null) {
                m.add("Data de inscrição não importada: não dá para conferir a data de corte (" + r.dataCorteInscricao().format(DATA) + ").");
            } else if (d.dataInscricao().isAfter(r.dataCorteInscricao())) {
                m.add("Inscrita em " + d.dataInscricao().format(DATA) + ", depois da data de corte de " + r.dataCorteInscricao().format(DATA) + ".");
            }
        }
        if (r.tipo() == Tipo.TRANSACAO && r.adesaoInicio() != null && base.isBefore(r.adesaoInicio())) {
            m.add("Adesão só a partir de " + r.adesaoInicio().format(DATA) + ".");
        }
        return m;
    }

    /** Débito na data-base: calculado (vencimento e principal) ou na posição do documento (inscrito). */
    private Consolidado consolidar(DebitoSimulado d, LocalDate base, boolean parcelamentoPr, RegraParcelamento reparcelamento,
                                   List<String> motivos, List<String> avisos, List<Etapa> memoria) {
        if (reparcelamento != null && d.saldoParcelamento() != null) {
            memoria.add(new Etapa(d.descricao() + ": saldo do parcelamento", "como no documento", d.saldoParcelamento(), "R$", reparcelamento.fundamento()));
            return new Consolidado(d.id(), d.descricao(), d.orgao(), d.vencimento(), d.saldoParcelamento(), null, null, null, null,
                    BigDecimal.ZERO, d.saldoParcelamento(), BigDecimal.ZERO, "saldo do parcelamento em " + data(d.posicao()),
                    "saldo devedor como no documento", d.origem());
        }
        if (d.inscrito()) {
            if (d.principal() == null) {
                motivos.add("Inscrição sem principal no documento: importe o extrato com os valores.");
                return null;
            }
            String posicao = "posição do documento em " + data(d.posicao());
            if (d.orgao() == OrgaoCredor.PGE_PR) {
                BigDecimal total = Objects.requireNonNullElse(d.total(), d.principal());
                BigDecimal acresc = total.subtract(d.principal());
                memoria.add(new Etapa(d.descricao() + ": total da CDA", "principal " + moeda(d.principal()) + " + acréscimos da CDA "
                        + moeda(acresc), total, "R$", "valores da conta corrente (" + posicao + ")"));
                avisos.add(d.descricao() + ": " + posicao + "; a PGE/SEFA atualiza até a data do parcelamento.");
                return new Consolidado(d.id(), d.descricao(), d.orgao(), d.vencimento(), d.principal(), null, acresc, null, null,
                        BigDecimal.ZERO, total, BigDecimal.ZERO, posicao, "valores da CDA", d.origem());
            }
            BigDecimal multa = Objects.requireNonNullElse(d.multa(), BigDecimal.ZERO);
            BigDecimal juros = Objects.requireNonNullElse(d.juros(), BigDecimal.ZERO);
            BigDecimal baseEncargo = d.principal().add(multa).add(juros);
            LocalDate dataRegra = d.posicao() != null ? d.posicao() : base;
            BigDecimal encargo = acrescimos.encargo(d.orgao(), baseEncargo, dataRegra);
            String fundamento = regras.acrescimo(d.orgao(), dataRegra).fundamento();
            if (d.encargo() != null && d.encargo().compareTo(encargo) != 0) {
                avisos.add(d.descricao() + ": encargo legal do documento (" + moeda(d.encargo()) + ") difere do calculado pela regra ("
                        + moeda(encargo) + "); usado o calculado.");
            }
            BigDecimal total = baseEncargo.add(encargo);
            memoria.add(new Etapa(d.descricao() + ": principal, multa e juros", "como no documento (" + posicao + ")", baseEncargo, "R$", null));
            memoria.add(new Etapa(d.descricao() + ": encargo legal", texto(regras.acrescimo(d.orgao(), dataRegra).encargoLegal())
                    + "% × " + moeda(baseEncargo), encargo, "R$", "Decreto-Lei 1.025/1969, art. 1º"));
            avisos.add(d.descricao() + ": " + posicao + "; a PGFN atualiza os juros até a consolidação.");
            return new Consolidado(d.id(), d.descricao(), d.orgao(), d.vencimento(), d.principal(), null, multa, null, juros, encargo,
                    total, BigDecimal.ZERO, posicao, fundamento, d.origem());
        }
        if (d.vencimento() == null || d.principal() == null) {
            motivos.add("Débito sem vencimento ou principal no documento.");
            return null;
        }
        if (!base.isAfter(d.vencimento())) {
            motivos.add("Ainda não vencido em " + base.format(DATA) + ".");
            return null;
        }
        boolean emPrazo = parcelamentoPr && base.isBefore(d.vencimento().plusDays(31));
        Acrescimos a = acrescimos.calcular(d.orgao(), d.principal(), d.vencimento(), base, false, emPrazo);
        if (emPrazo) {
            avisos.add(d.descricao() + ": ainda no prazo da multa diária; parcelável com a multa do 31º dia (RICMS/PR, art. 81, §7º).");
        }
        String juros = a.selicDe() == null ? "1% no mês do pagamento" : "SELIC de " + a.selicDe() + " a " + a.selicAte() + " + 1%";
        memoria.add(new Etapa(d.descricao() + ": principal", "vencimento " + d.vencimento().format(DATA), d.principal(), "R$", null));
        memoria.add(new Etapa(d.descricao() + ": multa de mora", a.diasAtraso() + " dias de atraso → " + texto(a.percentualMulta()) + "%",
                a.multa(), "R$", a.fundamento()));
        memoria.add(new Etapa(d.descricao() + ": juros de mora", juros + " = " + texto(a.percentualJuros()) + "%", a.juros(), "R$", a.fundamento()));
        return new Consolidado(d.id(), d.descricao(), d.orgao(), d.vencimento(), d.principal(), a.percentualMulta(), a.multa(),
                a.percentualJuros(), a.juros(), BigDecimal.ZERO, d.principal().add(a.total()), BigDecimal.ZERO,
                "calculado até " + base.format(DATA), a.fundamento(), d.origem());
    }

    // --------------------------------------------------------------- cronograma

    /** SELIC por mês: carregada (real) ou, do mês da data-base em diante, projetada. */
    private final class Projecao {
        final YearMonth mesBase;
        BigDecimal taxa;
        String origem;
        boolean usada;
        Map<YearMonth, BigDecimal> carregadas;

        Projecao(YearMonth mesBase, BigDecimal taxa) {
            this.mesBase = mesBase;
            this.taxa = taxa;
            this.origem = taxa == null ? null : "parâmetro do escritório";
        }

        BigDecimal soma(YearMonth de, YearMonth ate, boolean[] projetou) {
            BigDecimal s = BigDecimal.ZERO;
            if (de.isAfter(ate)) {
                return s;
            }
            if (carregadas == null) {
                carregadas = indices.taxasCarregadas(TipoIndice.SELIC, mesBase.minusMonths(240), mesBase.plusMonths(240));
            }
            for (YearMonth m = de; !m.isAfter(ate); m = m.plusMonths(1)) {
                BigDecimal t = carregadas.get(m);
                if (t == null) {
                    if (m.isBefore(mesBase)) {
                        throw IndiceMonetarioService.indiceAusente(TipoIndice.SELIC, m);
                    }
                    t = taxaProjetada();
                    projetou[0] = true;
                    usada = true;
                }
                s = s.add(t);
            }
            return s;
        }

        private BigDecimal taxaProjetada() {
            if (taxa == null) {
                List<BigDecimal> ultimas = carregadas.entrySet().stream().filter(e -> !e.getKey().isAfter(mesBase))
                        .sorted(Map.Entry.<YearMonth, BigDecimal>comparingByKey().reversed()).limit(12).map(Map.Entry::getValue).toList();
                if (ultimas.isEmpty()) {
                    throw IndiceMonetarioService.indiceAusente(TipoIndice.SELIC, mesBase);
                }
                taxa = MotorParcelamento.soma(ultimas).divide(BigDecimal.valueOf(ultimas.size()), 4, RoundingMode.HALF_UP);
                origem = "média das " + ultimas.size() + " últimas SELIC mensais do Banco Central carregadas";
            }
            return taxa;
        }
    }

    private List<Parcela> cronograma(RegraParcelamento r, LocalDate base, BigDecimal entrada, int prestacoesEntrada, BigDecimal saldo,
                                     BigDecimal valorParcela, int parcelasSaldo, BigDecimal fatorImpostoMulta, Projecao projecao,
                                     boolean aVista) {
        YearMonth consolidacao = YearMonth.from(base);
        YearMonth primeiro = switch (r.primeiraParcela()) {
            case MES_SEGUINTE -> consolidacao.plusMonths(1);
            case MES_DA_CONSOLIDACAO, DATA_BASE -> consolidacao;
        };
        List<BigDecimal[]> nominais = new ArrayList<>(); // [valor, tipo(0 entrada,1 parcela)]
        if (prestacoesEntrada > 0) {
            BigDecimal cada = entrada.divide(BigDecimal.valueOf(prestacoesEntrada), 2, RoundingMode.HALF_UP);
            for (int i = 1; i <= prestacoesEntrada; i++) {
                nominais.add(new BigDecimal[]{i == prestacoesEntrada ? entrada.subtract(cada.multiply(BigDecimal.valueOf(i - 1))) : cada, BigDecimal.ZERO});
            }
        }
        for (int i = 1; i <= parcelasSaldo; i++) {
            nominais.add(new BigDecimal[]{i == parcelasSaldo ? saldo.subtract(valorParcela.multiply(BigDecimal.valueOf(i - 1))) : valorParcela, BigDecimal.ONE});
        }
        List<Parcela> out = new ArrayList<>();
        BigDecimal restanteNominal = soma(nominais.stream().map(n -> n[0]).toList());
        for (int k = 0; k < nominais.size(); k++) {
            YearMonth mes = primeiro.plusMonths(k);
            LocalDate venc = r.primeiraParcela() == RegrasParcelamento.PrimeiraParcela.DATA_BASE
                    ? base.plusMonths(k) : ultimoDiaUtil(mes);
            boolean[] projetou = {false};
            BigDecimal pctCorrecao;
            BigDecimal nominal = nominais.get(k)[0];
            BigDecimal valor;
            if (aVista) { // pagamento integral no mês da adesão, sem acréscimo
                pctCorrecao = BigDecimal.ZERO;
                valor = nominal;
            } else if (r.correcao() == Correcao.SELIC_ACUMULADA_MAIS_1) {
                pctCorrecao = projecao.soma(consolidacao.plusMonths(1), mes.minusMonths(1), projetou).add(BigDecimal.ONE);
                valor = nominal.multiply(BigDecimal.ONE.add(pctCorrecao.divide(CEM, 10, RoundingMode.HALF_UP))).setScale(2, RoundingMode.HALF_UP);
            } else {
                pctCorrecao = k == 0 ? BigDecimal.ZERO : projecao.soma(consolidacao.plusMonths(1), mes, projetou);
                valor = nominal.add(nominal.multiply(fatorImpostoMulta).multiply(pctCorrecao).divide(CEM, 2, RoundingMode.HALF_UP));
            }
            restanteNominal = restanteNominal.subtract(nominal);
            out.add(new Parcela(k + 1, nominais.get(k)[1].signum() == 0 ? "ENTRADA" : "PARCELA", venc, nominal, pctCorrecao, valor,
                    projetou[0], restanteNominal, BigDecimal.ZERO));
        }
        // saldo projetado: soma do que falta pagar com a correção
        BigDecimal restante = soma(out.stream().map(Parcela::valor).toList());
        List<Parcela> comSaldo = new ArrayList<>();
        for (Parcela p : out) {
            restante = restante.subtract(p.valor());
            comSaldo.add(new Parcela(p.numero(), p.tipo(), p.vencimento(), p.nominal(), p.percentualCorrecao(), p.valor(), p.projetada(),
                    p.saldoNominal(), restante));
        }
        return comSaldo;
    }

    /** ponytail: só fim de semana; feriados nacionais não descontados (o vencimento real pode ser 1 dia útil antes). */
    static LocalDate ultimoDiaUtil(YearMonth mes) {
        LocalDate d = mes.atEndOfMonth();
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.minusDays(1);
        }
        return d;
    }

    /** PR: os juros vincendos incidem sobre imposto e multa da parcela (RICMS/PR, art. 81, §3º, I). */
    private static BigDecimal fatorImpostoMulta(List<Consolidado> cons) {
        BigDecimal total = soma(cons.stream().map(Consolidado::total).toList());
        if (total.signum() == 0) {
            return BigDecimal.ONE;
        }
        BigDecimal impostoMulta = soma(cons.stream().map(c -> c.principal().add(Objects.requireNonNullElse(c.multa(), BigDecimal.ZERO))).toList());
        return impostoMulta.divide(total, 10, RoundingMode.HALF_UP);
    }

    private static String correcaoTexto(Correcao c) {
        return c == Correcao.SELIC_ACUMULADA_MAIS_1
                ? "cada prestação + SELIC acumulada do mês seguinte ao da consolidação até o mês anterior ao do pagamento + 1% no mês do pagamento"
                : "da 2ª parcela em diante, juros vincendos: soma da SELIC mensal até o vencimento sobre o imposto e a multa da parcela";
    }

    private static Selo selo(List<Parcela> cron, BigDecimal sustentavel) {
        if (sustentavel == null || sustentavel.signum() <= 0 || cron.isEmpty()) {
            return null;
        }
        BigDecimal maior = cron.stream().map(Parcela::valor).max(Comparator.naturalOrder()).orElseThrow();
        BigDecimal pct = maior.multiply(CEM).divide(sustentavel, 2, RoundingMode.HALF_UP);
        boolean cabe = maior.compareTo(sustentavel) <= 0;
        return new Selo(cabe, maior, sustentavel, pct, cabe ? "Cabe na parcela sustentável" : "Compromete " + texto(pct) + "% da parcela sustentável");
    }

    private static BigDecimal minimo(RegraParcelamento r, boolean pf, BigDecimal upf) {
        if (r.parcelaMinimaUpf() != null) {
            return upf == null ? null : r.parcelaMinimaUpf().multiply(upf).setScale(2, RoundingMode.HALF_UP);
        }
        return pf ? r.parcelaMinimaPF() : r.parcelaMinimaPJ();
    }

    private List<FonteUsada> fontes(RegraParcelamento r) {
        Map<String, FonteUsada> out = new LinkedHashMap<>();
        for (String k : r.fontes()) {
            Fonte f = regras.fonte(k);
            out.put(k, new FonteUsada(k, f.titulo(), f.url(), f.data(), f.sha256(), f.secundaria(), f.observacao()));
        }
        return List.copyOf(out.values());
    }

    private static String periodo(RegraParcelamento r) {
        return r.vigenciaInicio().format(DATA) + (r.vigenciaFim() == null ? "" : " a " + r.vigenciaFim().format(DATA));
    }

    static BigDecimal pct(BigDecimal valor, BigDecimal percentual) {
        return valor.multiply(percentual).divide(CEM, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal soma(List<BigDecimal> valores) {
        return valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String data(LocalDate d) {
        return d == null ? "—" : d.format(DATA);
    }

    private static String texto(BigDecimal v) {
        return v == null ? "—" : v.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String moeda(BigDecimal v) {
        return "R$ " + String.format(java.util.Locale.forLanguageTag("pt-BR"), "%,.2f", v);
    }
}
