package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.Debito;
import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.entity.enums.SituacaoDebito;
import com.fiscalhub.backend.entity.enums.TipoTributo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DebitoResponseDTO(Long id, Long clienteId, Long documentoOrigemId, String referenciaOrigem,
                                OrgaoCredor orgaoCredor, TipoTributo tributo, LocalDate competencia,
                                LocalDate dataVencimento, BigDecimal valorPrincipal, BigDecimal valorMulta,
                                BigDecimal valorJuros, SituacaoDebito situacao, String numeroInscricao) {

    public static DebitoResponseDTO fromEntity(Debito d) {
        return new DebitoResponseDTO(d.getId(), d.getCliente().getId(), d.getDocumentoOrigem().getId(),
                d.getReferenciaOrigem(), d.getOrgaoCredor(), d.getTributo(), d.getCompetencia(), d.getDataVencimento(),
                d.getValorPrincipal(), d.getValorMulta(), d.getValorJuros(), d.getSituacao(), d.getNumeroInscricao());
    }
}
