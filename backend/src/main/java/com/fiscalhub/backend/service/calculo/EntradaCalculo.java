package com.fiscalhub.backend.service.calculo;

import com.fiscalhub.backend.dto.SimulacaoRequestDTO;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.Debito;
import com.fiscalhub.backend.entity.enums.TipoTributo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Entrada consolidada do motor: débitos do mesmo cliente + parâmetros da simulação.
 * ponytail: consolida somando o principal e usando o vencimento mais antigo (equivale ao cálculo antigo
 * para um débito); cálculo por débito com seu próprio vencimento fica para a Fase 5.
 */
public record EntradaCalculo(Cliente cliente, List<Debito> debitos, SimulacaoRequestDTO parametros) {

    public BigDecimal valorPrincipal() {
        return debitos.stream().map(Debito::getValorPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public LocalDate vencimentoMaisAntigo() {
        return debitos.stream().map(Debito::getDataVencimento).min(Comparator.naturalOrder()).orElseThrow();
    }

    /** O tributo, se todos os débitos forem do mesmo; senão null. */
    public TipoTributo tributo() {
        List<TipoTributo> tributos = debitos.stream().map(Debito::getTributo).distinct().toList();
        return tributos.size() == 1 ? tributos.get(0) : null;
    }

    public List<Long> debitoIds() {
        return debitos.stream().map(Debito::getId).toList();
    }
}
