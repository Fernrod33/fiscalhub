package com.fiscalhub.backend.situacao;

import com.fiscalhub.backend.entity.enums.TipoIndice;
import com.fiscalhub.backend.service.IndiceMonetarioService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Acréscimos moratórios de tributo federal pago depois do vencimento (RFB e PGFN), componente único da regra:
 * <ul>
 *   <li>multa de mora: 0,33% por dia de atraso, contado do dia seguinte ao vencimento até o dia do pagamento, limitada
 *   a 20% (Lei 9.430/96, art. 61, caput e §§1º e 2º);</li>
 *   <li>juros: soma das taxas SELIC mensais do mês seguinte ao do vencimento até o mês anterior ao do pagamento, mais 1%
 *   no mês do pagamento (art. 61, §3º, e art. 5º, §3º). Pago no próprio mês do vencimento: sem juros.</li>
 * </ul>
 * Sem estimativa: mês sem SELIC carregada é erro (BusinessRuleException do IndiceMonetarioService).
 * A conciliação usa para achar pagamento em atraso sem acréscimos; as calculadoras de parcelamento da Fase 5 devem usar
 * este mesmo componente.
 */
@Component
public class AcrescimosMoratorios {

    static final BigDecimal MULTA_DIARIA = new BigDecimal("0.33");
    static final BigDecimal MULTA_MAXIMA = new BigDecimal("20.00");
    static final BigDecimal JUROS_MES_PAGAMENTO = BigDecimal.ONE;

    /** Percentuais em %, valores em R$. selicDe/selicAte: meses somados (null quando não há mês a somar). */
    public record Acrescimos(BigDecimal principal, LocalDate vencimento, LocalDate pagamento, long diasAtraso,
                             BigDecimal percentualMulta, BigDecimal multa, YearMonth selicDe, YearMonth selicAte,
                             BigDecimal percentualJuros, BigDecimal juros) {
        public BigDecimal total() {
            return multa.add(juros);
        }
    }

    private final IndiceMonetarioService indices;

    public AcrescimosMoratorios(IndiceMonetarioService indices) {
        this.indices = indices;
    }

    public Acrescimos calcular(BigDecimal principal, LocalDate vencimento, LocalDate pagamento) {
        long dias = Math.max(0, ChronoUnit.DAYS.between(vencimento, pagamento));
        BigDecimal percentualMulta = MULTA_DIARIA.multiply(BigDecimal.valueOf(dias)).min(MULTA_MAXIMA);
        YearMonth mesVencimento = YearMonth.from(vencimento);
        YearMonth mesPagamento = YearMonth.from(pagamento);
        BigDecimal percentualJuros = BigDecimal.ZERO;
        YearMonth de = null;
        YearMonth ate = null;
        if (mesPagamento.isAfter(mesVencimento)) {
            de = mesVencimento.plusMonths(1);
            ate = mesPagamento.minusMonths(1);
            percentualJuros = indices.somarTaxas(TipoIndice.SELIC, de, ate).add(JUROS_MES_PAGAMENTO);
            if (de.isAfter(ate)) {
                de = ate = null; // pago no mês seguinte ao vencimento: só o 1%
            }
        }
        return new Acrescimos(principal, vencimento, pagamento, dias, percentualMulta, percentual(principal, percentualMulta),
                de, ate, percentualJuros, percentual(principal, percentualJuros));
    }

    private static BigDecimal percentual(BigDecimal valor, BigDecimal percentual) {
        return valor.multiply(percentual).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
