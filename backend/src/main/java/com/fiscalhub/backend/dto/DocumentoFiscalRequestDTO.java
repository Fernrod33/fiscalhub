package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.enums.DocumentoEsfera;
import com.fiscalhub.backend.entity.enums.DocumentoJuridicao;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

@Data
public class DocumentoFiscalRequestDTO {
    private DocumentoTipo tipo;
    private DocumentoEsfera esfera;
    private DocumentoJuridicao juridicao;
    private Set<TipoTributo> tributos;
    private LocalDate periodoInicio;
    private LocalDate periodoFim;
    private String observacao;
    private Boolean retificacao = false;
}
