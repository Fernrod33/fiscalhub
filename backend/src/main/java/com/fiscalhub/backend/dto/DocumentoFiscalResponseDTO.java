package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.enums.DocumentoEsfera;
import com.fiscalhub.backend.entity.enums.DocumentoJuridicao;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Data
public class DocumentoFiscalResponseDTO {
    private Long id;
    private Long clienteId;
    private Long usuarioId;
    private DocumentoTipo tipo;
    private DocumentoEsfera esfera;
    private DocumentoJuridicao juridicao;
    private Set<TipoTributo> tributos;
    private LocalDate periodoInicio;
    private LocalDate periodoFim;
    private String nomeOriginalArquivo;
    private String nomeArquivoInterno;
    private String extensao;
    private String mimeType;
    private Long tamanhoBytes;
    private LocalDateTime dataImportacao;
    private DocumentoStatus status;
    private Boolean retificacao;
    private String observacao;
    private Long documentoOriginalId;

    public static DocumentoFiscalResponseDTO fromEntity(DocumentoFiscal documento) {
        DocumentoFiscalResponseDTO dto = new DocumentoFiscalResponseDTO();
        dto.setId(documento.getId());
        dto.setClienteId(documento.getCliente() != null ? documento.getCliente().getId() : null);
        dto.setUsuarioId(documento.getUsuario() != null ? documento.getUsuario().getId() : null);
        dto.setTipo(documento.getTipo());
        dto.setEsfera(documento.getEsfera());
        dto.setJuridicao(documento.getJuridicao());
        dto.setTributos(documento.getTributos());
        dto.setPeriodoInicio(documento.getPeriodoInicio());
        dto.setPeriodoFim(documento.getPeriodoFim());
        dto.setNomeOriginalArquivo(documento.getNomeOriginalArquivo());
        dto.setNomeArquivoInterno(documento.getNomeArquivoInterno());
        dto.setExtensao(documento.getExtensao());
        dto.setMimeType(documento.getMimeType());
        dto.setTamanhoBytes(documento.getTamanhoBytes());
        dto.setDataImportacao(documento.getDataImportacao());
        dto.setStatus(documento.getStatus());
        dto.setRetificacao(documento.getRetificacao());
        dto.setObservacao(documento.getObservacao());
        dto.setDocumentoOriginalId(documento.getDocumentoOriginal() != null ? documento.getDocumentoOriginal().getId() : null);
        return dto;
    }
}
