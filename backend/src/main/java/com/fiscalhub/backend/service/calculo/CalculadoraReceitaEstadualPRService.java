package com.fiscalhub.backend.service.calculo;

import com.fiscalhub.backend.dto.EtapaCalculoDTO;
import com.fiscalhub.backend.dto.ParcelaDTO;
import com.fiscalhub.backend.dto.SimulacaoRequestDTO;
import com.fiscalhub.backend.dto.SimulacaoResultDTO;
import com.fiscalhub.backend.entity.Declaracao;
import com.fiscalhub.backend.entity.enums.ModalidadeParcelamento;
import com.fiscalhub.backend.entity.enums.OrgaoReceita;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.service.IndiceMonetarioService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Engine de cálculo para parcelamentos da Receita Estadual do Paraná (SEFA-PR).
 *
 * Referências legais:
 * - RICMS/PR - Regulamento do ICMS do Paraná
 * - Lei Estadual 11.580/96 e alterações
 * - Portarias SEFA-PR sobre parcelamento de débitos estaduais
 *
 * Regras implementadas:
 * - Atualização monetária pela UFPR (Unidade Fiscal Padrão do Paraná)
 * - Multa de mora: 0,33%/dia (até 30 dias) ou 20% (acima de 30 dias)
 * - Juros de mora: 1% ao mês (simplex, sobre o valor atualizado)
 * - Parcela mínima: R$ 200,00
 * - Máximo: 60 parcelas
 */
@Service
public class CalculadoraReceitaEstadualPRService implements CalculoParcelamentoStrategy {

    private static final BigDecimal PARCELA_MINIMA = new BigDecimal("200.00");
    private static final int MAX_PARCELAS = 60;
    private static final BigDecimal MULTA_MORA_MAX = new BigDecimal("20.00");
    private static final BigDecimal MULTA_MORA_DIARIA = new BigDecimal("0.33");
    private static final BigDecimal JUROS_MENSAIS = new BigDecimal("1.00"); // 1% a.m.

        private final IndiceMonetarioService indiceMonetarioService;

        public CalculadoraReceitaEstadualPRService(IndiceMonetarioService indiceMonetarioService) {
                this.indiceMonetarioService = indiceMonetarioService;
        }

    @Override
    public boolean suporta(ModalidadeParcelamento modalidade) {
        return modalidade == ModalidadeParcelamento.ORDINARIO_SEFA_PR
                || modalidade == ModalidadeParcelamento.ESPECIAL_SEFA_PR;
    }

    @Override
    public SimulacaoResultDTO calcular(Declaracao declaracao, SimulacaoRequestDTO request) {
        // ---- 0. Validações ----
        if (declaracao.getDataVencimento() == null) {
            throw new BusinessRuleException("A declaração precisa ter data de vencimento para calcular o parcelamento.");
        }

        BigDecimal valorOriginal = resolverValorDebito(declaracao);
        LocalDate dataVencimento = declaracao.getDataVencimento();
        LocalDate dataCalculo = request.getDataCalculo() != null ? request.getDataCalculo() : LocalDate.now();

        if (!dataCalculo.isAfter(dataVencimento)) {
            throw new BusinessRuleException("A data de cálculo deve ser posterior à data de vencimento.");
        }

        int numeroParcelas = request.getNumeroParcelas();
        List<EtapaCalculoDTO> memoria = new ArrayList<>();
        int etapa = 1;

        // ---- 1. Valor original ----
        memoria.add(new EtapaCalculoDTO(etapa++, "Valor original do débito", "-", valorOriginal, "R$"));

        // ---- 2. Dias em atraso ----
        long diasEmAtraso = ChronoUnit.DAYS.between(dataVencimento, dataCalculo);
        memoria.add(new EtapaCalculoDTO(etapa++, "Dias em atraso",
                "Data cálculo - Data vencimento", BigDecimal.valueOf(diasEmAtraso), "dias"));

        // ---- 3. Atualização monetária pela UFPR ----
        YearMonth mesVencimento = YearMonth.from(dataVencimento);
        YearMonth mesCalculo = YearMonth.from(dataCalculo);
        BigDecimal fatorUFPR = BigDecimal.ONE;
        BigDecimal percentualAtualizacao = BigDecimal.ZERO;
        String descricaoUFPR;

        try {
            fatorUFPR = indiceMonetarioService.calcularFatorAtualizacaoUFPR(mesVencimento, mesCalculo);
            percentualAtualizacao = fatorUFPR.subtract(BigDecimal.ONE)
                    .multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP);
            descricaoUFPR = "UFPR " + mesCalculo + " / UFPR " + mesVencimento;
        } catch (Exception e) {
            // Fallback: usar IPCA estimado de 0,5% ao mês
            long meses = ChronoUnit.MONTHS.between(mesVencimento, mesCalculo);
            percentualAtualizacao = BigDecimal.valueOf(meses).multiply(new BigDecimal("0.50"))
                    .setScale(4, RoundingMode.HALF_UP);
            fatorUFPR = BigDecimal.ONE.add(percentualAtualizacao.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
            descricaoUFPR = "UFPR (estimado - 0,5% a.m.)";
        }

        BigDecimal valorAtualizado = valorOriginal.multiply(fatorUFPR).setScale(2, RoundingMode.HALF_UP);
        memoria.add(new EtapaCalculoDTO(etapa++, "Fator de atualização monetária (UFPR)",
                descricaoUFPR, fatorUFPR, "-"));
        memoria.add(new EtapaCalculoDTO(etapa++, "Percentual de atualização monetária",
                descricaoUFPR, percentualAtualizacao, "%"));
        memoria.add(new EtapaCalculoDTO(etapa++, "Valor atualizado (antes das penalidades)",
                "Débito × Fator UFPR", valorAtualizado, "R$"));

        // ---- 4. Multa de mora ----
        BigDecimal percentualMulta;
        String formulaMulta;
        if (diasEmAtraso <= 30) {
            percentualMulta = MULTA_MORA_DIARIA.multiply(BigDecimal.valueOf(diasEmAtraso))
                    .setScale(4, RoundingMode.HALF_UP);
            formulaMulta = "0,33% × " + diasEmAtraso + " dias";
        } else {
            percentualMulta = MULTA_MORA_MAX;
            formulaMulta = "Fixo 20% (mais de 30 dias)";
        }

        // Redução de multa para modalidade especial
        BigDecimal reducaoMulta = BigDecimal.ZERO;
        if (request.getPercentualReducaoMulta() != null && request.getPercentualReducaoMulta() > 0) {
            BigDecimal fatorReducao = BigDecimal.valueOf(request.getPercentualReducaoMulta())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            reducaoMulta = percentualMulta.multiply(fatorReducao).setScale(4, RoundingMode.HALF_UP);
            percentualMulta = percentualMulta.subtract(reducaoMulta).max(BigDecimal.ZERO);
            memoria.add(new EtapaCalculoDTO(etapa++,
                    "Redução de multa (" + request.getPercentualReducaoMulta() + "% - Programa Especial)",
                    formulaMulta + " × redução", reducaoMulta.negate(), "%"));
        }

        BigDecimal valorMulta = valorAtualizado.multiply(percentualMulta)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        memoria.add(new EtapaCalculoDTO(etapa++, "Percentual de multa de mora", formulaMulta, percentualMulta, "%"));
        memoria.add(new EtapaCalculoDTO(etapa++, "Valor da multa de mora",
                "Valor atualizado × " + percentualMulta + "%", valorMulta, "R$"));

        // ---- 5. Juros de mora (1% a.m. simplex sobre o valor atualizado) ----
        long mesesAtraso = ChronoUnit.MONTHS.between(mesVencimento, mesCalculo);
        BigDecimal percentualJuros = JUROS_MENSAIS.multiply(BigDecimal.valueOf(mesesAtraso))
                .setScale(4, RoundingMode.HALF_UP);

        // Redução de juros para modalidade especial
        BigDecimal reducaoJuros = BigDecimal.ZERO;
        if (request.getPercentualReducaoJuros() != null && request.getPercentualReducaoJuros() > 0) {
            BigDecimal fatorReducaoJ = BigDecimal.valueOf(request.getPercentualReducaoJuros())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            reducaoJuros = percentualJuros.multiply(fatorReducaoJ).setScale(4, RoundingMode.HALF_UP);
            percentualJuros = percentualJuros.subtract(reducaoJuros).max(BigDecimal.ZERO);
            memoria.add(new EtapaCalculoDTO(etapa++,
                    "Redução de juros (" + request.getPercentualReducaoJuros() + "% - Programa Especial)",
                    "1% × meses × redução", reducaoJuros.negate(), "%"));
        }

        BigDecimal valorJuros = valorAtualizado.multiply(percentualJuros)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        memoria.add(new EtapaCalculoDTO(etapa++,
                "Juros de mora (" + mesesAtraso + " meses × 1% a.m.)",
                "1% × " + mesesAtraso + " meses (simplex)", percentualJuros, "%"));
        memoria.add(new EtapaCalculoDTO(etapa++, "Valor dos juros de mora",
                "Valor atualizado × " + percentualJuros + "%", valorJuros, "R$"));

        // ---- 6. Total a parcelar ----
        BigDecimal totalComPenalidades = valorAtualizado.add(valorMulta).add(valorJuros);
        memoria.add(new EtapaCalculoDTO(etapa++, "Total consolidado (com penalidades)",
                "Valor atualizado + Multa + Juros", totalComPenalidades, "R$"));

        // ---- 7. Entrada (opcional) ----
        BigDecimal valorEntrada = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(request.getIncluirEntrada())) {
            valorEntrada = totalComPenalidades.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
            memoria.add(new EtapaCalculoDTO(etapa++, "Entrada (10% do total consolidado)",
                    "Total × 10%", valorEntrada, "R$"));
        }

        BigDecimal saldoParcelar = totalComPenalidades.subtract(valorEntrada);

        // ---- 8. Validação da parcela mínima ----
        String avisoParcelaMinima = null;
        int parcelasAjustadas = Math.min(numeroParcelas, MAX_PARCELAS);
        BigDecimal valorParcelaCalculada = saldoParcelar
                .divide(BigDecimal.valueOf(parcelasAjustadas), 2, RoundingMode.HALF_UP);

        if (valorParcelaCalculada.compareTo(PARCELA_MINIMA) < 0) {
            parcelasAjustadas = saldoParcelar.divide(PARCELA_MINIMA, 0, RoundingMode.FLOOR).intValue();
            if (parcelasAjustadas < 1) parcelasAjustadas = 1;
            valorParcelaCalculada = saldoParcelar.divide(BigDecimal.valueOf(parcelasAjustadas), 2, RoundingMode.CEILING);
            avisoParcelaMinima = "Número de parcelas reduzido para " + parcelasAjustadas
                    + " para respeitar o valor mínimo de R$ " + PARCELA_MINIMA + " por parcela (SEFA-PR).";
        }
        memoria.add(new EtapaCalculoDTO(etapa++, "Valor de cada parcela",
                "Saldo a parcelar ÷ " + parcelasAjustadas, valorParcelaCalculada, "R$"));

        // ---- 9. Custo total ----
        BigDecimal custoTotal = valorEntrada.add(valorParcelaCalculada.multiply(BigDecimal.valueOf(parcelasAjustadas)));
        memoria.add(new EtapaCalculoDTO(etapa++, "Custo total do parcelamento",
                "Entrada + (" + parcelasAjustadas + " × " + valorParcelaCalculada + ")", custoTotal, "R$"));

        // ---- 10. Economia ----
        BigDecimal econMulta = valorAtualizado.multiply(reducaoMulta)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal econJuros = valorAtualizado.multiply(reducaoJuros)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal economiaObtida = econMulta.add(econJuros);
        if (economiaObtida.compareTo(BigDecimal.ZERO) > 0) {
            memoria.add(new EtapaCalculoDTO(etapa++, "Economia com reduções (Programa Especial)",
                    "Redução multa + Redução juros aplicados", economiaObtida, "R$"));
        }

        // ---- 11. Tabela de parcelas ----
        List<ParcelaDTO> tabelaParcelas = gerarTabelaParcelas(
                dataCalculo, parcelasAjustadas, valorParcelaCalculada, valorEntrada, saldoParcelar);

        // ---- Montar resultado ----
        SimulacaoResultDTO result = new SimulacaoResultDTO();
        result.setDeclaracaoId(declaracao.getId());
        result.setOrgao(OrgaoReceita.RECEITA_ESTADUAL_PR);
        result.setModalidade(request.getModalidade());
        result.setTributo(declaracao.getTributo());
        result.setEmpresaRazaoSocial(declaracao.getEmpresa().getRazaoSocial());
        result.setEmpresaCnpj(declaracao.getEmpresa().getCnpj());
        result.setValorOriginal(valorOriginal);
        result.setDataVencimentoOriginal(dataVencimento);
        result.setDataCalculo(dataCalculo);
        result.setDiasEmAtraso((int) diasEmAtraso);
        result.setPercentualMultaMora(percentualMulta);
        result.setValorMultaMora(valorMulta);
        result.setPercentualMultaOficio(BigDecimal.ZERO);
        result.setValorMultaOficio(BigDecimal.ZERO);
        result.setPercentualJurosMora(percentualJuros);
        result.setValorJurosMora(valorJuros);
        result.setIndiceUtilizado("UFPR");
        result.setValorReducaoMulta(econMulta);
        result.setValorReducaoJuros(econJuros);
        result.setValorAtualizado(totalComPenalidades);
        result.setValorEntrada(valorEntrada);
        result.setNumeroParcelas(parcelasAjustadas);
        result.setValorParcela(valorParcelaCalculada);
        result.setCustoTotal(custoTotal);
        result.setEconomiaObtida(economiaObtida);
        result.setAvisoParcelaMinima(avisoParcelaMinima);
        result.setMemoriaCalculo(memoria);
        result.setTabelaParcelas(tabelaParcelas);

        return result;
    }

    private BigDecimal resolverValorDebito(Declaracao declaracao) {
        if (declaracao.getValorImposto() != null
                && declaracao.getValorImposto().compareTo(BigDecimal.ZERO) > 0) {
            return declaracao.getValorImposto();
        }
        return declaracao.getValorTotal();
    }

    private List<ParcelaDTO> gerarTabelaParcelas(LocalDate dataCalculo, int numeroParcelas,
                                                   BigDecimal valorParcela, BigDecimal valorEntrada,
                                                   BigDecimal saldoParcelar) {
        List<ParcelaDTO> tabela = new ArrayList<>();
        BigDecimal saldoRestante = saldoParcelar;

        if (valorEntrada.compareTo(BigDecimal.ZERO) > 0) {
            tabela.add(new ParcelaDTO(0, dataCalculo, valorEntrada, saldoRestante));
        }

        // SEFA-PR: vencimento no último dia útil do mês (usamos dia 28 como aproximação)
        LocalDate vencimentoParcela = dataCalculo.withDayOfMonth(Math.min(28, dataCalculo.lengthOfMonth()))
                .plusMonths(1);

        for (int i = 1; i <= numeroParcelas; i++) {
            saldoRestante = saldoRestante.subtract(valorParcela).max(BigDecimal.ZERO);
            tabela.add(new ParcelaDTO(i, vencimentoParcela, valorParcela, saldoRestante));
            vencimentoParcela = vencimentoParcela.plusMonths(1);
        }

        return tabela;
    }
}
