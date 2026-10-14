package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Parte opcional do upload. O tipo é só sugestão: vale o detectado no conteúdo. */
@Data
public class DocumentoFiscalRequestDTO {
    private DocumentoTipo tipo;

    @Size(max = 1000)
    private String observacao;
}
