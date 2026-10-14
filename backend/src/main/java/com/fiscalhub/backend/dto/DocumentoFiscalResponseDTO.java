package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.enums.DocumentoEsfera;
import com.fiscalhub.backend.entity.enums.DocumentoJuridicao;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.entity.enums.TipoTributo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

public record DocumentoFiscalResponseDTO(Long id, Long clienteId, Long usuarioId, DocumentoTipo tipo,
                                         DocumentoEsfera esfera, DocumentoJuridicao juridicao, Set<TipoTributo> tributos,
                                         LocalDate periodoInicio, LocalDate periodoFim, String nomeOriginalArquivo,
                                         String extensao, String mimeType, Long tamanhoBytes,
                                         LocalDateTime dataImportacao, DocumentoStatus status, Boolean retificacao,
                                         String observacao, String sha256) {

    public static DocumentoFiscalResponseDTO fromEntity(DocumentoFiscal d) {
        return new DocumentoFiscalResponseDTO(d.getId(), d.getCliente().getId(), d.getUsuario().getId(), d.getTipo(),
                d.getEsfera(), d.getJuridicao(), d.getTributos(), d.getPeriodoInicio(), d.getPeriodoFim(),
                d.getNomeOriginalArquivo(), d.getExtensao(), d.getMimeType(), d.getTamanhoBytes(),
                d.getDataImportacao(), d.getStatus(), d.getRetificacao(), d.getObservacao(), d.getSha256());
    }
}
