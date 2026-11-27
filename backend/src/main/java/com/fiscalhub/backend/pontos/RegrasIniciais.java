package com.fiscalhub.backend.pontos;

import com.fiscalhub.backend.capacidade.DfcDiretaService;
import com.fiscalhub.backend.capacidade.MetodologiaCapacidade;
import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.entity.enums.SituacaoDebito;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.fiscal.ApuracaoService.Apuracao;
import com.fiscalhub.backend.fiscal.ApuracaoService.SaldoFiscal;
import com.fiscalhub.backend.fiscal.ConferenciaEcdEcf;
import com.fiscalhub.backend.fiscal.RepositorioApuracao.Detalhe;
import com.fiscalhub.backend.parcelamento.MotorParcelamento.ResultadoModalidade;
import com.fiscalhub.backend.parcelamento.RegrasParcelamento;
import com.fiscalhub.backend.parcelamento.SimulacaoService;
import com.fiscalhub.backend.pontos.RegraPontoAtencao.*;
import com.fiscalhub.backend.situacao.ConciliacaoService.ItemConciliacao;
import com.fiscalhub.backend.situacao.ConciliacaoService.Status;
import com.fiscalhub.backend.situacao.SituacaoFiscalService.DebitoConsolidado;
import com.fiscalhub.backend.situacao.SituacaoFiscalService.OrigemValor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * As regras iniciais (R01 a R16). Cada uma é uma classe pequena registrada como bean; nenhuma calcula: consultam o
 * {@link ContextoPontos} (conciliação, situação fiscal, saldos, capacidade, simulação, escriturações, DFC) e devolvem
 * os achados com as evidências e o link para a tela de origem. Fundamento, recomendação e limites: regras.json.
 */
final class RegrasIniciais {

    private RegrasIniciais() {
    }

    private static final Set<Status> NAO_PAGO = EnumSet.of(Status.NAO_PAGO, Status.PAGO_A_MENOR);

    // ------------------------------------------------------------------------------------------------ apoio

    static String moeda(BigDecimal v) {
        return v == null ? "—" : "R$ " + String.format(Locale.forLanguageTag("pt-BR"), "%,.2f", v);
    }

    static BigDecimal soma(Stream<BigDecimal> v) {
        return v.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static OrigemValor origem(long documentoId, String arquivo, String registro, Long linha) {
        return new OrigemValor(documentoId, arquivo, null, null, registro, linha, null);
    }

    /** Evidências de um item da conciliação: o apurado, o declarado e o pago, cada um com a origem; link para a célula. */
    static List<Evidencia> evidencias(ItemConciliacao i) {
        String link = "tributos?item=" + i.id();
        List<Evidencia> out = new ArrayList<>();
        if (i.apurado() != null) i.apurado().origens().forEach(o -> out.add(new Evidencia("APURADO", i.id(), "Apurado", i.apurado().valor(), o, link)));
        if (i.declarado() != null) i.declarado().origens().forEach(o -> out.add(new Evidencia("DECLARADO", i.id(), "Declarado", i.declarado().valor(), o, link)));
        if (i.pago() != null) i.pago().origens().forEach(o -> out.add(new Evidencia("PAGO", i.id(), "Pago", i.pago().valor(), o, link)));
        if (out.isEmpty()) out.add(new Evidencia("CONCILIACAO", i.id(), i.statusRotulo(), i.diferenca(), null, link));
        return out;
    }

    static String rotulo(ItemConciliacao i) {
        return i.tributo() + (i.uf() == null ? "" : " · " + i.uf()) + " " + String.format("%02d/%d", i.periodo().getMonthValue(), i.periodo().getYear());
    }

    static Evidencia evidencia(DebitoConsolidado d) {
        BigDecimal v = d.valorTotal() != null ? d.valorTotal() : d.valorPrincipal();
        String desc = d.orgaoCredor() + " " + d.tributo() + (d.numeroInscricao() != null ? " " + d.numeroInscricao() : d.periodo() != null ? " " + d.periodo() : "");
        return new Evidencia("DEBITO", String.valueOf(d.id()), desc, v, d.origens().isEmpty() ? null : d.origens().getFirst().origem(),
                "situacao-fiscal?debito=" + d.id());
    }

    static Evidencia evidencia(SaldoFiscal s) {
        return new Evidencia("SALDO", String.valueOf(s.id()), s.descricao() + " em " + s.dataFim(), s.saldoFinal(),
                origem(s.documentoId(), s.arquivo(), s.registro(), s.linha()), "tributos?saldo=" + s.tipo());
    }

    /** Chave estável de um débito entre reimportações (o id muda; a inscrição ou órgão/tributo/período não). */
    static String chave(DebitoConsolidado d) {
        return d.numeroInscricao() != null ? d.numeroInscricao() : d.orgaoCredor() + "|" + d.tributo() + "|" + Objects.toString(d.competencia(), d.periodo());
    }

    /** O saldo credor do ICMS próprio do mesmo período e UF, se houver (o que torna o ICMS-ST não pago um caso da R04). */
    static Optional<SaldoFiscal> saldoCredorIcms(ContextoPontos ctx, String uf, YearMonth periodo) {
        return ctx.saldos().stream().filter(s -> "SALDO_CREDOR_ICMS".equals(s.tipo()) && Objects.equals(s.uf(), uf)
                && YearMonth.from(s.dataFim()).equals(periodo) && s.saldoFinal() != null && s.saldoFinal().signum() > 0).findFirst();
    }

    static boolean semConciliacao(ContextoPontos ctx) {
        return ctx.conciliacao().itens().isEmpty();
    }

    static final String FALTA_CONCILIACAO = "falta importar o apurado (EFD, ECF), o declarado (DCTF, DCTFWeb, conta corrente do PR) ou o pago (DARF, GR-PR)";

    // ------------------------------------------------------------------------------------------------ R01 a R16

    @Component
    static class R01PagoAMenor implements RegraPontoAtencao {
        public String codigo() { return "R01"; }

        public Avaliacao avaliar(ContextoPontos ctx, Definicao def) {
            if (semConciliacao(ctx)) return Avaliacao.naoAvaliada(FALTA_CONCILIACAO);
            List<Achado> out = new ArrayList<>();
            for (ItemConciliacao i : ctx.conciliacao().itens()) {
                if (!NAO_PAGO.contains(i.status()) || (i.tributo() == TipoTributo.ICMS_ST && saldoCredorIcms(ctx, i.uf(), i.periodo()).isPresent())) {
                    continue; // ICMS-ST com saldo credor próprio: R04
                }
                BigDecimal acresc = i.acrescimos() == null ? null : i.acrescimos().total();
                BigDecimal valor = i.diferenca().add(Objects.requireNonNullElse(acresc, BigDecimal.ZERO));
                out.add(new Achado(i.id(), null, i.statusRotulo() + ": " + rotulo(i),
                        rotulo(i) + ": devido " + moeda(i.declarado() != null ? i.declarado().valor() : i.apurado() == null ? null : i.apurado().valor())
                                + ", pago " + moeda(i.pago() == null ? BigDecimal.ZERO : i.pago().valor()) + ", diferença " + moeda(i.diferenca())
                                + (acresc == null ? (i.erroAcrescimos() != null ? "; acréscimos não calculados: " + i.erroAcrescimos() : "; acréscimos estaduais: ver a simulação")
                                : "; multa e juros até hoje " + moeda(acresc)) + ".",
                        valor, evidencias(i)));
            }
            return Avaliacao.de(out);
        }
    }

    @Component
    static class R02AtrasoSemAcrescimos implements RegraPontoAtencao {
        public String codigo() { return "R02"; }

        public Avaliacao avaliar(ContextoPontos ctx, Definicao def) {
            if (semConciliacao(ctx)) return Avaliacao.naoAvaliada(FALTA_CONCILIACAO);
            return Avaliacao.de(ctx.conciliacao().itens().stream().filter(i -> i.status() == Status.PAGO_EM_ATRASO_SEM_ACRESCIMOS).map(i -> {
                BigDecimal acresc = i.acrescimos() == null ? null : i.acrescimos().total();
                return new Achado(i.id(), null, "Pago em atraso sem acréscimos: " + rotulo(i), rotulo(i) + ": vencimento " + i.vencimento()
                        + ", pago em " + (i.pago() == null ? "—" : i.pago().dataPagamento()) + " só o principal; multa "
                        + moeda(i.acrescimos() == null ? null : i.acrescimos().multa()) + " e juros "
                        + moeda(i.acrescimos() == null ? null : i.acrescimos().juros()) + " em aberto.", acresc, evidencias(i));
            }).toList());
        }
    }

    @Component
    static class R03ApuradoDeclarado implements RegraPontoAtencao {
        public String codigo() { return "R03"; }

        public Avaliacao avaliar(ContextoPontos ctx, Definicao def) {
            if (semConciliacao(ctx)) return Avaliacao.naoAvaliada(FALTA_CONCILIACAO);
            return Avaliacao.de(ctx.conciliacao().itens().stream()
                    .filter(i -> i.status() == Status.DIVERGENCIA_APURADO_DECLARADO || i.status() == Status.NAO_DECLARADO)
                    .map(i -> new Achado(i.id(), null, i.statusRotulo() + ": " + rotulo(i), rotulo(i) + ": apurado "
                            + moeda(i.apurado() == null ? null : i.apurado().valor()) + ", declarado "
                            + moeda(i.declarado() == null ? BigDecimal.ZERO : i.declarado().valor()) + ", diferença " + moeda(i.diferenca()) + ".",
                            i.diferenca().abs(), evidencias(i))).toList());
        }
    }

    @Component
    static class R04IcmsStComSaldoCredor implements RegraPontoAtencao {
        public String codigo() { return "R04"; }

        public Avaliacao avaliar(ContextoPontos ctx, Definicao def) {
            if (semConciliacao(ctx)) return Avaliacao.naoAvaliada(FALTA_CONCILIACAO);
            List<Achado> out = new ArrayList<>();
            for (ItemConciliacao i : ctx.conciliacao().itens()) {
                if (i.tributo() != TipoTributo.ICMS_ST || !NAO_PAGO.contains(i.status())) continue;
                saldoCredorIcms(ctx, i.uf(), i.periodo()).ifPresent(s -> {
                    List<Evidencia> ev = new ArrayList<>(evidencias(i));
                    ev.add(evidencia(s));
                    out.add(new Achado(i.id(), null, "ICMS-ST não recolhido com saldo credor de ICMS próprio: " + rotulo(i),
                            rotulo(i) + ": ICMS-ST de " + moeda(i.diferenca()) + " sem recolhimento, embora a apuração própria tenha saldo credor de "
                                    + moeda(s.saldoFinal()) + " — o saldo credor não quita o ST.", i.diferenca(), ev));
                });
            }
            return Avaliacao.de(out);
        }
    }

    @Component
    static class R05SaldoCredorAcumulado implements RegraPontoAtencao {
        public String codigo() { return "R05"; }

        public Avaliacao avaliar(ContextoPontos ctx, Definicao def) {
            List<SaldoFiscal> icms = ctx.saldos().stream().filter(s -> "SALDO_CREDOR_ICMS".equals(s.tipo())).toList();
            if (icms.isEmpty()) return Avaliacao.naoAvaliada("falta importar a EFD ICMS/IPI (ou o RTI do PR) com a apuração mensal");
            int n = def.inteiro("mesesConsecutivos");
            BigDecimal multiplo = def.decimal("multiploDebitoMedio");
            List<Achado> out = new ArrayList<>();
            Map<String, List<SaldoFiscal>> porUf = icms.stream().collect(Collectors.groupingBy(s -> Objects.toString(s.uf(), "—"), TreeMap::new, Collectors.toList()));
            porUf.forEach((uf, lista) -> {
                // um saldo por mês (a EFD e o RTI trazem o mesmo; vale o de maior documento, o mais recente importado)
                TreeMap<YearMonth, SaldoFiscal> porMes = new TreeMap<>();
                lista.forEach(s -> porMes.merge(YearMonth.from(s.dataFim()), s, (a, b) -> a.documentoId() >= b.documentoId() ? a : b));
                List<SaldoFiscal> ultimos = new ArrayList<>(porMes.descendingMap().values()).subList(0, Math.min(n, porMes.size()));
                boolean seguidos = ultimos.size() == n && ultimos.stream().allMatch(s -> s.saldoFinal().signum() > 0)
                        && YearMonth.from(ultimos.getFirst().dataFim()).minusMonths(n - 1).equals(YearMonth.from(ultimos.getLast().dataFim()));
                SaldoFiscal ultimo = porMes.lastEntry().getValue();
                List<BigDecimal> debitos = ctx.apuracoes().stream().filter(a -> "ICMS".equals(a.tributo()) && Objects.equals(Objects.toString(a.uf(), "—"), uf)
                        && a.valorApurado() != null).map(Apuracao::valorApurado).toList();
                BigDecimal media = debitos.isEmpty() ? null : soma(debitos.stream()).divide(BigDecimal.valueOf(debitos.size()), 2, RoundingMode.HALF_UP);
                boolean acimaDaMedia = media != null && media.signum() > 0 && ultimo.saldoFinal().compareTo(media.multiply(multiplo)) > 0;
                if (seguidos || acimaDaMedia) {
                    out.add(new Achado(uf, null, "Saldo credor de ICMS acumulado (" + uf + ")", "Saldo credor de ICMS em " + uf + ": "
                            + moeda(ultimo.saldoFinal()) + " em " + YearMonth.from(ultimo.dataFim())
                            + (seguidos ? "; saldo credor nos últimos " + n + " meses seguidos" : "")
                            + (acimaDaMedia ? "; acima de " + multiplo.stripTrailingZeros().toPlainString() + "× o débito médio mensal (" + moeda(media) + ")" : "") + ".",
                            ultimo.saldoFinal(), ultimos.stream().map(RegrasIniciais::evidencia).toList()));
                }
            });
            return Avaliacao.de(out);
        }
    }

    @Component
    static class R06Omissao implements RegraPontoAtencao {
        public String codigo() { return "R06"; }

        public Avaliacao avaliar(ContextoPontos ctx, Definicao def) {
            Severidade grave = Severidade.valueOf(String.valueOf(def.parametros().get("severidadeSeImpedeCertidao")));
            return Avaliacao.de(ctx.pendencias().stream().filter(p -> "OMISSAO_DECLARACAO".equals(p.tipo())).map(p -> new Achado(
                    p.descricao() + "|" + p.periodo(), p.impedeCertidao() ? grave : null, "Omissão de declaração: " + p.descricao(),
                    p.descricao() + (p.periodo() == null ? "" : " (" + YearMonth.from(p.periodo()) + ")") + ": "
                            + (p.impedeCertidao() ? "impede a certidão" : "não impede a certidão (só a previdenciária impede)") + ". " + p.fundamento(),
                    null, List.of(new Evidencia("PENDENCIA", String.valueOf(p.id()), p.descricao(), null, p.origem(), "situacao-fiscal?pendencia=" + p.id()))))
                    .toList());
        }
    }

    @Component
    static class R07ImpedeCertidao implements RegraPontoAtencao {
        public String codigo() { return "R07"; }

        public Avaliacao avaliar(ContextoPontos ctx, Definicao def) {
            return Avaliacao.de(ctx.debitos().stream().filter(DebitoConsolidado::impedeCertidao).map(d -> {
                Evidencia e = evidencia(d);
                return new Achado(chave(d), null, "Débito que impede a certidão: " + e.descricao(), e.descricao() + " ("
                        + Objects.toString(d.situacaoDocumento(), d.situacao().name()) + "), " + moeda(e.valor()) + ".", e.valor(), List.of(e));
            }).toList());
        }
    }

    @Component
    static class R08RevisaoCapag implements RegraPontoAtencao {
        public String codigo() { return "R08"; }

        public Avaliacao avaliar(ContextoPontos ctx, Definicao def) {
            var r = ctx.capacidade();
            if (r.capagOficial() == null) return Avaliacao.naoAvaliada("falta importar a tela da CAPAG do Regularize");
            if (r.capagEstimada() == null) return Avaliacao.naoAvaliada("falta importar as ECD (demonstrações e DFC direta) para a CAPAG estimada");
            if (!r.revisaoVantajosa()) return Avaliacao.de(List.of());
            return Avaliacao.de(List.of(new Achado("capag", null, "Revisão da CAPAG potencialmente vantajosa",
                    "CAPAG oficial " + r.capagOficial() + " e estimada " + r.capagEstimada() + " (estimativa do consultor): a revisão pode melhorar o desconto da transação.",
                    null, List.of(new Evidencia("CAPACIDADE", "capag", "CAPAG oficial " + r.capagOficial() + " × estimada " + r.capagEstimada(), null, null, "capacidade")))));
        }
    }

    @Component
    static class R09TransacaoNaoNegociada implements RegraPontoAtencao {
        public String codigo() { return "R09"; }

        public Avaliacao avaliar(ContextoPontos ctx, Definicao def) {
            List<DebitoConsolidado> livres = ctx.debitos().stream().filter(d -> d.orgaoCredor() == OrgaoCredor.PGFN && d.negociacao() == null
                    && d.valorPrincipal() != null).toList();
            if (livres.isEmpty()) return Avaliacao.de(List.of());
            var s = ctx.servicos().simulacoes().simular(ctx.clienteId(), new SimulacaoService.PedidoParcelamento(
                    livres.stream().map(DebitoConsolidado::id).toList(), LocalDate.now(), null, false), ctx.usuario());
            if (s.insumos().capagOficial() == null) return Avaliacao.naoAvaliada("falta importar a tela da CAPAG do Regularize (o desconto depende da classe oficial)");
            List<Achado> out = new ArrayList<>();
            for (ResultadoModalidade m : s.modalidades()) {
                if (m.tipo() != RegrasParcelamento.Tipo.TRANSACAO || !"OFICIAL".equals(m.cenario()) || !m.elegivel()
                        || m.desconto() == null || m.desconto().valor().signum() <= 0) continue;
                List<Evidencia> ev = new ArrayList<>(livres.stream().filter(d -> m.debitos().stream().anyMatch(c -> c.debitoId() == d.id()))
                        .map(RegrasIniciais::evidencia).toList());
                ev.add(new Evidencia("SIMULACAO", m.versaoRegra(), m.nome(), m.desconto().valor(), null, "simulacoes"));
                out.add(new Achado(m.chave(), null, "Transação com desconto disponível: " + m.nome(), m.debitos().size() + " inscrição(ões) não negociada(s), "
                        + "total " + moeda(m.totalConsolidado()) + "; desconto máximo do edital " + moeda(m.desconto().valor()) + " com a CAPAG oficial "
                        + m.desconto().classe() + "; " + m.cronograma().size() + " prestações.", m.desconto().valor(), ev));
            }
            return Avaliacao.de(out);
        }
    }

    @Component
    static class R10ParcelamentoEmRisco implements RegraPontoAtencao {
        public String codigo() { return "R10"; }

        public Avaliacao avaliar(ContextoPontos ctx, Definicao def) {
            var ativos = ctx.parcelamentos().stream().filter(p -> !"RESCINDIDO".equals(p.situacao())).toList();
            if (ativos.isEmpty()) return Avaliacao.de(List.of());
            List<Achado> out = new ArrayList<>();
            ativos.stream().filter(p -> "EM_ATRASO".equals(p.situacao())).forEach(p -> out.add(new Achado("atraso|" + p.numero(), null,
                    "Parcelamento em atraso: " + p.numero(), p.modalidade() + " " + p.numero() + " em atraso (" + p.parcelasPagas() + " de " + p.parcelasTotal()
                    + " pagas): risco de rescisão.", p.saldo(), List.of(new Evidencia("PARCELAMENTO", p.numero(), p.modalidade(), p.valorParcela(), p.origem(),
                    "situacao-fiscal?parcelamento=" + p.id())))));
            BigDecimal sustentavel;
            try {
                sustentavel = ctx.capacidade().parcelaSustentavel();
            } catch (com.fiscalhub.backend.exception.BusinessRuleException e) {
                sustentavel = null;
            }
            if (sustentavel == null) {
                return new Avaliacao(out, "parcela sustentável não disponível (análise gerencial): importe as ECD para comparar as parcelas");
            }
            BigDecimal parcelas = soma(ativos.stream().map(p -> p.valorParcela()));
            if (parcelas.compareTo(sustentavel) > 0) {
                out.add(new Achado("sustentavel", null, "Parcelas acima da parcela sustentável", "As parcelas em curso somam " + moeda(parcelas)
                        + " por mês, acima da parcela sustentável de " + moeda(sustentavel) + " (análise gerencial): risco de rescisão.",
                        parcelas.subtract(sustentavel), Stream.concat(ativos.stream().map(p -> new Evidencia("PARCELAMENTO", p.numero(), p.modalidade() + " "
                        + p.numero(), p.valorParcela(), p.origem(), "situacao-fiscal?parcelamento=" + p.id())), Stream.of(new Evidencia("CAPACIDADE",
                        "parcela-sustentavel", "Parcela sustentável (análise gerencial)", sustentavel, null, "capacidade"))).toList()));
            }
            return Avaliacao.de(out);
        }
    }

    @Component
    static class R11IndedutivelElalur implements RegraPontoAtencao {
        public String codigo() { return "R11"; }

        public Avaliacao avaliar(ContextoPontos ctx, Definicao def) {
            List<Apuracao> ecf = ctx.apuracoes().stream().filter(a -> "ECF".equals(a.escrituracao()) && ("IRPJ".equals(a.tributo()) || "CSLL".equals(a.tributo()))).toList();
            if (ecf.isEmpty()) return Avaliacao.naoAvaliada("falta importar a ECF (e-Lalur e e-Lacs, Parte A)");
            List<String> termos = def.lista("termos").stream().map(t -> t.toLowerCase(Locale.ROOT)).toList();
            Map<String, List<Evidencia>> porItem = new TreeMap<>();
            Map<String, BigDecimal> valores = new HashMap<>();
            for (Apuracao a : ecf) {
                for (Detalhe l : ctx.servicos().apuracoes().detalhe(ctx.clienteId(), a.id(), ctx.usuario()).linhas()) {
                    String desc = Objects.toString(l.descricao(), "").toLowerCase(Locale.ROOT);
                    if (!"ADICAO".equals(l.grupo()) || termos.stream().noneMatch(desc::contains)) continue;
                    String k = a.dataFim().getYear() + "|" + l.descricao();
                    porItem.computeIfAbsent(k, x -> new ArrayList<>()).add(new Evidencia("ELALUR", String.valueOf(a.id()), a.tributo() + ": " + l.descricao(),
                            l.valor(), origem(a.documentoId(), a.arquivo(), l.registro(), l.linha()), "tributos?apuracao=" + a.id()));
                    valores.merge(k, l.valor(), BigDecimal::max);
                }
            }
            return Avaliacao.de(porItem.entrySet().stream().map(e -> new Achado(e.getKey(), null, "Adição indedutível no e-Lalur: " + e.getKey().split("\\|", 2)[1],
                    "Exercício " + e.getKey().split("\\|")[0] + ": adição de " + moeda(valores.get(e.getKey())) + " (" + e.getKey().split("\\|", 2)[1]
                            + ") na Parte A do IRPJ/CSLL — indício de autuação; revisar a contingência.", valores.get(e.getKey()), e.getValue())).toList());
        }
    }

    @Component
    static class R12PrejuizoFiscal implements RegraPontoAtencao {
        public String codigo() { return "R12"; }

        public Avaliacao avaliar(ContextoPontos ctx, Definicao def) {
            List<SaldoFiscal> parteB = ctx.saldos().stream().filter(s -> "PREJUIZO_FISCAL".equals(s.tipo()) || "BASE_NEGATIVA_CSLL".equals(s.tipo())).toList();
            if (parteB.isEmpty() && ctx.apuracoes().stream().noneMatch(a -> "ECF".equals(a.escrituracao()))) {
                return Avaliacao.naoAvaliada("falta importar a ECF (Parte B do e-Lalur e do e-Lacs)");
            }
            List<Achado> out = new ArrayList<>();
            parteB.stream().collect(Collectors.groupingBy(SaldoFiscal::tipo)).forEach((tipo, lista) -> {
                SaldoFiscal ultimo = lista.stream().max(Comparator.comparing(SaldoFiscal::dataFim).thenComparing(SaldoFiscal::id)).orElseThrow();
                BigDecimal saldo = soma(lista.stream().filter(s -> s.dataFim().equals(ultimo.dataFim())).map(s -> s.saldoFinal().abs()));
                if (saldo.signum() > 0) {
                    String nome = "PREJUIZO_FISCAL".equals(tipo) ? "Prejuízo fiscal (IRPJ)" : "Base negativa da CSLL";
                    out.add(new Achado(tipo, null, nome + " acumulado", nome + " de " + moeda(saldo) + " em " + ultimo.dataFim() + ": compensável até "
                            + def.decimal("percentualTrava").stripTrailingZeros().toPlainString() + "% do lucro real/base de cada período.", saldo,
                            lista.stream().filter(s -> s.dataFim().equals(ultimo.dataFim())).map(RegrasIniciais::evidencia).toList()));
                }
            });
            return Avaliacao.de(out);
        }
    }

    @Component
    static class R13CreditosPisCofins implements RegraPontoAtencao {
        public String codigo() { return "R13"; }

        public Avaliacao avaliar(ContextoPontos ctx, Definicao def) {
            List<SaldoFiscal> creditos = ctx.saldos().stream().filter(s -> "CREDITO_PIS".equals(s.tipo()) || "CREDITO_COFINS".equals(s.tipo())).toList();
            if (creditos.isEmpty() && ctx.escrituracoes().stream().noneMatch(e -> "EFD_CONTRIBUICOES".equals(e.origem()))) {
                return Avaliacao.naoAvaliada("falta importar a EFD-Contribuições (registros 1100 e 1500)");
            }
            int n = def.inteiro("meses");
            List<Achado> out = new ArrayList<>();
            creditos.stream().collect(Collectors.groupingBy(SaldoFiscal::tipo)).forEach((tipo, lista) -> {
                TreeMap<YearMonth, BigDecimal> porMes = new TreeMap<>();
                lista.forEach(s -> porMes.merge(YearMonth.from(s.dataFim()), s.saldoFinal(), BigDecimal::add));
                List<Map.Entry<YearMonth, BigDecimal>> ultimos = new ArrayList<>(porMes.descendingMap().entrySet()).subList(0, Math.min(n, porMes.size()));
                if (ultimos.size() == n && ultimos.stream().allMatch(e -> e.getValue().signum() > 0)) {
                    YearMonth fim = ultimos.getFirst().getKey();
                    String nome = "CREDITO_PIS".equals(tipo) ? "PIS" : "COFINS";
                    out.add(new Achado(tipo, null, "Créditos de " + nome + " acumulados sem uso", "Saldo de créditos de " + nome + " de "
                            + moeda(ultimos.getFirst().getValue()) + " em " + fim + ", com saldo nos últimos " + n + " meses.", ultimos.getFirst().getValue(),
                            lista.stream().filter(s -> YearMonth.from(s.dataFim()).equals(fim)).map(RegrasIniciais::evidencia).toList()));
                }
            });
            return Avaliacao.de(out);
        }
    }

    @Component
    static class R14DividendosComDebito implements RegraPontoAtencao {
        public String codigo() { return "R14"; }

        public Avaliacao avaliar(ContextoPontos ctx, Definicao def) {
            List<Integer> anos = ctx.servicos().dfc().exercicios(ctx.clienteId(), ctx.usuario());
            if (anos.isEmpty()) return Avaliacao.naoAvaliada("falta importar a ECD (lançamentos para a DFC direta)");
            int ano = anos.stream().max(Integer::compare).orElseThrow();
            DfcDiretaService.Dfc dfc = ctx.servicos().dfc().dfc(ctx.clienteId(), ano, ctx.usuario());
            BigDecimal distribuido = soma(dfc.linhas().stream().filter(l -> "CAPITAL_DIVIDENDOS".equals(l.categoria())).map(l -> l.total()));
            if (distribuido.signum() >= 0) return Avaliacao.de(List.of());
            // ADI 5.161 (STF, 30/09/2026): só inscrição em dívida ativa da União, exigível (CTN, art. 151) e sem garantia (LEF, art. 9º)
            var detalhes = ctx.servicos().capacidade().insumos(ctx.clienteId(), ctx.usuario(), MetodologiaCapacidade.Simulacao.NENHUMA).detalhes();
            List<DebitoConsolidado> federais = ctx.debitos().stream().filter(d -> d.orgaoCredor() == OrgaoCredor.PGFN
                    && d.situacao() == SituacaoDebito.INSCRITO_DIVIDA_ATIVA && semGarantiaNemSuspensao(detalhes.get(d.numeroInscricao()))).toList();
            if (federais.isEmpty()) return Avaliacao.de(List.of());
            List<Evidencia> ev = new ArrayList<>(List.of(new Evidencia("DFC", String.valueOf(ano), "Saídas de capital e dividendos (DFC direta " + ano + ")",
                    distribuido.negate(), origem(dfc.documentoId(), dfc.arquivo(), "I200/I250", null), "demonstracoes?dfc=" + ano)));
            federais.forEach(d -> ev.add(evidencia(d)));
            return Avaliacao.de(List.of(new Achado(String.valueOf(ano), null, "Distribuição de lucros com débito federal não garantido",
                    "Saídas de capital e dividendos de " + moeda(distribuido.negate()) + " na DFC direta de " + ano + " com " + federais.size()
                            + " inscrição(ões) em dívida ativa da União exigível(is) e sem garantia.", distribuido.negate(), ev)));
        }

        private static boolean semGarantiaNemSuspensao(MetodologiaCapacidade.Detalhe d) {
            return d == null || ((d.garantia() == null || d.garantia().isBlank()) && d.suspensaoDesde() == null);
        }
    }

    @Component
    static class R15EcdEcf implements RegraPontoAtencao {
        public String codigo() { return "R15"; }

        public Avaliacao avaliar(ContextoPontos ctx, Definicao def) {
            List<ConferenciaEcdEcf.Comparacao> c = ctx.servicos().conferencia().comparacoes(ctx.clienteId());
            if (c.isEmpty()) return Avaliacao.naoAvaliada("falta importar a ECD e a ECF do mesmo exercício");
            BigDecimal limiar = def.decimal("limiar");
            return Avaliacao.de(c.stream().filter(x -> x.diferenca().abs().compareTo(limiar) > 0).map(x -> new Achado(x.exercicio() + "|" + x.item(), null,
                    "Divergência ECD × ECF: " + x.item() + " de " + x.exercicio(), x.item() + " de " + x.exercicio() + ": ECD " + moeda(x.ecd()) + " × ECF "
                    + moeda(x.ecf()) + "; diferença " + moeda(x.diferenca()) + ".", x.diferenca().abs(), List.of(
                    new Evidencia("ECD", String.valueOf(x.documentoEcd()), "ECD: " + x.origemEcd(), x.ecd(), origem(x.documentoEcd(), x.arquivoEcd(), x.origemEcd(), null), "demonstracoes"),
                    new Evidencia("ECF", String.valueOf(x.documentoEcf()), "ECF: " + x.origemEcf(), x.ecf(), origem(x.documentoEcf(), x.arquivoEcf(), x.origemEcf(), null), "demonstracoes"))))
                    .toList());
        }
    }

    @Component
    static class R16Substituta implements RegraPontoAtencao {
        public String codigo() { return "R16"; }

        public Avaliacao avaliar(ContextoPontos ctx, Definicao def) {
            var todas = ctx.escrituracoes();
            if (todas.isEmpty()) return Avaliacao.naoAvaliada("nenhuma escrituração importada");
            return Avaliacao.de(todas.stream().filter(e -> "SUBSTITUIDA".equals(e.situacao())).map(sub -> todas.stream()
                    .filter(v -> "VIGENTE".equals(v.situacao()) && v.origem().equals(sub.origem()) && v.dataFim().equals(sub.dataFim())).findFirst()
                    .map(v -> new Achado(v.origem() + "|" + v.dataFim(), null, "Escrituração substituta: " + v.origem() + " " + v.dataFim().getYear(),
                            v.origem() + " de " + v.dataInicio() + " a " + v.dataFim() + ": vale a substituta (" + v.arquivo() + "); a original (" + sub.arquivo()
                                    + ") foi substituída.", null, List.of(
                            new Evidencia("ESCRITURACAO", String.valueOf(v.id()), "Vigente: " + v.arquivo(), null, origem(v.documentoId(), v.arquivo(), null, null), "importacoes?documento=" + v.documentoId()),
                            new Evidencia("ESCRITURACAO", String.valueOf(sub.id()), "Substituída: " + sub.arquivo(), null, origem(sub.documentoId(), sub.arquivo(), null, null), "importacoes?documento=" + sub.documentoId()))))
                    .orElse(null)).filter(Objects::nonNull).toList());
        }
    }
}
