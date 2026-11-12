package com.fiscalhub.backend.importacao;

import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import org.springframework.stereotype.Component;

/** Conteúdo que o detector não reconhece (OUTRO): fica guardado no storage, com um alerta; todo outro tipo tem leitura. */
@Component
public class ProcessadorSemLeitura implements ProcessadorDocumento {

    @Override
    public boolean suporta(DocumentoTipo tipo) {
        return tipo == DocumentoTipo.OUTRO;
    }

    @Override
    public void processar(DocumentoFiscal documento, ContextoProcessamento contexto) {
        try (var in = contexto.abrir()) {
            String inicio = new String(in.readNBytes(120), java.nio.charset.StandardCharsets.ISO_8859_1).split("[\\r\\n]")[0];
            documento.setAssinaturaLayout(AguardandoUsuario.assinatura(documento.getTipo() + " " + inicio));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        contexto.registrar(Severidade.ALERTA, null, null, "TIPO_SEM_LEITURA",
                "A leitura deste tipo de arquivo ainda não está disponível. O arquivo fica guardado e poderá ser reprocessado.");
    }
}
