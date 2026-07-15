package com.fiscalhub.backend.service.calculo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.dto.EtapaCalculoDTO;
import com.fiscalhub.backend.dto.ParcelaDTO;
import com.fiscalhub.backend.dto.SimulacaoRequestDTO;
import com.fiscalhub.backend.dto.SimulacaoResultDTO;
import com.fiscalhub.backend.entity.Declaracao;
import com.fiscalhub.backend.entity.enums.ModalidadeParcelamento;
import com.fiscalhub.backend.entity.enums.OrgaoReceita;
import com.fiscalhub.backend.entity.enums.TipoIndice;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.service.IndiceMonetarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Engine de cálculo para parcelamentos da Receita Federal do Brasil.
 *
 * Referências legais:
 * - Art. 61, §1° da Lei 9.430/96 (multa de mora)
 * - Art. 13 da Lei 9.065/95 (juros SELIC)
 * - Lei 10.522/02 (parcelamento ordinário, até 60x)
 * - Portaria PGFN/RFB n° 15/2009 (parcela mínima)
 */
@Service
public class CalculadoraReceitaFederalService implements CalculoParcelamentoStrategy {

    private static final BigDecimal PARCELA_MINIMA_PJ = new BigDecimal("500.00");
    private static final int MAX_PARCELAS = 60;
    private static final BigDecimal MULTA_MORA_MAX = new BigDecimal("20.00");
    private static final BigDecimal MULTA_MORA_DIARIA = new BigDecimal("0.33");

    @Autowired
    private IndiceMonetarioService indiceMonetarioService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean suporta(ModalidadeParcelamento modalidade) {
        return modalidade == ModalidadeParcelamento.ORDINARIO_RFB
                || modalidade == ModalidadeParcelamento.PERT_RFB
                || modalidade == ModalidadeParcelamento.REFIS;
    }

    @Override
    public SimulacaoResultDTO calcular(Declaracao declaracao, SimulacaoRequestDTO request) {
        // ---- 0. Validações iniciais ----
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

        // ---- 3. Multa de mora (Art. 61, §1° Lei 9.430/96) ----
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

        // Redução de multa para modalidades especiais
        BigDecimal reducaoMulta = BigDecimal.ZERO;
        if (request.getPercentualReducaoMulta() != null && request.getPercentualReducaoMulta() > 0) {
            BigDecimal fatorReducao = BigDecimal.valueOf(request.getPercentualReducaoMulta())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            reducaoMulta = percentualMulta.multiply(fatorReducao).setScale(4, RoundingMode.HALF_UP);
            percentualMulta = percentualMulta.subtract(reducaoMulta).max(BigDecimal.ZERO);
            memoria.add(new EtapaCalculoDTO(etapa++,
                    "Redução de multa (" + request.getPercentualReducaoMulta() + "%)",
                    formulaMulta + " × redução", reducaoMulta.negate(), "%"));
        }

        BigDecimal valorMulta = valorOriginal.multiply(percentualMulta)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        memoria.add(new EtapaCalculoDTO(etapa++, "Percentual de multa de mora", formulaMulta, percentualMulta, "%"));
        memoria.add(new EtapaCalculoDTO(etapa++, "Valor da multa de mora",
                "Débito × " + percentualMulta + "%", valorMulta, "R$"));

        // ---- 4. Juros SELIC acumulados (Art. 13 Lei 9.065/95) ----
        // Incidem a partir do 1° dia do mês seguinte ao vencimento até o mês do cálculo
        YearMonth mesInicioJuros = YearMonth.from(dataVencimento).plusMonths(1);
        YearMonth mesFimJuros = YearMonth.from(dataCalculo);

        BigDecimal percentualJuros = BigDecimal.ZERO;
        String indiceUtilizado = "SELIC";
        try {
            percentualJuros = indiceMonetarioService.calcularJurosAcumulados(
                    TipoIndice.SELIC, mesInicioJuros, mesFimJuros);
        } catch (Exception e) {
            // Se não houver dados SELIC no período, usa estimativa conservadora de 1% a.m.
            long mesesAtraso = ChronoUnit.MONTHS.between(mesInicioJuros, mesFimJuros) + 1;
            percentualJuros = BigDecimal.valueOf(mesesAtraso).multiply(new BigDecimal("1.00"))
                    .setScale(4, RoundingMode.HALF_UP);
            indiceUtilizado = "SELIC (estimado)";
        }

        // Redução de juros para modalidades especiais
        BigDecimal reducaoJuros = BigDecimal.ZERO;
        if (request.getPercentualReducaoJuros() != null && request.getPercentualReducaoJuros() > 0) {
            BigDecimal fatorReducaoJ = BigDecimal.valueOf(request.getPercentualReducaoJuros())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            reducaoJuros = percentualJuros.multiply(fatorReducaoJ).setScale(4, RoundingMode.HALF_UP);
            percentualJuros = percentualJuros.subtract(reducaoJuros).max(BigDecimal.ZERO);
            memoria.add(new EtapaCalculoDTO(etapa++,
                    "Redução de juros (" + request.getPercentualReducaoJuros() + "%)",
                    "SELIC acumulado × redução", reducaoJuros.negate(), "%"));
        }

        BigDecimal valorJuros = valorOriginal.multiply(percentualJuros)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        memoria.add(new EtapaCalculoDTO(etapa++,
                "Juros SELIC acumulados (" + mesInicioJuros + " a " + mesFimJuros + ")",
                "Produto das taxas mensais SELIC", percentualJuros, "%"));
        memoria.add(new EtapaCalculoDTO(etapa++, "Valor dos juros de mora",
                "Débito × " + percentualJuros + "%", valorJuros, "R$"));

        // ---- 5. Valor atualizado ----
        BigDecimal valorAtualizado = valorOriginal.add(valorMulta).add(valorJuros);
        memoria.add(new EtapaCalculoDTO(etapa++, "Valor total atualizado",
                "Débito + Multa + Juros", valorAtualizado, "R$"));

        // ---- 6. Entrada (apenas para modalidades que exigem) ----
        BigDecimal valorEntrada = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(request.getIncluirEntrada())
                && request.getModalidade() == ModalidadeParcelamento.PERT_RFB) {
            // PERT exige entrada de 5% do total consolidado
            valorEntrada = valorAtualizado.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
            memoria.add(new EtapaCalculoDTO(etapa++, "Entrada obrigatória (5% - PERT)",
                    "Valor atualizado × 5%", valorEntrada, "R$"));
        }

        // ---- 7. Saldo a parcelar ----
        BigDecimal saldoParcelar = valorAtualizado.subtract(valorEntrada);

        // ---- 8. Validação da parcela mínima ----
        String avisoParcelaMinima = null;
        int parcelasAjustadas = Math.min(numeroParcelas, MAX_PARCELAS);
        BigDecimal valorParcelaCalculada = saldoParcelar
                .divide(BigDecimal.valueOf(parcelasAjustadas), 2, RoundingMode.HALF_UP);

        if (valorParcelaCalculada.compareTo(PARCELA_MINIMA_PJ) < 0) {
            parcelasAjustadas = saldoParcelar.divide(PARCELA_MINIMA_PJ, 0, RoundingMode.FLOOR).intValue();
            if (parcelasAjustadas < 1) parcelasAjustadas = 1;
            valorParcelaCalculada = saldoParcelar.divide(BigDecimal.valueOf(parcelasAjustadas), 2, RoundingMode.CEILING);
            avisoParcelaMinima = "Número de parcelas reduzido para " + parcelasAjustadas
                    + " para respeitar o valor mínimo de R$ " + PARCELA_MINIMA_PJ + " por parcela (RFB).";
        }
        memoria.add(new EtapaCalculoDTO(etapa++, "Valor de cada parcela",
                "Saldo a parcelar ÷ " + parcelasAjustadas, valorParcelaCalculada, "R$"));

        // ---- 9. Custo total ----
        BigDecimal custoTotal = valorEntrada.add(valorParcelaCalculada.multiply(BigDecimal.valueOf(parcelasAjustadas)));
        memoria.add(new EtapaCalculoDTO(etapa++, "Custo total do parcelamento",
                "Entrada + (" + parcelasAjustadas + " × " + valorParcelaCalculada + ")", custoTotal, "R$"));

        // ---- 10. Economia obtida ----
        BigDecimal economiaObtida = calcularEconomia(valorOriginal, reducaoMulta, reducaoJuros);
        if (economiaObtida.compareTo(BigDecimal.ZERO) > 0) {
            memoria.add(new EtapaCalculoDTO(etapa++, "Economia com reduções",
                    "Redução multa + Redução juros aplicados", economiaObtida, "R$"));
        }

        // ---- 11. Tabela de parcelas ----
        List<ParcelaDTO> tabelaParcelas = gerarTabelaParcelas(dataCalculo, parcelasAjustadas,
                valorParcelaCalculada, valorEntrada, saldoParcelar);

        // ---- Montar resultado ----
        SimulacaoResultDTO result = new SimulacaoResultDTO();
        result.setDeclaracaoId(declaracao.getId());
        result.setOrgao(OrgaoReceita.RECEITA_FEDERAL);
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
        result.setIndiceUtilizado(indiceUtilizado);
        result.setValorReducaoMulta(valorOriginal.multiply(reducaoMulta).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        result.setValorReducaoJuros(valorOriginal.multiply(reducaoJuros).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        result.setValorAtualizado(valorAtualizado);
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
        // Usa valorImposto como débito se disponível, senão valorTotal
        if (declaracao.getValorImposto() != null
                && declaracao.getValorImposto().compareTo(BigDecimal.ZERO) > 0) {
            return declaracao.getValorImposto();
        }
        return declaracao.getValorTotal();
    }

    private BigDecimal calcularEconomia(BigDecimal valorOriginal, BigDecimal reducaoMultaPct, BigDecimal reducaoJurosPct) {
        BigDecimal econMulta = valorOriginal.multiply(reducaoMultaPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal econJuros = valorOriginal.multiply(reducaoJurosPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return econMulta.add(econJuros);
    }

    private List<ParcelaDTO> gerarTabelaParcelas(LocalDate dataCalculo, int numeroParcelas,
                                                   BigDecimal valorParcela, BigDecimal valorEntrada,
                                                   BigDecimal saldoParcelar) {
        List<ParcelaDTO> tabela = new ArrayList<>();
        BigDecimal saldoRestante = saldoParcelar;

        // Entrada (parcela 0) se houver
        if (valorEntrada.compareTo(BigDecimal.ZERO) > 0) {
            tabela.add(new ParcelaDTO(0, dataCalculo, valorEntrada, saldoRestante));
        }

        // Parcelas mensais — vencimento no dia 28 de cada mês
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
