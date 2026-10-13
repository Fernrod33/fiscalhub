package com.fiscalhub.backend;

import com.fiscalhub.backend.entity.enums.DocumentoEsfera;
import com.fiscalhub.backend.entity.enums.DocumentoJuridicao;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.util.DocumentoFiscalClassificationResolver;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentoFiscalClassificationTest {

    @Test
    void deveClassificarIpiComoFederal() {
        assertEquals(DocumentoEsfera.FEDERAL, DocumentoFiscalClassificationResolver.classificarEsfera(TipoTributo.IPI));
        assertEquals(DocumentoJuridicao.UNIAO, DocumentoFiscalClassificationResolver.classificarJuridicao(TipoTributo.IPI));
    }

    @Test
    void deveClassificarIcmsComoEstadualParana() {
        assertEquals(DocumentoEsfera.ESTADUAL, DocumentoFiscalClassificationResolver.classificarEsfera(TipoTributo.ICMS));
        assertEquals(DocumentoJuridicao.PARANA, DocumentoFiscalClassificationResolver.classificarJuridicao(TipoTributo.ICMS));
    }

    @Test
    void devePermitirEfdIcmsIpiComMultiplosTributos() {
        Set<TipoTributo> tributos = Set.of(TipoTributo.ICMS, TipoTributo.IPI);
        assertEquals(DocumentoTipo.EFD_ICMS_IPI, DocumentoTipo.fromTributos(tributos));
        assertEquals(DocumentoEsfera.ESTADUAL, DocumentoFiscalClassificationResolver.classificarEsfera(tributos));
    }
}
