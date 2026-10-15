package com.fiscalhub.backend.contabil.ecd;

import com.fiscalhub.backend.contabil.RepositorioContabil;
import com.fiscalhub.backend.contabil.referencial.MontagemReferencial;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.importacao.ContextoProcessamento;
import com.fiscalhub.backend.importacao.Escrituracoes;
import com.fiscalhub.backend.importacao.ProcessadorDocumento;
import com.fiscalhub.backend.sped.LayoutSped;
import org.springframework.stereotype.Component;

/**
 * ECD (SPED Contábil): plano de contas (I050/I051), saldos periódicos (I155), resultado antes do encerramento
 * (I350/I355), demonstrações publicadas (J005/J100/J150) e contagem (9900/9999). Leiautes cobertos e fonte de cada
 * campo: src/main/resources/sped/ecd-leiautes.properties (gerado dos manuais oficiais). Depois da leitura, monta o
 * Balanço e a DRE referenciais e confere com as publicadas.
 */
@Component
public class ProcessadorEcd implements ProcessadorDocumento {

    static final LayoutSped LAYOUT = LayoutSped.carregar("sped/ecd-leiautes.properties");

    private final RepositorioContabil repositorio;
    private final Escrituracoes escrituracoes;
    private final MontagemReferencial montagem;

    public ProcessadorEcd(RepositorioContabil repositorio, Escrituracoes escrituracoes, MontagemReferencial montagem) {
        this.repositorio = repositorio;
        this.escrituracoes = escrituracoes;
        this.montagem = montagem;
    }

    @Override
    public boolean suporta(DocumentoTipo tipo) {
        return tipo == DocumentoTipo.ECD;
    }

    @Override
    public void processar(DocumentoFiscal documento, ContextoProcessamento contexto) {
        LeituraEcd.Lida lida = new LeituraEcd(documento, contexto, escrituracoes, repositorio, LAYOUT).ler();
        montagem.montar(new MontagemReferencial.Pedido(lida.escrituracaoId(), lida.planoReferencial(), lida.inicio(),
                lida.fim(), "I155", "I355", lida.balancoPublicado(), lida.resultadoPublicado()), contexto);
    }
}
