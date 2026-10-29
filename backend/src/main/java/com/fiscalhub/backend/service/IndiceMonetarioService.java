package com.fiscalhub.backend.service;

import com.fiscalhub.backend.entity.IndiceMonetario;
import com.fiscalhub.backend.entity.enums.TipoIndice;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.repository.IndiceMonetarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Serviço responsável por consultar e calcular índices monetários
 * (SELIC) utilizados nos acréscimos e na correção das parcelas.
 */
@Service
// noRollbackFor: "falta SELIC" é resposta, não falha; quem chama (conciliação, capacidade) captura e segue na mesma transação
@Transactional(readOnly = true, noRollbackFor = com.fiscalhub.backend.exception.BusinessRuleException.class)
public class IndiceMonetarioService {

    private final IndiceMonetarioRepository indiceMonetarioRepository;

    public IndiceMonetarioService(IndiceMonetarioRepository indiceMonetarioRepository) {
        this.indiceMonetarioRepository = indiceMonetarioRepository;
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

    /** Taxas carregadas no intervalo (inclusive), por mês, sem exigir todos: base da projeção e do "real × projetado". */
    public java.util.Map<YearMonth, BigDecimal> taxasCarregadas(TipoIndice tipo, YearMonth mesInicio, YearMonth mesFim) {
        java.util.Map<YearMonth, BigDecimal> taxas = new java.util.TreeMap<>();
        if (!mesInicio.isAfter(mesFim)) {
            indiceMonetarioRepository.findByTipoAndPeriodo(tipo, mesInicio.getYear(), mesInicio.getMonthValue(),
                    mesFim.getYear(), mesFim.getMonthValue()).forEach(i -> taxas.put(YearMonth.of(i.getAno(), i.getMes()), i.getValor()));
        }
        return taxas;
    }

    public static BusinessRuleException indiceAusente(TipoIndice tipo, YearMonth mes) {
        return new BusinessRuleException("Índice " + tipo + " ausente para " + mes + ".");
    }
}
