package com.fiscalhub.backend.relatorio;

import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.relatorio.CargaTributariaService.ExercicioCarga;
import com.fiscalhub.backend.relatorio.ModeloRelatorio.*;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.AcessoRecursoService;
import com.fiscalhub.backend.situacao.ConciliacaoService;
import com.fiscalhub.backend.situacao.ConciliacaoService.ItemConciliacao;
import com.fiscalhub.backend.situacao.ConciliacaoService.Status;
import com.fiscalhub.backend.situacao.SituacaoFiscalService;
import com.fiscalhub.backend.situacao.SituacaoFiscalService.OrigemValor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Monta o ModeloRelatorio de cada tipo a partir dos serviços que já existem (conciliação, situação fiscal, carga
 * tributária, identidade). Nada é recalculado: só agrupa, soma para os totais e escolhe o que mostrar.
 * Tipo novo de relatório: um método aqui + o caso no switch (o template e o Excel são genéricos).
 */
@Component
@Transactional(readOnly = true)
public class MontagemRelatorios {

    /** Parâmetros da emissão (também gravados no relatório emitido). de/ate: competências; na carga, os exercícios. */
    public record Parametros(TipoRelatorio tipo, YearMonth de, YearMonth ate, boolean incluirDivergencias, boolean incluirFontes,
                             Long simulacaoId) {
    }

    static final String VERSAO_TEMPLATE = "relatorio-2026.10";
    private static final Set<Status> DIVERGENCIAS = EnumSet.of(Status.DIVERGENCIA_APURADO_DECLARADO, Status.NAO_DECLARADO,
            Status.PAGO_A_MENOR, Status.PAGO_A_MAIOR, Status.NAO_PAGO, Status.PAGO_EM_ATRASO_SEM_ACRESCIMOS);
    private static final String ND = "não disponível";
    private static final int MAX_SERIES = GraficoSvg.PALETA.size();

    private final AcessoRecursoService acesso;
    private final ConciliacaoService conciliacao;
    private final SituacaoFiscalService situacao;
    private final CargaTributariaService carga;
    private final IdentidadeEscritorioService identidade;
    private final NamedParameterJdbcTemplate jdbc;
    private final RelatoriosCapacidade capacidade;
    private final RelatorioProposta proposta;
    private final String versaoNormas;

    public MontagemRelatorios(AcessoRecursoService acesso, ConciliacaoService conciliacao, SituacaoFiscalService situacao,
                              CargaTributariaService carga, IdentidadeEscritorioService identidade, NamedParameterJdbcTemplate jdbc,
                              RelatoriosCapacidade capacidade, RelatorioProposta proposta) {
        this.capacidade = capacidade;
        this.proposta = proposta;
        this.acesso = acesso;
        this.conciliacao = conciliacao;
        this.situacao = situacao;
        this.carga = carga;
        this.identidade = identidade;
        this.jdbc = jdbc;
        this.versaoNormas = "codigos-receita sha256:" + sha256Recurso("receitas/codigos-receita.txt").substring(0, 12)
                + "; acréscimos: Lei 9.430/96, art. 61";
    }

    public ModeloRelatorio montar(long clienteId, Parametros p, LocalDateTime emissao, UsuarioAutenticado ua) {
        Cliente cliente = acesso.cliente(clienteId, ua);
        if (p.de() != null && p.ate() != null && p.de().isAfter(p.ate())) {
            throw new BusinessRuleException("O início do período deve ser anterior ao fim.");
        }
        Corpo corpo = switch (p.tipo()) {
            case TRIBUTOS -> tributos(clienteId, p, ua);
            case CARGA_TRIBUTARIA -> carga(clienteId, p, ua);
            case SITUACAO_FISCAL -> situacao(clienteId, ua);
            case CAPACIDADE -> capacidade.capacidade(clienteId, ua);
            case DOSSIE_CAPAG -> capacidade.dossie(clienteId, ua);
            case PROPOSTA_REGULARIZACAO -> proposta.proposta(clienteId, p.simulacaoId(), ua);
        };
        var id = identidade.consultar(ua);
        String responsavel = id.responsavelNome() == null ? null
                : id.responsavelNome() + (id.responsavelRegistro() == null ? "" : " — " + id.responsavelRegistro());
        Cabecalho cab = new Cabecalho(id.nomeExibicao() == null ? id.nome() : id.nomeExibicao(), identidade.chaveLogo(ua),
                // a cor vai para um atributo style do template: só #rrggbb passa (defesa além da validação do PUT)
                id.corPrincipal().matches("#[0-9a-f]{6}") ? id.corPrincipal() : IdentidadeEscritorioService.COR_PADRAO, id.rodape(),
                responsavel, cliente.getRazaoSocial(), cliente.getCnpj(), periodo(p), emissao, null);
        List<Fonte> fontes = p.incluirFontes() ? fontes(clienteId, corpo.documentos()) : List.of();
        List<String> notas = new ArrayList<>(notas(p.tipo()));
        notas.addAll(0, corpo.notas());
        List<String> assinaturas = p.tipo() == TipoRelatorio.DOSSIE_CAPAG ? List.of(
                "Contador responsável" + (responsavel == null ? "" : ": " + responsavel), "Representante legal do contribuinte: "
                        + cliente.getRazaoSocial()) : null;
        return new ModeloRelatorio(p.tipo(), p.tipo().titulo, cab, corpo.sumario(), corpo.secoes(), notas, fontes,
                new Versoes(VERSAO_TEMPLATE, versaoNormas), assinaturas);
    }

    /** notas: notas metodológicas próprias do conteúdo (ex.: as normas e versões usadas), antes das fixas do tipo. */
    record Corpo(List<Indicador> sumario, List<Secao> secoes, Set<Long> documentos, List<String> notas) {
        Corpo(List<Indicador> sumario, List<Secao> secoes, Set<Long> documentos) {
            this(sumario, secoes, documentos, List.of());
        }
    }

    // ---------------------------------------------------------------- tributos apurados, declarados e pagos

    private Corpo tributos(long clienteId, Parametros p, UsuarioAutenticado ua) {
        List<ItemConciliacao> itens = conciliacao.conciliar(clienteId, p.de(), p.ate(), null, ua).itens().stream()
                .sorted(Comparator.comparing(ItemConciliacao::esfera).reversed().thenComparing(i -> i.tributo().name())
                        .thenComparing(ItemConciliacao::periodo)).toList();
        Set<Long> documentos = new LinkedHashSet<>();
        itens.forEach(i -> origens(i).forEach(o -> documentos.add(o.documentoId())));

        List<Coluna> colunas = List.of(new Coluna("Esfera", Formato.TEXTO), new Coluna("Tributo", Formato.TEXTO),
                new Coluna("Competência", Formato.COMPETENCIA), new Coluna("Apurado", Formato.MOEDA), new Coluna("Declarado", Formato.MOEDA),
                new Coluna("Pago", Formato.MOEDA), new Coluna("Diferença", Formato.MOEDA), new Coluna("Status", Formato.TEXTO));
        List<List<String>> linhas = itens.stream().map(i -> linha(esfera(i.esfera()), tributo(i), i.periodo().toString(),
                cru(i.apurado() == null ? null : i.apurado().valor()), cru(i.declarado() == null ? null : i.declarado().valor()),
                cru(i.pago() == null ? null : i.pago().valor()), cru(i.diferenca()), i.statusRotulo())).toList();
        List<Secao> secoes = new ArrayList<>();
        String semDados = itens.isEmpty() ? "Nenhum tributo apurado, declarado ou pago no período." : null;
        secoes.add(new Secao("Apurado × pago por competência", semDados, false, null, itens.isEmpty() ? null : graficoApuradoPago(itens)));
        secoes.add(new Secao("Tributos por competência", "Diferença: devido − pago (positivo = falta pagar); quando o apurado "
                + "difere do declarado ou não foi declarado, apurado − declarado.", false,
                new Tabela(colunas, linhas, null), null));
        secoes.add(new Secao("Totais por tributo", null, false, totais(itens, i -> esfera(i.esfera()) + "|" + tributo(i), "Tributo"), null));
        secoes.add(new Secao("Totais por esfera", null, false, totais(itens, i -> esfera(i.esfera()), "Esfera"), null));
        boolean algumPago = itens.stream().anyMatch(i -> i.pago() != null);
        secoes.add(new Secao("Composição dos tributos pagos", algumPago ? null : "Nenhum pagamento no período.", false, null,
                algumPago ? graficoComposicaoPaga(itens) : null));

        List<ItemConciliacao> divergentes = itens.stream().filter(i -> DIVERGENCIAS.contains(i.status())).toList();
        BigDecimal acrescimos = divergentes.stream().map(ItemConciliacao::acrescimos).filter(Objects::nonNull)
                .map(a -> a.total()).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (p.incluirDivergencias()) {
            List<List<String>> div = divergentes.stream().map(i -> linha(tributo(i), i.periodo().toString(), i.statusRotulo(),
                    cru(i.diferenca()), cru(i.acrescimos() == null ? null : i.acrescimos().multa()),
                    cru(i.acrescimos() == null ? null : i.acrescimos().juros()),
                    i.erroAcrescimos() != null ? "não calculado: " + i.erroAcrescimos() : cru(i.acrescimos() == null ? null : i.acrescimos().total()),
                    origemTexto(i))).toList();
            secoes.add(new Secao("Divergências", divergentes.isEmpty() ? "Nenhuma divergência no período." : "Acréscimos devidos "
                    + "pelo atraso (regra federal) até o pagamento ou, se não pago, até a data de emissão.", !divergentes.isEmpty(),
                    divergentes.isEmpty() ? null : new Tabela(List.of(new Coluna("Tributo", Formato.TEXTO),
                            new Coluna("Competência", Formato.COMPETENCIA), new Coluna("Status", Formato.TEXTO),
                            new Coluna("Diferença", Formato.MOEDA), new Coluna("Multa", Formato.MOEDA), new Coluna("Juros", Formato.MOEDA),
                            new Coluna("Acréscimos", Formato.MOEDA), new Coluna("Origem dos valores", Formato.TEXTO)), div, null), null));
        }
        List<Indicador> sumario = List.of(
                new Indicador("Total apurado", cru(soma(itens, i -> i.apurado() == null ? null : i.apurado().valor())), Formato.MOEDA, null),
                new Indicador("Total declarado", cru(soma(itens, i -> i.declarado() == null ? null : i.declarado().valor())), Formato.MOEDA, null),
                new Indicador("Total pago", cru(soma(itens, i -> i.pago() == null ? null : i.pago().valor())), Formato.MOEDA, null),
                new Indicador("Divergências", String.valueOf(divergentes.size()), Formato.INTEIRO,
                        divergentes.isEmpty() ? null : "de " + itens.size() + " tributos × competências"),
                new Indicador("Acréscimos devidos", cru(acrescimos), Formato.MOEDA, "multa e juros de mora, regra federal"));
        return new Corpo(sumario, secoes, documentos);
    }

    private static Tabela totais(List<ItemConciliacao> itens, Function<ItemConciliacao, String> chave, String rotulo) {
        Map<String, List<ItemConciliacao>> grupos = itens.stream().collect(Collectors.groupingBy(chave, TreeMap::new, Collectors.toList()));
        boolean comEsfera = !"Esfera".equals(rotulo);
        List<Coluna> colunas = new ArrayList<>();
        if (comEsfera) {
            colunas.add(new Coluna("Esfera", Formato.TEXTO));
        }
        colunas.addAll(List.of(new Coluna(rotulo, Formato.TEXTO), new Coluna("Apurado", Formato.MOEDA), new Coluna("Declarado", Formato.MOEDA),
                new Coluna("Pago", Formato.MOEDA), new Coluna("Divergências", Formato.INTEIRO)));
        List<List<String>> linhas = new ArrayList<>();
        grupos.forEach((k, g) -> {
            List<String> l = new ArrayList<>(List.of(k.split("\\|")));
            l.addAll(valores(g));
            linhas.add(l);
        });
        List<String> total = new ArrayList<>(comEsfera ? List.of("Total", "") : List.of("Total"));
        total.addAll(valores(itens));
        return new Tabela(colunas, linhas, total);
    }

    private static List<String> valores(List<ItemConciliacao> g) {
        return List.of(cru(soma(g, i -> i.apurado() == null ? null : i.apurado().valor())),
                cru(soma(g, i -> i.declarado() == null ? null : i.declarado().valor())),
                cru(soma(g, i -> i.pago() == null ? null : i.pago().valor())),
                String.valueOf(g.stream().filter(i -> DIVERGENCIAS.contains(i.status())).count()));
    }

    private static Grafico graficoApuradoPago(List<ItemConciliacao> itens) {
        List<YearMonth> meses = itens.stream().map(ItemConciliacao::periodo).distinct().sorted().toList();
        Function<Function<ItemConciliacao, BigDecimal>, List<BigDecimal>> porMes = f -> meses.stream()
                .map(m -> soma(itens.stream().filter(i -> i.periodo().equals(m)).toList(), f)).toList();
        return new Grafico(TipoGrafico.BARRAS, "Apurado × pago por competência", Formato.MOEDA, meses.stream().map(MontagemRelatorios::mes).toList(),
                List.of(new Serie("Apurado", porMes.apply(i -> i.apurado() == null ? null : i.apurado().valor())),
                        new Serie("Pago", porMes.apply(i -> i.pago() == null ? null : i.pago().valor()))));
    }

    /** Barras empilhadas: os 5 tributos mais pagos e "Outros" (a paleta tem 6 cores: nunca uma cor gerada). */
    private static Grafico graficoComposicaoPaga(List<ItemConciliacao> itens) {
        List<YearMonth> meses = itens.stream().filter(i -> i.pago() != null).map(ItemConciliacao::periodo).distinct().sorted().toList();
        Map<String, BigDecimal> totalPorTributo = new HashMap<>();
        itens.stream().filter(i -> i.pago() != null).forEach(i -> totalPorTributo.merge(tributo(i), i.pago().valor(), BigDecimal::add));
        List<String> ordem = totalPorTributo.entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(Map.Entry::getKey).toList();
        List<String> principais = ordem.size() <= MAX_SERIES ? ordem : ordem.subList(0, MAX_SERIES - 1);
        List<Serie> series = new ArrayList<>();
        for (String t : principais) {
            series.add(new Serie(t, meses.stream().map(m -> soma(itens.stream().filter(i -> i.periodo().equals(m) && tributo(i).equals(t)).toList(),
                    i -> i.pago() == null ? null : i.pago().valor())).toList()));
        }
        if (ordem.size() > principais.size()) {
            series.add(new Serie("Outros", meses.stream().map(m -> soma(itens.stream().filter(i -> i.periodo().equals(m)
                    && !principais.contains(tributo(i))).toList(), i -> i.pago() == null ? null : i.pago().valor())).toList()));
        }
        return new Grafico(TipoGrafico.EMPILHADAS, "Composição dos tributos pagos por competência", Formato.MOEDA,
                meses.stream().map(MontagemRelatorios::mes).toList(), series);
    }

    private static Stream<OrigemValor> origens(ItemConciliacao i) {
        return Stream.of(i.apurado() == null ? List.<OrigemValor>of() : i.apurado().origens(),
                i.declarado() == null ? List.<OrigemValor>of() : i.declarado().origens(),
                i.pago() == null ? List.<OrigemValor>of() : i.pago().origens()).flatMap(List::stream);
    }

    private static String origemTexto(ItemConciliacao i) {
        List<String> partes = new ArrayList<>();
        if (i.apurado() != null) partes.add("Apurado: " + origens(i.apurado().origens()));
        if (i.declarado() != null) partes.add("Declarado: " + origens(i.declarado().origens()));
        if (i.pago() != null) partes.add("Pago: " + origens(i.pago().origens()));
        i.debitos().stream().findFirst().ifPresent(d -> partes.add("Débito " + d.orgao() + ": " + origens(d.origens())));
        if (!i.codigosIlustrativos().isEmpty()) partes.add("código ilustrativo " + String.join(", ", i.codigosIlustrativos()));
        return String.join("; ", partes);
    }

    private static String origens(List<OrigemValor> os) {
        return os.isEmpty() ? "—" : os.stream().map(MontagemRelatorios::origem).distinct().collect(Collectors.joining(" + "));
    }

    /** "EFD_CONTRIBUICOES_082026.txt, registro M600, linha 123" ou "DARF.pdf, p. 1, linha 13". */
    static String origem(OrigemValor o) {
        StringBuilder s = new StringBuilder(o.arquivo());
        if (o.pagina() != null) s.append(", p. ").append(o.pagina());
        if (o.registro() != null) s.append(", ").append(o.registro().matches("[A-Z0-9]{4}") ? "registro " + o.registro() : o.registro());
        if (o.linha() != null) s.append(", linha ").append(o.linha());
        return s.toString();
    }

    // ---------------------------------------------------------------- carga tributária efetiva

    private Corpo carga(long clienteId, Parametros p, UsuarioAutenticado ua) {
        List<ExercicioCarga> exercicios = carga.carga(clienteId, p.de() == null ? null : p.de().getYear(),
                p.ate() == null ? null : p.ate().getYear(), ua);
        Set<Long> documentos = new LinkedHashSet<>();
        exercicios.forEach(e -> documentos.addAll(e.documentos()));
        List<List<String>> linhas = new ArrayList<>();
        BigDecimal anterior = null;
        for (ExercicioCarga e : exercicios) {
            BigDecimal variacao = anterior != null && e.carga() != null ? e.carga().subtract(anterior) : null;
            linhas.add(List.of(String.valueOf(e.exercicio()), nd(e.receitaBruta()), nd(e.tributos()), nd(e.carga()),
                    variacao == null ? "—" : variacao.toPlainString(), nd(e.lair()), nd(e.irpjCsll()),
                    e.irpjCsllNaoAplicavel() ? "não aplicável (prejuízo)" : nd(e.cargaIrpjCsll())));
            anterior = e.carga() != null ? e.carga() : anterior;
        }
        List<Secao> secoes = new ArrayList<>();
        secoes.add(new Secao("Carga efetiva por exercício", "Carga efetiva = tributos ÷ receita bruta; IRPJ + CSLL ÷ lucro antes do "
                + "IRPJ e da CSLL. Variação em pontos percentuais sobre o último exercício disponível.", false,
                new Tabela(List.of(new Coluna("Exercício", Formato.TEXTO), new Coluna("Receita bruta", Formato.MOEDA),
                        new Coluna("Tributos", Formato.MOEDA), new Coluna("Carga efetiva", Formato.PERCENTUAL),
                        new Coluna("Variação (p.p.)", Formato.PERCENTUAL), new Coluna("Lucro antes do IR", Formato.MOEDA),
                        new Coluna("IRPJ + CSLL", Formato.MOEDA), new Coluna("IRPJ + CSLL ÷ LAIR", Formato.PERCENTUAL)), linhas, null), null));
        List<List<String>> faltas = exercicios.stream().flatMap(e -> e.faltando().stream().map(f -> List.of(String.valueOf(e.exercicio()),
                "não disponível — " + f))).toList();
        if (!faltas.isEmpty()) {
            secoes.add(new Secao("Insumos que faltam", "Nada é estimado: sem o insumo, a razão fica como não disponível.", true,
                    new Tabela(List.of(new Coluna("Exercício", Formato.TEXTO), new Coluna("O que importar", Formato.TEXTO)), faltas, null), null));
        }
        boolean algumaCarga = exercicios.stream().anyMatch(e -> e.carga() != null);
        secoes.add(new Secao("Carga efetiva por exercício (gráfico)", algumaCarga ? null
                : "Gráfico não disponível: nenhum exercício com a carga calculada.", false, null, !algumaCarga ? null : new Grafico(TipoGrafico.LINHA,
                "Carga tributária efetiva por exercício", Formato.PERCENTUAL, exercicios.stream().map(e -> String.valueOf(e.exercicio())).toList(),
                List.of(new Serie("Carga efetiva", exercicios.stream().map(ExercicioCarga::carga).toList())))));
        List<List<String>> porTributo = exercicios.stream().flatMap(e -> e.porTributo().stream().map(t -> List.of(String.valueOf(e.exercicio()),
                t.tributo(), cru(t.valor()), nd(t.carga())))).toList();
        secoes.add(new Secao("Carga por tributo", porTributo.isEmpty() ? "Nenhuma apuração importada no período." : null, false,
                porTributo.isEmpty() ? null : new Tabela(List.of(new Coluna("Exercício", Formato.TEXTO), new Coluna("Tributo", Formato.TEXTO),
                        new Coluna("Valor a recolher", Formato.MOEDA), new Coluna("Carga", Formato.PERCENTUAL)), porTributo, null), null));
        // só desenha com algum valor positivo: um gráfico de barras zeradas não diz nada (os zeros estão na tabela)
        List<ExercicioCarga> comTributo = exercicios.stream()
                .filter(e -> e.porTributo().stream().anyMatch(t -> t.carga() != null && t.carga().signum() > 0)).toList();
        if (!comTributo.isEmpty()) {
            List<ExercicioCarga> ultimos = comTributo.subList(Math.max(0, comTributo.size() - MAX_SERIES), comTributo.size());
            List<String> tributos = ultimos.stream().flatMap(e -> e.porTributo().stream()).map(CargaTributariaService.TributoCarga::tributo)
                    .distinct().sorted().toList();
            secoes.add(new Secao("Carga por tributo (gráfico)", null, false, null, new Grafico(TipoGrafico.BARRAS, "Carga por tributo",
                    Formato.PERCENTUAL, tributos, ultimos.stream().map(e -> new Serie(String.valueOf(e.exercicio()), tributos.stream()
                    .map(t -> e.porTributo().stream().filter(x -> x.tributo().equals(t)).map(CargaTributariaService.TributoCarga::carga)
                            .filter(Objects::nonNull).findFirst().orElse(null)).toList())).toList())));
        }
        ExercicioCarga ultimo = exercicios.stream().filter(e -> e.carga() != null).reduce((a, b) -> b).orElse(null);
        ExercicioCarga ultimoIr = exercicios.stream().filter(e -> e.cargaIrpjCsll() != null).reduce((a, b) -> b).orElse(null);
        List<Indicador> sumario = List.of(
                new Indicador("Carga efetiva" + (ultimo == null ? "" : " " + ultimo.exercicio()), ultimo == null ? ND : cru(ultimo.carga()),
                        Formato.PERCENTUAL, ultimo == null ? "falta insumo em todos os exercícios" : "tributos ÷ receita bruta"),
                new Indicador("IRPJ + CSLL ÷ LAIR" + (ultimoIr == null ? "" : " " + ultimoIr.exercicio()),
                        ultimoIr == null ? ND : cru(ultimoIr.cargaIrpjCsll()), Formato.PERCENTUAL, null),
                new Indicador("Exercícios analisados", String.valueOf(exercicios.size()), Formato.INTEIRO, null),
                new Indicador("Exercícios com carga calculada", String.valueOf(exercicios.stream().filter(e -> e.carga() != null).count()),
                        Formato.INTEIRO, null));
        return new Corpo(sumario, secoes, documentos);
    }

    private static String nd(BigDecimal v) {
        return v == null ? ND : v.toPlainString();
    }

    // ---------------------------------------------------------------- situação fiscal resumida

    private Corpo situacao(long clienteId, UsuarioAutenticado ua) {
        var debitos = situacao.debitos(clienteId, null, null, ua);
        var parcelamentos = situacao.parcelamentos(clienteId, ua);
        var pendencias = situacao.pendencias(clienteId, ua);
        var capag = situacao.capacidades(clienteId, ua);
        Set<Long> documentos = new LinkedHashSet<>();
        debitos.forEach(d -> d.origens().forEach(o -> documentos.add(o.origem().documentoId())));
        parcelamentos.forEach(x -> documentos.add(x.origem().documentoId()));
        pendencias.forEach(x -> documentos.add(x.origem().documentoId()));
        capag.forEach(x -> documentos.add(x.origem().documentoId()));

        List<Coluna> colunasDebito = colunas("Órgão", "Tributo", "Competência:COMPETENCIA", "Inscrição / processo", "Situação",
                "Valor total:MOEDA", "Origem");
        Function<SituacaoFiscalService.DebitoConsolidado, List<String>> linhaDebito = d -> linha(d.orgaoCredor().name(),
                d.tributo() == null ? Objects.toString(d.codigoReceita(), "—") : d.tributo().name(),
                d.competencia() == null ? Objects.toString(d.periodo(), "—") : d.competencia().toString(),
                Stream.of(d.numeroInscricao(), d.numeroProcesso()).filter(Objects::nonNull).collect(Collectors.joining(" / ")),
                d.situacao().name().replace('_', ' ').toLowerCase(), cru(d.valorTotal()),
                d.origens().stream().map(o -> origem(o.origem())).distinct().collect(Collectors.joining(" + ")));
        Function<SituacaoFiscalService.PendenciaFiscal, List<String>> linhaPendencia = x -> linha(x.tipo(), Objects.toString(x.descricao(), "—"),
                x.periodo() == null ? "—" : x.periodo().toString(), Objects.toString(x.situacao(), "—"),
                x.impedeCertidao() ? "Sim — " + x.fundamento() : "Não (fora do art. 4º, I, da PC RFB/PGFN 1.751/2014)", origem(x.origem()));
        List<Coluna> colunasPendencia = colunas("Tipo", "Descrição", "Período:COMPETENCIA", "Situação", "Impede certidão", "Origem");

        var impedem = debitos.stream().filter(SituacaoFiscalService.DebitoConsolidado::impedeCertidao).toList();
        var pendenciasImpedem = pendencias.stream().filter(SituacaoFiscalService.PendenciaFiscal::impedeCertidao).toList();
        List<Secao> secoes = new ArrayList<>();
        secoes.add(secao("Itens que impedem a certidão", "Nenhum débito marcado como impeditivo nos documentos importados.", true,
                impedem, colunasDebito, linhaDebito, null,
                "Débitos que os documentos dos órgãos apontam como impeditivos da certidão negativa (ou positiva com efeitos de negativa)."));
        if (!pendenciasImpedem.isEmpty()) {
            secoes.add(secao("Pendências de declaração que impedem a certidão", null, true, pendenciasImpedem, colunasPendencia,
                    linhaPendencia, null, "Fundamento: " + SituacaoFiscalService.FUNDAMENTO_CERTIDAO + "."));
        }
        Map<String, List<SituacaoFiscalService.DebitoConsolidado>> porOrgao = debitos.stream().collect(Collectors.groupingBy(
                d -> d.orgaoCredor().name() + "|" + d.situacao().name().replace('_', ' ').toLowerCase(), TreeMap::new, Collectors.toList()));
        BigDecimal totalDebitos = somaDebitos(debitos);
        secoes.add(secao("Débitos por órgão e situação", "Nenhum débito nos documentos importados.", false, List.copyOf(porOrgao.entrySet()),
                colunas("Órgão", "Situação", "Débitos:INTEIRO", "Valor total:MOEDA"), e -> {
                    List<String> l = new ArrayList<>(List.of(e.getKey().split("\\|")));
                    l.add(String.valueOf(e.getValue().size()));
                    l.add(cru(somaDebitos(e.getValue())));
                    return l;
                }, List.of("Total", "", String.valueOf(debitos.size()), cru(totalDebitos)), null));
        if (!debitos.isEmpty()) {
            secoes.add(secao("Débitos", null, false, debitos, colunasDebito, linhaDebito, null, null));
        }
        secoes.add(secao("Parcelamentos e transações", "Nenhum parcelamento nos documentos importados.", false, parcelamentos,
                colunas("Órgão", "Número", "Modalidade", "Parcelas pagas", "Progresso:PERCENTUAL", "Saldo:MOEDA", "Situação", "Origem"),
                x -> linha(x.orgao().name(), Objects.toString(x.numero(), "—"), Objects.toString(x.modalidade(), "—"),
                        x.parcelasPagas() == null ? "—" : x.parcelasPagas() + (x.parcelasTotal() == null ? "" : " de " + x.parcelasTotal()),
                        x.parcelasPagas() == null || x.parcelasTotal() == null || x.parcelasTotal() == 0 ? "—"
                                : BigDecimal.valueOf(x.parcelasPagas() * 100L).divide(BigDecimal.valueOf(x.parcelasTotal()), 2, java.math.RoundingMode.HALF_EVEN).toPlainString(),
                        cru(x.saldo()), Objects.toString(x.situacao(), "—"), origem(x.origem())), null, null));
        secoes.add(secao("Pendências", "Nenhuma pendência nos documentos importados.", false, pendencias, colunasPendencia, linhaPendencia, null, null));
        secoes.add(secao("Capacidade de pagamento oficial (CAPAG)", "Nenhuma CAPAG nos documentos importados.", false, capag,
                colunas("Fonte", "Classe", "Capacidade:MOEDA", "Passivo:MOEDA", "Data:DATA", "Origem"),
                x -> linha(x.fonte(), Objects.toString(x.classe(), "—"), cru(x.capacidade()), cru(x.passivo()),
                        x.dataReferencia() == null ? "—" : x.dataReferencia().toString(), origem(x.origem())), null, null));
        BigDecimal saldoParcelado = parcelamentos.stream().map(SituacaoFiscalService.ParcelamentoFiscal::saldo).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Indicador> sumario = List.of(
                new Indicador("Débitos", cru(totalDebitos), Formato.MOEDA, debitos.size() + " débito(s)"),
                new Indicador("Impedem a certidão", String.valueOf(impedem.size() + pendenciasImpedem.size()), Formato.INTEIRO,
                        pendenciasImpedem.isEmpty() ? null : pendenciasImpedem.size() + " pendência(s) de declaração"),
                new Indicador("Saldo parcelado", cru(saldoParcelado), Formato.MOEDA, parcelamentos.size() + " parcelamento(s) ou transação(ões)"),
                new Indicador("Pendências", String.valueOf(pendencias.size()), Formato.INTEIRO, null),
                new Indicador("CAPAG", capag.stream().map(SituacaoFiscalService.CapacidadeOficial::classe).filter(Objects::nonNull)
                        .findFirst().orElse(ND), Formato.TEXTO, null));
        return new Corpo(sumario, secoes, documentos);
    }

    /**
     * Seção com tabela: sem itens, só o texto de vazio (e sem destaque). texto: a explicação quando há itens.
     */
    static <T> Secao secao(String titulo, String vazio, boolean destaque, List<T> itens, List<Coluna> colunas,
                           Function<T, List<String>> linha, List<String> totais, String texto) {
        return itens.isEmpty() ? new Secao(titulo, vazio, false, null, null)
                : new Secao(titulo, texto, destaque, new Tabela(colunas, itens.stream().map(linha).toList(), totais), null);
    }

    /** "Nome" (texto) ou "Nome:FORMATO". */
    static List<Coluna> colunas(String... especificacoes) {
        return Stream.of(especificacoes).map(e -> {
            int i = e.lastIndexOf(':');
            return i < 0 ? new Coluna(e, Formato.TEXTO) : new Coluna(e.substring(0, i), Formato.valueOf(e.substring(i + 1)));
        }).toList();
    }

    private static BigDecimal somaDebitos(List<SituacaoFiscalService.DebitoConsolidado> debitos) {
        return debitos.stream().map(SituacaoFiscalService.DebitoConsolidado::valorTotal).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------------------------------------------------------------- comum

    private List<Fonte> fontes(long clienteId, Set<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT id, tipo, nome_original_arquivo, periodo_inicio, periodo_fim, data_importacao, sha256 FROM documento_fiscal
                WHERE cliente_id = :cliente AND id IN (:ids) ORDER BY tipo, periodo_inicio NULLS LAST, id""",
                new MapSqlParameterSource("cliente", clienteId).addValue("ids", ids), (rs, i) -> new Fonte(rs.getLong(1), rs.getString(2),
                        rs.getString(3), periodo(rs.getDate(4), rs.getDate(5)), rs.getTimestamp(6).toLocalDateTime(), rs.getString(7)));
    }

    private static String periodo(java.sql.Date ini, java.sql.Date fim) {
        if (ini == null && fim == null) return "—";
        if (ini == null || fim == null) return Formatos.data((ini == null ? fim : ini).toLocalDate());
        return Formatos.data(ini.toLocalDate()) + " a " + Formatos.data(fim.toLocalDate());
    }

    /** Linha de tabela: aceita célula nula (List.of não aceita). */
    private static List<String> linha(String... celulas) {
        return Arrays.asList(celulas);
    }

    private static String periodo(Parametros p) {
        if (EnumSet.of(TipoRelatorio.SITUACAO_FISCAL, TipoRelatorio.CAPACIDADE, TipoRelatorio.DOSSIE_CAPAG).contains(p.tipo())) return "posição dos documentos importados";
        if (p.tipo() == TipoRelatorio.PROPOSTA_REGULARIZACAO) return "data-base da simulação";
        if (p.tipo() == TipoRelatorio.CARGA_TRIBUTARIA) {
            return p.de() == null && p.ate() == null ? "todos os exercícios importados"
                    : "exercícios " + (p.de() == null ? "…" : p.de().getYear()) + " a " + (p.ate() == null ? "…" : p.ate().getYear());
        }
        return p.de() == null && p.ate() == null ? "todas as competências importadas"
                : "competências " + (p.de() == null ? "…" : mes(p.de())) + " a " + (p.ate() == null ? "…" : mes(p.ate()));
    }

    /** Notas metodológicas fixas por tipo: de onde vem cada número e a base legal (docs/normativas). */
    static List<String> notas(TipoRelatorio tipo) {
        List<String> comuns = List.of(
                "Todos os números vêm de documentos importados no FiscalHub (escriturações SPED, declarações, guias e relatórios "
                        + "dos órgãos); nada é digitado ou estimado. O anexo de fontes lista cada documento com o SHA-256 do arquivo.",
                "Este relatório é uma fotografia: os dados usados ficam gravados com ele (snapshot), e uma nova emissão pode "
                        + "trazer números diferentes se novos documentos forem importados.");
        List<String> proprias = switch (tipo) {
            case TRIBUTOS -> List.of(
                    "Apurado: valor a recolher das escriturações fiscais vigentes (EFD-Contribuições, EFD ICMS/IPI, ECF). Declarado: "
                            + "DCTF e DCTFWeb; no ICMS, a conta corrente da SEFA-PR ou, sem ela, a própria EFD. Pago: DARF, "
                            + "relatório de pagamentos e GR-PR (parcelas de parcelamento ficam de fora).",
                    "O código de receita de cada declaração e pagamento é traduzido em tributo pela tabela de códigos de receita "
                            + "da RFB e da SEFA-PR; códigos ainda não confirmados em fonte oficial aparecem como ilustrativos.",
                    "Acréscimos moratórios (regra federal, Lei 9.430/96, art. 61): multa de mora de 0,33% por dia a partir do dia "
                            + "seguinte ao vencimento, limitada a 20%; juros pela soma simples da SELIC mensal do mês seguinte ao "
                            + "vencimento até o mês anterior ao pagamento, mais 1% no mês do pagamento. Débito em aberto: calculados "
                            + "até a data de emissão. Sem SELIC carregada, os acréscimos não são calculados.");
            case CARGA_TRIBUTARIA -> List.of(
                    "Receita bruta (conta referencial 3.01.01.01.01) e lucro antes do IRPJ e da CSLL (3.01) da DRE referencial "
                            + "montada da ECD; sem ECD no exercício, do L300/P150 da ECF (soma dos períodos de apuração).",
                    "Tributos: valor a recolher das apurações vigentes do exercício (após créditos, deduções e estimativas), "
                            + "por tributo. Contribuições previdenciárias e retenções declaradas na DCTFWeb não entram (não são apurações).",
                    "A razão só é apresentada com todos os insumos: a receita do exercício e os 12 meses de cada escrituração fiscal "
                            + "que o cliente tem no FiscalHub. Faltando algum, a linha mostra \"não disponível\" e o que importar; "
                            + "com prejuízo antes do IR, a razão IRPJ + CSLL ÷ LAIR não se aplica.");
            case CAPACIDADE, DOSSIE_CAPAG -> List.of(
                    "Três camadas separadas, sem misturar: a CAPAG oficial (importada do Regularize, nunca estimada), as "
                            + "estimativas do consultor (CAPAG pelos dois métodos e recálculo da classificação do PR pela norma) e a "
                            + "análise gerencial, que não é critério oficial.",
                    "Onde a norma não fixa a fórmula (GRF, PLR, faixas A-D da CAPAG, método que prevalece), o cálculo usa os "
                            + "parâmetros do escritório listados neste relatório e o resultado é rotulado como estimativa.",
                    "Fontes normativas: Portaria PGFN 6.757/2022 (arts. 21, 24, 25 e 30, com as Portarias 1.241/2023 e "
                            + "1.457/2024) e Resolução Conjunta PGE/SEFA 01/2025 (com a RC 15/2026), textos baixados em 28/10/2026 "
                            + "com URL e SHA-256 na tabela de regras.");
            case PROPOSTA_REGULARIZACAO -> List.of(
                    "Exato × projeção: principal, multa, juros e encargo vêm dos documentos importados e da SELIC publicada pelo "
                            + "Banco Central; as parcelas que vencem depois do último mês com SELIC usam a taxa projetada indicada "
                            + "(parâmetro do escritório ou média das últimas 12) e aparecem como projetadas no cronograma.",
                    "Regras de cada modalidade (prazos, parcela mínima, entrada, descontos, vedações, garantia, rescisão) como dado "
                            + "versionado, com o artigo e a fonte de cada uma; a versão usada está no anexo. Fonte secundária: o texto "
                            + "oficial não pôde ser baixado e a cópia usada está identificada.",
                    "Desconto da transação: o máximo do edital pela classe da CAPAG oficial importada; o desconto efetivo é "
                            + "definido pela PGFN conforme a capacidade de pagamento. A CAPAG estimada aparece só como cenário "
                            + "rotulado e nunca é a recomendação.");
            case SITUACAO_FISCAL -> List.of(
                    "Débitos, parcelamentos, pendências e CAPAG dos relatórios dos órgãos importados (Relatório de Situação Fiscal "
                            + "da RFB, extrato do Regularize/PGFN, conta corrente e RTI da SEFA-PR), o mais recente de cada tipo. "
                            + "A mesma dívida em duas fontes é um débito só, com as duas origens.",
                    "Itens que impedem a certidão: débitos que o próprio documento do órgão marca como impeditivos.",
                    "CAPAG: a classe e a capacidade vêm do documento da PGFN (Portaria PGFN 6.757/2022); o FiscalHub não as estima.");
        };
        List<String> todas = new ArrayList<>(proprias);
        todas.addAll(comuns);
        return todas;
    }

    private static BigDecimal soma(List<ItemConciliacao> itens, Function<ItemConciliacao, BigDecimal> f) {
        return itens.stream().map(f).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String cru(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    private static String tributo(ItemConciliacao i) {
        return i.tributo().name().replace('_', '-') + (i.uf() == null ? "" : " " + i.uf());
    }

    private static String esfera(String e) {
        return "FEDERAL".equals(e) ? "Federal" : "Estadual";
    }

    private static String mes(YearMonth m) {
        return String.format("%02d/%d", m.getMonthValue(), m.getYear());
    }

    private static String sha256Recurso(String caminho) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new ClassPathResource(caminho).getContentAsByteArray()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
