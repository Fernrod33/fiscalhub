package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.entity.enums.SituacaoDebito;
import com.fiscalhub.backend.entity.enums.TipoTributo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Débito lido de um arquivo pelos parsers (Fase 2). Não é exposto na API. */
public record DebitoImportado(String referenciaOrigem, OrgaoCredor orgaoCredor, TipoTributo tributo,
                              LocalDate competencia, LocalDate dataVencimento, BigDecimal valorPrincipal,
                              BigDecimal valorMulta, BigDecimal valorJuros, SituacaoDebito situacao,
                              String numeroInscricao) {
}
