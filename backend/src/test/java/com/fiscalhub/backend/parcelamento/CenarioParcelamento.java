package com.fiscalhub.backend.parcelamento;

import com.fiscalhub.backend.entity.IndiceMonetario;
import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.entity.enums.TipoIndice;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.parcelamento.MotorParcelamento.DebitoSimulado;
import com.fiscalhub.backend.parcelamento.MotorParcelamento.Pedido;
import com.fiscalhub.backend.parcelamento.MotorParcelamento.ResultadoModalidade;
import com.fiscalhub.backend.repository.IndiceMonetarioRepository;
import com.fiscalhub.backend.service.IndiceMonetarioService;
import com.fiscalhub.backend.situacao.AcrescimosMoratorios;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fixture dos testes do motor: débitos, pedido e índices em memória, com as regras reais de resources/parcelamento.
 * Padrão: um débito de R$ 10.000,00 vencido em 10/01/2024, 10 parcelas. O IndiceMonetarioService é o real, sobre um
 * repositório fake em memória.
 */
final class CenarioParcelamento {

    static final LocalDate VENCIMENTO = LocalDate.of(2024, 1, 10);

    private final OrgaoCredor orgao;
    private final TipoTributo tributo;
    private final List<IndiceMonetario> indices = new ArrayList<>();
    private final List<DebitoSimulado> outros = new ArrayList<>();
    private BigDecimal valor = new BigDecimal("10000.00");
    private LocalDate dataCalculo = VENCIMENTO.plusDays(10);
    private Integer parcelas = 10;
    private String capagOficial;
    private String capagEstimada;
    private BigDecimal selicProjetada;
    private BigDecimal parcelaSustentavel;
    private boolean reparcelamento;
    private UnaryOperator<DebitoSimulado> ajuste = d -> d;

    final IndiceMonetarioRepository repositorio = mock(IndiceMonetarioRepository.class);

    private CenarioParcelamento(OrgaoCredor orgao, TipoTributo tributo) {
        this.orgao = orgao;
        this.tributo = tributo;
        when(repositorio.findByTipoAndAnoAndMes(any(), anyInt(), anyInt())).thenAnswer(inv -> buscar(
                inv.getArgument(0), YearMonth.of(inv.<Integer>getArgument(1), inv.<Integer>getArgument(2))));
        when(repositorio.findByTipoAndPeriodo(any(), anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(inv -> periodo(
                inv.getArgument(0),
                YearMonth.of(inv.<Integer>getArgument(1), inv.<Integer>getArgument(2)),
                YearMonth.of(inv.<Integer>getArgument(3), inv.<Integer>getArgument(4))));
    }

    /** Débito na RFB com SELIC de 1,00% a.m. de 2024-01 a 2025-12. */
    static CenarioParcelamento federal() {
        return new CenarioParcelamento(OrgaoCredor.RFB, TipoTributo.IRPJ)
                .selic(YearMonth.of(2024, 1), YearMonth.of(2025, 12), "1.00");
    }

    /** Inscrição na PGFN (valores do documento: ver {@link #inscricao}), SELIC de 1,00% a.m. de 2024-01 a 2026-12. */
    static CenarioParcelamento pgfn() {
        return new CenarioParcelamento(OrgaoCredor.PGFN, TipoTributo.IPI)
                .selic(YearMonth.of(2024, 1), YearMonth.of(2026, 12), "1.00");
    }

    /** Sem nenhum índice cadastrado. */
    static CenarioParcelamento federalSemSelic() {
        return new CenarioParcelamento(OrgaoCredor.RFB, TipoTributo.IRPJ);
    }

    /** ICMS declarado na EFD (SEFA-PR) com UFPR 100,00 (jan/24), 101,00 (fev/24), 110,00 (fev/25) e SELIC 1,00% a.m. */
    static CenarioParcelamento estadualPR() {
        return new CenarioParcelamento(OrgaoCredor.SEFA_PR, TipoTributo.ICMS)
                .ufpr(YearMonth.of(2024, 1), "100.00")
                .ufpr(YearMonth.of(2024, 2), "101.00")
                .ufpr(YearMonth.of(2025, 2), "110.00")
                .selic(YearMonth.of(2024, 1), YearMonth.of(2025, 12), "1.00");
    }

    CenarioParcelamento diasDeAtraso(int dias) {
        this.dataCalculo = VENCIMENTO.plusDays(dias);
        return this;
    }

    CenarioParcelamento dataBase(LocalDate data) {
        this.dataCalculo = data;
        return this;
    }

    CenarioParcelamento valor(String valor) {
        this.valor = new BigDecimal(valor);
        return this;
    }

    CenarioParcelamento parcelas(Integer parcelas) {
        this.parcelas = parcelas;
        return this;
    }

    CenarioParcelamento capag(String oficial, String estimada) {
        this.capagOficial = oficial;
        this.capagEstimada = estimada;
        return this;
    }

    CenarioParcelamento selicProjetada(String taxa) {
        this.selicProjetada = new BigDecimal(taxa);
        return this;
    }

    CenarioParcelamento parcelaSustentavel(String valor) {
        this.parcelaSustentavel = new BigDecimal(valor);
        return this;
    }

    CenarioParcelamento reparcelamento() {
        this.reparcelamento = true;
        return this;
    }

    /** Ajusta o débito principal (inscrição, negociação, tributo...). */
    CenarioParcelamento debito(UnaryOperator<DebitoSimulado> ajuste) {
        this.ajuste = ajuste;
        return this;
    }

    /** Valores da inscrição como no extrato (principal, multa, juros, encargo), inscrita em dataInscricao. */
    CenarioParcelamento inscricao(String principal, String multa, String juros, String encargo, LocalDate dataInscricao) {
        return debito(d -> new DebitoSimulado(d.id(), d.orgao(), d.tributo(), d.descricao(), null, new BigDecimal(principal),
                new BigDecimal(multa), new BigDecimal(juros), new BigDecimal(encargo), null, dataCalculo, "90 3 24 000001-00",
                dataInscricao, null, null, false, false, null));
    }

    CenarioParcelamento maisUmDebito(String valor, LocalDate vencimento) {
        outros.add(debito(outros.size() + 2L, new BigDecimal(valor), vencimento));
        return this;
    }

    /** Taxa mensal (%) igual em todos os meses do intervalo (inclusive); substitui valores já cadastrados. */
    CenarioParcelamento selic(YearMonth de, YearMonth ate, String taxa) {
        for (YearMonth m = de; !m.isAfter(ate); m = m.plusMonths(1)) {
            indice(TipoIndice.SELIC, m, taxa);
        }
        return this;
    }

    CenarioParcelamento ufpr(YearMonth mes, String valor) {
        return indice(TipoIndice.UFPR, mes, valor);
    }

    ResultadoModalidade calcularFederal() {
        return calcular("RFB_ORDINARIO");
    }

    ResultadoModalidade calcularPR() {
        return calcular("SEFA_PR_ORDINARIO");
    }

    ResultadoModalidade calcular(String modalidade) {
        return motor().calcular(modalidade, debitos(), pedido());
    }

    List<ResultadoModalidade> simular() {
        return motor().simular(debitos(), pedido());
    }

    private MotorParcelamento motor() {
        RegrasParcelamento regras = new RegrasParcelamento();
        IndiceMonetarioService servico = new IndiceMonetarioService(repositorio);
        return new MotorParcelamento(new AcrescimosMoratorios(servico, regras), servico, regras);
    }

    private List<DebitoSimulado> debitos() {
        List<DebitoSimulado> d = new ArrayList<>(List.of(ajuste.apply(debito(1L, valor, VENCIMENTO))));
        d.addAll(outros);
        return d;
    }

    private Pedido pedido() {
        return new Pedido(dataCalculo, parcelas, false, capagOficial, capagEstimada, selicProjetada, parcelaSustentavel, reparcelamento);
    }

    private DebitoSimulado debito(long id, BigDecimal valor, LocalDate vencimento) {
        return new DebitoSimulado(id, orgao, tributo, tributo + " " + id, vencimento, valor, null, null, null, null, null, null, null,
                null, null, false, false, null);
    }

    private CenarioParcelamento indice(TipoIndice tipo, YearMonth mes, String valor) {
        indices.removeIf(i -> i.getTipo() == tipo && mesDe(i).equals(mes));
        IndiceMonetario i = new IndiceMonetario();
        i.setTipo(tipo);
        i.setAno(mes.getYear());
        i.setMes(mes.getMonthValue());
        i.setValor(new BigDecimal(valor));
        indices.add(i);
        return this;
    }

    private Optional<IndiceMonetario> buscar(TipoIndice tipo, YearMonth mes) {
        return indices.stream().filter(i -> i.getTipo() == tipo && mesDe(i).equals(mes)).findFirst();
    }

    private List<IndiceMonetario> periodo(TipoIndice tipo, YearMonth de, YearMonth ate) {
        return indices.stream()
                .filter(i -> i.getTipo() == tipo && !mesDe(i).isBefore(de) && !mesDe(i).isAfter(ate))
                .sorted(Comparator.comparing(CenarioParcelamento::mesDe))
                .toList();
    }

    private static YearMonth mesDe(IndiceMonetario i) {
        return YearMonth.of(i.getAno(), i.getMes());
    }
}
