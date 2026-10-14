package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.JobProcessamento;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.EtapaProcessamento;
import com.fiscalhub.backend.entity.enums.StatusJob;

import java.time.LocalDateTime;

/** Andamento do processamento: etapa e percentual (bytes lidos / tamanho) do job mais recente. */
public record StatusDocumentoDTO(Long documentoId, DocumentoStatus status, StatusJob job, EtapaProcessamento etapa,
                                 Integer progresso, Integer tentativas, String mensagem, LocalDateTime inicio,
                                 LocalDateTime fim) {

    public static StatusDocumentoDTO de(JobProcessamento j) {
        return new StatusDocumentoDTO(j.getDocumento().getId(), j.getDocumento().getStatus(), j.getStatus(), j.getEtapa(),
                j.getProgresso(), j.getTentativas(), j.getMensagem(), j.getInicio(), j.getFim());
    }
}
