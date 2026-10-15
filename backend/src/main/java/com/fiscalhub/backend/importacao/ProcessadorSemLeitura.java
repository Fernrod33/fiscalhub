package com.fiscalhub.backend.importacao;

import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Último da fila: tipos ainda sem leitura (PDF, documentos da Fase 2b...) ficam guardados no storage, com um alerta. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProcessadorSemLeitura implements ProcessadorDocumento {

    @Override
    public boolean suporta(DocumentoTipo tipo) {
        return true;
    }

    @Override
    public void processar(DocumentoFiscal documento, ContextoProcessamento contexto) {
        String tipo = documento.getTipo() == DocumentoTipo.OUTRO ? "deste tipo de arquivo" : "de " + documento.getTipo();
        contexto.registrar(Severidade.ALERTA, null, null, "TIPO_SEM_LEITURA",
                "A leitura " + tipo + " ainda não está disponível. O arquivo fica guardado e poderá ser reprocessado.");
    }
}
