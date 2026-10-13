package com.fiscalhub.backend.service.calculo;

import com.fiscalhub.backend.dto.SimulacaoRequestDTO;
import com.fiscalhub.backend.dto.SimulacaoResultDTO;
import com.fiscalhub.backend.entity.Declaracao;
import com.fiscalhub.backend.entity.Empresa;
import com.fiscalhub.backend.entity.IndiceMonetario;
import com.fiscalhub.backend.entity.enums.ModalidadeParcelamento;
import com.fiscalhub.backend.entity.enums.TipoIndice;
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
 * Fixture dos testes do motor de parcelamento: débito, requisição e índices em memória.
 * Padrão: débito de R$ 10.000,00 vencido em 10/01/2024, 10 parcelas, sem entrada.
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
        return new CalculadoraReceitaFederalService(new IndiceMonetarioService(repositorio)).calcular(declaracao(), request());
    }

    SimulacaoResultDTO calcularPR() {
        return new CalculadoraReceitaEstadualPRService(new IndiceMonetarioService(repositorio)).calcular(declaracao(), request());
    }

    private Declaracao declaracao() {
        Empresa empresa = new Empresa();
        empresa.setRazaoSocial("Empresa Teste Ltda");
        empresa.setCnpj("11222333000181");
        Declaracao d = new Declaracao();
        d.setId(1L);
        d.setValorTotal(valor);
        d.setDataVencimento(VENCIMENTO);
        d.setEmpresa(empresa);
        return d;
    }

    private SimulacaoRequestDTO request() {
        SimulacaoRequestDTO r = new SimulacaoRequestDTO();
        r.setDeclaracaoId(1L);
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
