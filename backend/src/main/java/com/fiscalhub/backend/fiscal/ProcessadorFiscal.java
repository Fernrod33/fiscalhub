package com.fiscalhub.backend.fiscal;

import com.fiscalhub.backend.contabil.RepositorioContabil;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.importacao.ContextoProcessamento;
import com.fiscalhub.backend.importacao.Escrituracoes;
import com.fiscalhub.backend.importacao.ProcessadorDocumento;
import com.fiscalhub.backend.sped.LayoutSped;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Escriturações fiscais SPED: ECF, EFD-Contribuições e EFD ICMS/IPI. Cada uma tem a sua leitura (sobre o LeituraSped
 * comum) e grava no modelo único de apuração. Depois da ECF, confere com a ECD do mesmo exercício.
 */
@Component
public class ProcessadorFiscal implements ProcessadorDocumento {

    static final LayoutSped ECF = LayoutSped.carregar("sped/ecf-leiautes.properties");
    static final LayoutSped EFD_CONTRIBUICOES = LayoutSped.carregar("sped/efd-contribuicoes-leiautes.properties");
    static final LayoutSped EFD_ICMS_IPI = LayoutSped.carregar("sped/efd-icms-ipi-leiautes.properties");

    private final Escrituracoes escrituracoes;
    private final RepositorioApuracao apuracoes;
    private final RepositorioContabil contabil;
    private final ConferenciaEcdEcf conferencia;

    public ProcessadorFiscal(Escrituracoes escrituracoes, RepositorioApuracao apuracoes, RepositorioContabil contabil,
                             ConferenciaEcdEcf conferencia) {
        this.escrituracoes = escrituracoes;
        this.apuracoes = apuracoes;
        this.contabil = contabil;
        this.conferencia = conferencia;
    }

    @Override
    public boolean suporta(DocumentoTipo tipo) {
        return Set.of(DocumentoTipo.ECF, DocumentoTipo.EFD_CONTRIBUICOES, DocumentoTipo.EFD_ICMS_IPI).contains(tipo);
    }

    @Override
    public void processar(DocumentoFiscal documento, ContextoProcessamento contexto) {
        switch (documento.getTipo()) {
            case ECF -> {
                new LeituraEcf(documento, contexto, escrituracoes, apuracoes, contabil, ECF).executar();
                conferencia.conferir(documento.getCliente().getId(), documento.getPeriodoFim().getYear(), contexto);
            }
            case EFD_CONTRIBUICOES -> new LeituraEfdContribuicoes(documento, contexto, escrituracoes, apuracoes, EFD_CONTRIBUICOES).executar();
            default -> new LeituraEfdIcmsIpi(documento, contexto, escrituracoes, apuracoes, EFD_ICMS_IPI).executar();
        }
    }
}
