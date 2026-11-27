package com.fiscalhub.backend.capacidade;

import com.fiscalhub.backend.capacidade.DfcDiretaService.Dfc;
import com.fiscalhub.backend.capacidade.MetodologiaCapacidade.*;
import com.fiscalhub.backend.capacidade.ResultadoCapacidade.CriterioCapacidade;
import com.fiscalhub.backend.capacidade.ResultadoCapacidade.Rotulo;
import com.fiscalhub.backend.contabil.DemonstracaoService;
import com.fiscalhub.backend.relatorio.ModeloRelatorio.Formato;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.AcessoRecursoService;
import com.fiscalhub.backend.situacao.SituacaoFiscalService;
import com.fiscalhub.backend.situacao.SituacaoFiscalService.OrigemValor;
import com.fiscalhub.backend.sped.LeitorSped;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Aba Capacidade: monta os insumos uma vez, a partir dos serviços e dados que já existem (demonstrações referenciais,
 * DFC direta, situação fiscal, detalhes das dívidas, situação cadastral, faturamento da EFD), e roda as metodologias. A
 * CAPAG oficial é a importada (Regularize), sem recálculo. Nenhuma regra é repetida no frontend.
 */
@Service
@Transactional(readOnly = true)
public class CapacidadeService {

    /** oficial: CAPAG importada (null sem documento); metodologias: estimada, PR e gerencial, nessa ordem. */
    public record Capacidade(ResultadoCapacidade capagOficial, List<ResultadoCapacidade> metodologias,
                             List<AnaliseGerencial.IndicadoresGerenciais> indicadores, ParametrosCapacidade.ParametrosEscritorio parametros) {
    }

    /** Cartão da visão geral. */
    public record ResumoCapacidade(String capagOficial, String capagEstimada, boolean revisaoVantajosa, String classePr, String classePrOficial,
                         BigDecimal parcelaSustentavel) {
    }

    private final AcessoRecursoService acesso;
    private final DemonstracaoService demonstracoes;
    private final DfcDiretaService dfc;
    private final SituacaoFiscalService situacao;
    private final ParametrosCapacidade parametros;
    private final RegrasCapacidade regras;
    private final ReleituraSped releitura;
    private final JdbcTemplate jdbc;
    private final List<MetodologiaCapacidade> metodologias;
    private final Map<Long, BigDecimal> faturamentoPorEscrituracao = Collections.synchronizedMap(new HashMap<>());

    public CapacidadeService(AcessoRecursoService acesso, DemonstracaoService demonstracoes, DfcDiretaService dfc, SituacaoFiscalService situacao,
                             ParametrosCapacidade parametros, RegrasCapacidade regras, ReleituraSped releitura, JdbcTemplate jdbc,
                             List<MetodologiaCapacidade> metodologias) {
        this.acesso = acesso;
        this.demonstracoes = demonstracoes;
        this.dfc = dfc;
        this.situacao = situacao;
        this.parametros = parametros;
        this.regras = regras;
        this.releitura = releitura;
        this.jdbc = jdbc;
        List<String> ordem = List.of("CAPAG_ESTIMADA", "PR", "GERENCIAL");
        this.metodologias = metodologias.stream().sorted(Comparator.comparingInt(m -> ordem.indexOf(m.chave()))).toList();
    }

    public Capacidade capacidade(long clienteId, UsuarioAutenticado ua) {
        Insumos in = insumos(clienteId, ua, Simulacao.NENHUMA);
        List<ResultadoCapacidade> resultados = metodologias.stream().map(m -> m.calcular(in)).toList();
        List<AnaliseGerencial.IndicadoresGerenciais> indicadores = in.dre().exercicios().stream().map(e -> e.exercicio()).distinct().sorted()
                .map(a -> AnaliseGerencial.indicadores(in, a)).toList();
        return new Capacidade(capagOficial(in), resultados, indicadores, in.parametros());
    }

    /** Recalcula a classificação do PR com CDAs quitadas ou parceladas (nada é gravado). */
    public ResultadoCapacidade simularPr(long clienteId, Simulacao simulacao, UsuarioAutenticado ua) {
        Insumos in = insumos(clienteId, ua, simulacao);
        return metodologias.stream().filter(m -> "PR".equals(m.chave())).findFirst().orElseThrow().calcular(in);
    }

    public ResumoCapacidade resumo(long clienteId, UsuarioAutenticado ua) {
        Capacidade c = capacidade(clienteId, ua);
        ResultadoCapacidade est = c.metodologias().stream().filter(m -> "CAPAG_ESTIMADA".equals(m.metodologia())).findFirst().orElseThrow();
        ResultadoCapacidade pr = c.metodologias().stream().filter(m -> "PR".equals(m.metodologia())).findFirst().orElseThrow();
        ResultadoCapacidade ger = c.metodologias().stream().filter(m -> "GERENCIAL".equals(m.metodologia())).findFirst().orElseThrow();
        String oficial = c.capagOficial() == null ? null : c.capagOficial().classe();
        return new ResumoCapacidade(oficial, est.classe(), CapagEstimada.revisaoVantajosa(est.classe(), oficial), pr.classe(),
                (String) pr.parametros().get("oficial"), ger.valor());
    }

    /** Insumos de todas as metodologias (sem nova checagem de acesso depois da primeira). */
    public Insumos insumos(long clienteId, UsuarioAutenticado ua, Simulacao simulacao) {
        acesso.cliente(clienteId, ua);
        LocalDate hoje = LocalDate.now();
        var dre = demonstracoes.comparativo(clienteId, "DRE", "REFERENCIAL", null, ua);
        var bp = demonstracoes.comparativo(clienteId, "BP", "REFERENCIAL", null, ua);
        Map<Integer, Dfc> dfcs = new TreeMap<>();
        for (int ano : dfc.exercicios(clienteId, ua)) {
            dfc.dfc(clienteId, ano, ua.escritorioId()).ifPresent(d -> dfcs.put(ano, d));
        }
        Map<String, SituacaoCadastral> situacoes = situacoes(clienteId);
        LocalDate dataPr = situacoes.containsKey("SEFA_PR") ? situacoes.get("SEFA_PR").dataReferencia() : hoje;
        return new Insumos(clienteId, hoje, dataPr, dre, bp, dfcs, situacao.debitos(clienteId, null, null, ua),
                situacao.parcelamentos(clienteId, ua), situacao.pagamentos(clienteId, ua), situacao.capacidades(clienteId, ua),
                detalhes(clienteId), situacoes, faturamento(clienteId, dataPr), parametros.consultar(ua.escritorioId()), simulacao);
    }

    private ResultadoCapacidade capagOficial(Insumos in) {
        return in.capacidadesOficiais().stream().filter(c -> "PGFN".equals(c.fonte())).findFirst().map(c -> ResultadoCapacidade.de(
                "CAPAG_OFICIAL", "CAPAG oficial (Regularize)", Rotulo.OFICIAL, c.capacidade(), c.classe(), List.of(
                        new CriterioCapacidade("Capacidade de pagamento presumida", ResultadoCapacidade.cru(c.capacidade()), Formato.MOEDA, null, null,
                                "Portaria PGFN 6.757/2022, arts. 21 a 23 (calculada pela PGFN)", List.of(c.origem()), null),
                        new CriterioCapacidade("Passivo na PGFN", ResultadoCapacidade.cru(c.passivo()), Formato.MOEDA, null, null,
                                "documento do órgão", List.of(c.origem()), null),
                        new CriterioCapacidade("Razão capacidade ÷ passivo", ResultadoCapacidade.cru(c.razao()), Formato.TEXTO, null,
                                Boolean.FALSE.equals(c.classeConsistente()) ? "classe inconsistente com as faixas do escritório" : "classe consistente",
                                "faixas A-D: parâmetro do escritório (a Portaria não fixa os múltiplos)", List.of(c.origem()), null)),
                List.of(), Map.of(), List.of("Portaria PGFN 6.757/2022"), List.of("Classe e capacidade importadas do Regularize, como no documento."),
                Boolean.FALSE.equals(c.classeConsistente()) ? List.of("A classe do documento não bate com a razão capacidade ÷ passivo.") : List.of()))
                .orElse(null);
    }

    /** Detalhes das dívidas nos documentos vigentes, juntando as fontes pelo número (o mais recente primeiro). */
    private Map<String, Detalhe> detalhes(long clienteId) {
        Map<String, Detalhe> m = new LinkedHashMap<>();
        jdbc.query("""
                SELECT dd.numero, dd.data_inscricao, dd.origem_divida, dd.ajuizamento, dd.parcelamentos, dd.garantia, dd.suspensao_desde,
                       dd.classe_oficial, dd.pagina, dd.linha, dd.trecho, e.documento_id, doc.nome_original_arquivo, doc.tipo
                FROM divida_detalhe dd JOIN escrituracao_contabil e ON e.id = dd.escrituracao_id AND e.situacao = 'VIGENTE'
                JOIN documento_fiscal doc ON doc.id = e.documento_id
                WHERE dd.cliente_id = ? ORDER BY dd.data_referencia DESC, dd.id""", rs -> {
            OrigemValor o = new OrigemValor(rs.getLong(12), rs.getString(13), rs.getString(14), (Integer) rs.getObject(9), null,
                    rs.getLong(10), rs.getString(11));
            Detalhe novo = new Detalhe(rs.getString(1), data(rs.getDate(2)), rs.getString(3), rs.getString(4), (Integer) rs.getObject(5),
                    rs.getString(6), data(rs.getDate(7)), rs.getString(8), List.of(o));
            m.merge(novo.numero(), novo, (a, b) -> new Detalhe(a.numero(), nn(a.dataInscricao(), b.dataInscricao()),
                    nn(a.origemDivida(), b.origemDivida()), nn(a.ajuizamento(), b.ajuizamento()), nn(a.parcelamentos(), b.parcelamentos()),
                    nn(a.garantia(), b.garantia()), nn(a.suspensaoDesde(), b.suspensaoDesde()), nn(a.classeOficial(), b.classeOficial()),
                    concat(a.origens(), b.origens())));
        }, clienteId);
        return m;
    }

    private Map<String, SituacaoCadastral> situacoes(long clienteId) {
        Map<String, SituacaoCadastral> m = new HashMap<>();
        jdbc.query("""
                SELECT s.orgao, s.situacao, s.desde, s.data_referencia, s.pagina, s.linha, s.trecho, e.documento_id,
                       doc.nome_original_arquivo, doc.tipo
                FROM situacao_cadastral s JOIN escrituracao_contabil e ON e.id = s.escrituracao_id AND e.situacao = 'VIGENTE'
                JOIN documento_fiscal doc ON doc.id = e.documento_id
                WHERE s.cliente_id = ? ORDER BY s.data_referencia DESC, s.id DESC""", rs -> {
            m.putIfAbsent(rs.getString(1), new SituacaoCadastral(rs.getString(1), rs.getString(2), data(rs.getDate(3)),
                    rs.getDate(4).toLocalDate(), new OrigemValor(rs.getLong(8), rs.getString(9), rs.getString(10), (Integer) rs.getObject(5),
                    null, rs.getLong(6), rs.getString(7))));
        }, clienteId);
        return m;
    }

    /**
     * Faturamento declarado em 12 meses (Resolução Conjunta PGE/SEFA 01/2025, art. 1º, XII): soma do C190 das saídas
     * (CFOP 5, 6 e 7) da EFD ICMS/IPI vigente de cada mês. Último mês: o mais recente cuja EFD já venceu na data do
     * cálculo (dia de entrega da regra PR_DEVEDOR_INDICES; interpretação registrada na regra).
     */
    Faturamento faturamento(long clienteId, LocalDate dataCalculo) {
        var regra = regras.vigente("PR_DEVEDOR_INDICES", dataCalculo);
        int dia = regra.parametros().get("prazoEntregaEfdDia").asInt();
        int meses = regra.parametros().get("faturamentoMeses").asInt();
        YearMonth ate = YearMonth.from(dataCalculo).minusMonths(dataCalculo.getDayOfMonth() >= dia ? 1 : 2);
        YearMonth de = ate.minusMonths(meses - 1L);
        Map<YearMonth, Map<String, Object>> escrituracoes = new HashMap<>();
        jdbc.queryForList("""
                SELECT DISTINCT ON (e.data_inicio) e.id, e.data_inicio, e.documento_id, doc.nome_original_arquivo
                FROM escrituracao_contabil e JOIN documento_fiscal doc ON doc.id = e.documento_id
                WHERE e.cliente_id = ? AND e.origem = 'EFD_ICMS_IPI' AND e.situacao = 'VIGENTE' AND e.data_inicio BETWEEN ? AND ?
                ORDER BY e.data_inicio, e.id DESC""", clienteId, Date.valueOf(de.atDay(1)), Date.valueOf(ate.atDay(1)))
                .forEach(r -> escrituracoes.put(YearMonth.from(((Date) r.get("data_inicio")).toLocalDate()), r));
        List<YearMonth> faltando = new ArrayList<>();
        List<OrigemValor> origens = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (YearMonth m = de; !m.isAfter(ate); m = m.plusMonths(1)) {
            Map<String, Object> e = escrituracoes.get(m);
            if (e == null) {
                faltando.add(m);
                continue;
            }
            long id = ((Number) e.get("id")).longValue();
            total = total.add(faturamentoPorEscrituracao.computeIfAbsent(id, this::saidas));
            origens.add(new OrigemValor(((Number) e.get("documento_id")).longValue(), (String) e.get("nome_original_arquivo"),
                    "EFD_ICMS_IPI", null, "C190 (saídas)", null, null));
        }
        return new Faturamento(faltando.isEmpty() ? total.setScale(2, RoundingMode.HALF_EVEN) : null, de, ate, origens, faltando);
    }

    /** Soma do VL_OPR do C190 com CFOP de saída (5, 6, 7), relida do arquivo da EFD. */
    private BigDecimal saidas(long escrituracaoId) {
        BigDecimal[] soma = {BigDecimal.ZERO};
        releitura.ler(escrituracaoId, Set.of("C190"), (c, linha) -> {
            if (c.length > 5 && !c[3].isEmpty() && "567".indexOf(c[3].charAt(0)) >= 0) {
                soma[0] = soma[0].add(Objects.requireNonNullElse(LeitorSped.decimal(c[5]), BigDecimal.ZERO));
            }
        });
        return soma[0];
    }

    private static LocalDate data(Date d) {
        return d == null ? null : d.toLocalDate();
    }

    private static <T> T nn(T a, T b) {
        return a != null ? a : b;
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> l = new ArrayList<>(a);
        l.addAll(b);
        return l;
    }
}
