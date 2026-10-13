package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.JobProcessamento;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.StatusJob;

import java.time.LocalDateTime;

/** Andamento do processamento de um documento importado. */
public record StatusDocumentoDTO(Long documentoId, DocumentoStatus status, StatusJob job, Integer tentativas,
                                 String mensagem, LocalDateTime inicio, LocalDateTime fim) {

    public static StatusDocumentoDTO de(JobProcessamento j) {
        return new StatusDocumentoDTO(j.getDocumento().getId(), j.getDocumento().getStatus(), j.getStatus(),
                j.getTentativas(), j.getMensagem(), j.getInicio(), j.getFim());
    }
}
