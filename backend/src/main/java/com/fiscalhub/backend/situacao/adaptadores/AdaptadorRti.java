package com.fiscalhub.backend.situacao.adaptadores;

import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.fiscal.RepositorioApuracao.SaldoFiscal;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import com.fiscalhub.backend.pdf.DocumentoPdf;
import com.fiscalhub.backend.pdf.DocumentoPdf.Registro;
import com.fiscalhub.backend.situacao.Extracao;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * RTI (Receita Estadual do PR), versão ficticio-2026-10. ATENÇÃO: o conteúdo deste layout é PRESUMIDO pela massa
 * fictícia (resumo mensal de débitos, créditos, saldo credor e ICMS-ST retido × recolhido); o RTI real não foi visto.
 * Daqui sai a evolução do saldo credor de ICMS (saldo_fiscal, um por mês). Se o documento trouxer classificação e
 * capacidade de pagamento, elas vão para capacidade_pagamento_oficial (fonte SEFA_PR); este layout não traz.
 */
@Component
class AdaptadorRti extends AdaptadorFicticio {

    @Override
    public DocumentoTipo tipo() {
        return DocumentoTipo.RTI;
    }

    @Override
    String titulo() {
        return "Resumo por período de apuração do ICMS (layout fictício para teste)";
    }

    @Override
    public void extrair(DocumentoPdf pdf, Extracao e) {
        empresa(pdf, e);
        e.inicio = e.fim = emissao(pdf);
        // Período | Débitos | Créditos | Saldo credor anterior | ICMS a recolher | Saldo credor a transportar | ICMS-ST retido | recolhido
        for (Registro r : DocumentoPdf.tabela(pdf.secao(Pattern.compile("Endereço .*"), Set.of()),
                Pattern.compile("\\d{2}/\\d{4}"))) {
            YearMonth mes = mes(r.get(0));
            e.saldos.add(new SaldoFiscal("SALDO_CREDOR_ICMS", "PR", null, "Saldo credor de ICMS a transportar (RTI)",
                    mes.atDay(1), mes.atEndOfMonth(), valor(r.get(3)), valor(r.get(5)), "PDF p." + r.origem().pagina(),
                    r.origem().linha()));
        }
        e.alerta(Severidade.INFO, "RTI_SEM_CAPACIDADE", "Este RTI (layout " + VERSAO + ", conteúdo presumido) não traz "
                + "classificação nem capacidade de pagamento: nada foi gravado como capacidade oficial do PR. Foram lidos "
                + e.saldos.size() + " saldos credores mensais de ICMS.", null);
    }
}
