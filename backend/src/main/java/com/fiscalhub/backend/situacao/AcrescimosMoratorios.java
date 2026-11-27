package com.fiscalhub.backend.situacao;

import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.entity.enums.TipoIndice;
import com.fiscalhub.backend.parcelamento.RegrasParcelamento;
import com.fiscalhub.backend.parcelamento.RegrasParcelamento.RegraAcrescimo;
import com.fiscalhub.backend.service.IndiceMonetarioService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Acréscimos moratórios de tributo pago depois do vencimento, componente único (nenhum outro cálculo de multa ou juros
 * existe no sistema). Os parâmetros vêm da {@link RegraAcrescimo} vigente do órgão ({@code parcelamento/regras.json}):
 * <ul>
 *   <li>multa: multaDiaria por dia de atraso, do dia seguinte ao vencimento ao do pagamento, limitada a multaMaxima
 *   (federal: Lei 9.430/96, art. 61). PR: até o dia multaAposDias, por dia; depois, multaAposPrazo até a inscrição;
 *   inscrito, multaInscrito (Lei 11.580/96, art. 55, §1º, I, com a redução do RICMS/PR, art. 80);</li>
 *   <li>juros: soma das taxas SELIC mensais do mês seguinte ao do vencimento até o mês anterior ao do pagamento, mais
 *   jurosMesPagamento no mês do pagamento (Lei 9.430, art. 61, §3º; Lei 11.580, art. 38). Pago no próprio mês do
 *   vencimento: sem juros;</li>
 *   <li>encargo legal da dívida ativa da União sobre principal, multa e juros (DL 1.025/69, art. 1º).</li>
 * </ul>
 * Sem estimativa: mês sem SELIC carregada é erro (BusinessRuleException do IndiceMonetarioService).
 */
@Component
public class AcrescimosMoratorios {

    /** Percentuais em %, valores em R$. selicDe/selicAte: meses somados (null quando não há mês a somar). */
    public record Acrescimos(BigDecimal principal, LocalDate vencimento, LocalDate pagamento, long diasAtraso,
                             BigDecimal percentualMulta, BigDecimal multa, YearMonth selicDe, YearMonth selicAte,
                             BigDecimal percentualJuros, BigDecimal juros, String fundamento) {
        public BigDecimal total() {
            return multa.add(juros);
        }
    }

    private final IndiceMonetarioService indices;
    private final RegrasParcelamento regras;

    public AcrescimosMoratorios(IndiceMonetarioService indices, RegrasParcelamento regras) {
        this.indices = indices;
        this.regras = regras;
    }

    /** Tributo federal (RFB). */
    public Acrescimos calcular(BigDecimal principal, LocalDate vencimento, LocalDate pagamento) {
        return calcular(OrgaoCredor.RFB, principal, vencimento, pagamento, false, false);
    }

    /**
     * inscrito: já em dívida ativa (PR: multa cheia). multaAposPrazo: PR, parcelamento do imposto declarado ainda no
     * prazo da multa diária, que só é admitido com a multa do 31º dia (RICMS/PR, art. 81, §7º).
     */
    public Acrescimos calcular(OrgaoCredor orgao, BigDecimal principal, LocalDate vencimento, LocalDate pagamento,
                               boolean inscrito, boolean multaAposPrazo) {
        RegraAcrescimo r = regras.acrescimo(orgao, pagamento);
        long dias = Math.max(0, ChronoUnit.DAYS.between(vencimento, pagamento));
        BigDecimal percentualMulta;
        if (inscrito && r.multaInscrito() != null) {
            percentualMulta = r.multaInscrito();
        } else if (r.multaAposDias() != null && (dias > r.multaAposDias() || multaAposPrazo)) {
            percentualMulta = r.multaAposPrazo();
        } else {
            percentualMulta = r.multaDiaria().multiply(BigDecimal.valueOf(dias)).min(r.multaMaxima());
        }
        YearMonth mesVencimento = YearMonth.from(vencimento);
        YearMonth mesPagamento = YearMonth.from(pagamento);
        BigDecimal percentualJuros = BigDecimal.ZERO;
        YearMonth de = null;
        YearMonth ate = null;
        if (mesPagamento.isAfter(mesVencimento)) {
            de = mesVencimento.plusMonths(1);
            ate = mesPagamento.minusMonths(1);
            percentualJuros = indices.somarTaxas(TipoIndice.SELIC, de, ate).add(r.jurosMesPagamento());
            if (de.isAfter(ate)) {
                de = ate = null; // pago no mês seguinte ao vencimento: só o 1%
            }
        }
        return new Acrescimos(principal, vencimento, pagamento, dias, percentualMulta, percentual(principal, percentualMulta),
                de, ate, percentualJuros, percentual(principal, percentualJuros), r.fundamento());
    }

    /** Encargo legal do órgão sobre a base (principal + multa + juros); zero se o órgão não tem. */
    public BigDecimal encargo(OrgaoCredor orgao, BigDecimal base, LocalDate data) {
        return percentual(base, regras.acrescimo(orgao, data).encargoLegal());
    }

    private static BigDecimal percentual(BigDecimal valor, BigDecimal percentual) {
        return valor.multiply(percentual).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
