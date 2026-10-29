package com.fiscalhub.backend.service.calculo;

import com.fiscalhub.backend.dto.SimulacaoRequestDTO;
import com.fiscalhub.backend.dto.SimulacaoResultDTO;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.Debito;
import com.fiscalhub.backend.entity.IndiceMonetario;
import com.fiscalhub.backend.entity.enums.ModalidadeParcelamento;
import com.fiscalhub.backend.entity.enums.TipoIndice;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.repository.IndiceMonetarioRepository;
import com.fiscalhub.backend.service.IndiceMonetarioService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fixture dos testes do motor de parcelamento: débitos, requisição e índices em memória.
 * Padrão: um débito de R$ 10.000,00 vencido em 10/01/2024, 10 parcelas, sem entrada.
 * O IndiceMonetarioService é o real, sobre um repositório fake em memória.
 */
final class CenarioParcelamento {

    static final LocalDate VENCIMENTO = LocalDate.of(2024, 1, 10);

    private final ModalidadeParcelamento modalidade;
    private final List<IndiceMonetario> indices = new ArrayList<>();
    private BigDecimal valor = new BigDecimal("10000.00");
    private LocalDate dataCalculo = VENCIMENTO.plusDays(10);
    private int parcelas = 10;
    private boolean entrada;
    private final List<Debito> outrosDebitos = new ArrayList<>();

    final IndiceMonetarioRepository repositorio = mock(IndiceMonetarioRepository.class);

    private CenarioParcelamento(ModalidadeParcelamento modalidade) {
        this.modalidade = modalidade;
        when(repositorio.findByTipoAndAnoAndMes(any(), anyInt(), anyInt())).thenAnswer(inv -> buscar(
                inv.getArgument(0), YearMonth.of(inv.<Integer>getArgument(1), inv.<Integer>getArgument(2))));
        when(repositorio.findByTipoAndPeriodo(any(), anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(inv -> periodo(
                inv.getArgument(0),
                YearMonth.of(inv.<Integer>getArgument(1), inv.<Integer>getArgument(2)),
                YearMonth.of(inv.<Integer>getArgument(3), inv.<Integer>getArgument(4))));
    }

    /** Parcelamento ordinário RFB com SELIC de 1,00% a.m. de 2024-01 a 2025-12. */
    static CenarioParcelamento federal() {
        return new CenarioParcelamento(ModalidadeParcelamento.ORDINARIO_RFB)
                .selic(YearMonth.of(2024, 1), YearMonth.of(2025, 12), "1.00");
    }

    /** PERT (única modalidade federal que hoje aplica entrada). */
    static CenarioParcelamento federalPert() {
        return new CenarioParcelamento(ModalidadeParcelamento.PERT_RFB)
                .selic(YearMonth.of(2024, 1), YearMonth.of(2025, 12), "1.00");
    }

    /** Sem nenhum índice cadastrado. */
    static CenarioParcelamento federalSemSelic() {
        return new CenarioParcelamento(ModalidadeParcelamento.ORDINARIO_RFB);
    }

    /** Parcelamento ordinário SEFA-PR com UFPR 100,00 (jan/24), 101,00 (fev/24), 110,00 (fev/25) e SELIC 1,00% a.m. */
    static CenarioParcelamento estadualPR() {
        return new CenarioParcelamento(ModalidadeParcelamento.ORDINARIO_SEFA_PR)
                .ufpr(YearMonth.of(2024, 1), "100.00")
                .ufpr(YearMonth.of(2024, 2), "101.00")
                .ufpr(YearMonth.of(2025, 2), "110.00")
                .selic(YearMonth.of(2024, 1), YearMonth.of(2025, 12), "1.00");
    }

    CenarioParcelamento diasDeAtraso(int dias) {
        this.dataCalculo = VENCIMENTO.plusDays(dias);
        return this;
    }

    CenarioParcelamento valor(String valor) {
        this.valor = new BigDecimal(valor);
        return this;
    }

    CenarioParcelamento parcelas(int parcelas) {
        this.parcelas = parcelas;
        return this;
    }

    /** Acrescenta outro débito do mesmo cliente à simulação. */
    CenarioParcelamento maisUmDebito(String valor, LocalDate vencimento) {
        outrosDebitos.add(debito(outrosDebitos.size() + 2L, new BigDecimal(valor), vencimento));
        return this;
    }

    CenarioParcelamento comEntrada() {
        this.entrada = true;
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

    SimulacaoResultDTO calcularFederal() {
        return new CalculadoraReceitaFederalService(new IndiceMonetarioService(repositorio)).calcular(entrada());
    }

    SimulacaoResultDTO calcularPR() {
        return new CalculadoraReceitaEstadualPRService(new IndiceMonetarioService(repositorio)).calcular(entrada());
    }

    private EntradaCalculo entrada() {
        Cliente cliente = new Cliente();
        cliente.setId(1L);
        cliente.setRazaoSocial("Cliente Teste Ltda");
        cliente.setCnpj("11222333000181");
        List<Debito> debitos = new ArrayList<>(List.of(debito(1L, valor, VENCIMENTO)));
        debitos.addAll(outrosDebitos);
        return new EntradaCalculo(cliente, debitos, request());
    }

    private static Debito debito(Long id, BigDecimal valor, LocalDate vencimento) {
        Debito d = new Debito();
        d.setId(id);
        d.setTributo(TipoTributo.IRPJ);
        d.setValorPrincipal(valor);
        d.setDataVencimento(vencimento);
        return d;
    }

    private SimulacaoRequestDTO request() {
        SimulacaoRequestDTO r = new SimulacaoRequestDTO();
        r.setClienteId(1L);
        r.setModalidade(modalidade);
        r.setNumeroParcelas(parcelas);
        r.setDataCalculo(dataCalculo);
        r.setIncluirEntrada(entrada);
        return r;
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
