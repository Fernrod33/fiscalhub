package com.fiscalhub.backend.service;

import com.fiscalhub.backend.entity.IndiceMonetario;
import com.fiscalhub.backend.entity.enums.TipoIndice;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.repository.IndiceMonetarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

/**
 * Serviço responsável por consultar e calcular índices monetários
 * (SELIC, UFPR, IPCA) utilizados nos cálculos de parcelamento.
 */
@Service
@Transactional(readOnly = true)
public class IndiceMonetarioService {

    private final IndiceMonetarioRepository indiceMonetarioRepository;

    public IndiceMonetarioService(IndiceMonetarioRepository indiceMonetarioRepository) {
        this.indiceMonetarioRepository = indiceMonetarioRepository;
    }

    /**
     * Calcula o fator acumulado de um índice de taxa mensal (ex: SELIC)
     * entre dois meses (inclusive), usando composição composta.
     *
     * Regra Receita Federal: juros SELIC incidem a partir do mês seguinte ao
     * vencimento até o mês do pagamento/cálculo.
     *
     * @param tipo       tipo do índice
     * @param mesInicio  primeiro mês a ser acumulado (inclusive)
     * @param mesFim     último mês a ser acumulado (inclusive)
     * @return percentual acumulado (ex: 15.34 para 15,34%)
     */
    public BigDecimal calcularJurosAcumulados(TipoIndice tipo, YearMonth mesInicio, YearMonth mesFim) {
        if (mesInicio.isAfter(mesFim)) {
            return BigDecimal.ZERO;
        }

        List<IndiceMonetario> indices = indicesCompletos(tipo, mesInicio, mesFim);

        // Produto de (1 + taxa_i/100) para cada mês
        BigDecimal fatorAcumulado = BigDecimal.ONE;
        for (IndiceMonetario indice : indices) {
            BigDecimal taxaMensal = indice.getValor().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
            fatorAcumulado = fatorAcumulado.multiply(BigDecimal.ONE.add(taxaMensal));
        }

        // Percentual = (fatorAcumulado - 1) * 100
        return fatorAcumulado.subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Soma simples das taxas mensais (ex.: SELIC) entre dois meses, inclusive, em percentual: a regra dos juros de mora
     * federais (Lei 9.430/96, art. 61, §3º). mesInicio depois de mesFim: zero. Mês sem índice: erro (sem estimativa).
     */
    public BigDecimal somarTaxas(TipoIndice tipo, YearMonth mesInicio, YearMonth mesFim) {
        if (mesInicio.isAfter(mesFim)) {
            return BigDecimal.ZERO;
        }
        return indicesCompletos(tipo, mesInicio, mesFim).stream().map(IndiceMonetario::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Os índices de todos os meses do intervalo; sem fallback: mês ausente gera erro de negócio, nunca juros zerados. */
    private List<IndiceMonetario> indicesCompletos(TipoIndice tipo, YearMonth mesInicio, YearMonth mesFim) {
        List<IndiceMonetario> indices = indiceMonetarioRepository.findByTipoAndPeriodo(
                tipo,
                mesInicio.getYear(), mesInicio.getMonthValue(),
                mesFim.getYear(), mesFim.getMonthValue()
        );
        YearMonth esperado = mesInicio;
        for (IndiceMonetario indice : indices) {
            if (!YearMonth.of(indice.getAno(), indice.getMes()).equals(esperado)) {
                break;
            }
            esperado = esperado.plusMonths(1);
        }
        if (!esperado.isAfter(mesFim)) {
            throw indiceAusente(tipo, esperado);
        }
        return indices;
    }

    /**
     * Retorna o valor da UFPR para um mês específico.
     * Usado na atualização monetária do parcelamento estadual PR.
     */
    public BigDecimal getValorUFPR(YearMonth mesAno) {
        return indiceMonetarioRepository
                .findByTipoAndAnoAndMes(TipoIndice.UFPR, mesAno.getYear(), mesAno.getMonthValue())
                .map(IndiceMonetario::getValor)
                .orElseThrow(() -> indiceAusente(TipoIndice.UFPR, mesAno));
    }

    /**
     * Calcula o fator de atualização monetária pela UFPR entre dois meses.
     *
     * @return fator (ex: 1.0523 representa atualização de 5,23%)
     */
    public BigDecimal calcularFatorAtualizacaoUFPR(YearMonth mesVencimento, YearMonth mesCalculo) {
        if (!mesCalculo.isAfter(mesVencimento)) {
            return BigDecimal.ONE;
        }
        BigDecimal ufprVencimento = getValorUFPR(mesVencimento);
        BigDecimal ufprCalculo = getValorUFPR(mesCalculo);
        return ufprCalculo.divide(ufprVencimento, 8, RoundingMode.HALF_UP);
    }

    private static BusinessRuleException indiceAusente(TipoIndice tipo, YearMonth mes) {
        return new BusinessRuleException("Índice " + tipo + " ausente para " + mes + ".");
    }
}
