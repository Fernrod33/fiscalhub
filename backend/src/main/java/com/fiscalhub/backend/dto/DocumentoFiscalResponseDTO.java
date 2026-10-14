package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Documento importado. cliente* vazios enquanto o documento aguarda cliente; cnpjArquivo e nomeEmpresaArquivo são a
 * empresa lida do arquivo (sugestão de cadastro).
 */
public record DocumentoFiscalResponseDTO(Long id, Long clienteId, String clienteRazaoSocial, Long usuarioId,
                                         DocumentoTipo tipo, DocumentoTipo tipoInformado, LocalDate periodoInicio,
                                         LocalDate periodoFim, String nomeOriginalArquivo, String extensao, String mimeType,
                                         Long tamanhoBytes, LocalDateTime dataImportacao, DocumentoStatus status,
                                         String observacao, String sha256, String cnpjArquivo, String nomeEmpresaArquivo) {

    public static DocumentoFiscalResponseDTO fromEntity(DocumentoFiscal d) {
        Cliente c = d.getCliente();
        return new DocumentoFiscalResponseDTO(d.getId(), c == null ? null : c.getId(), c == null ? null : c.getRazaoSocial(),
                d.getUsuario().getId(), d.getTipo(), d.getTipoInformado(), d.getPeriodoInicio(), d.getPeriodoFim(),
                d.getNomeOriginalArquivo(), d.getExtensao(), d.getMimeType(), d.getTamanhoBytes(), d.getDataImportacao(),
                d.getStatus(), d.getObservacao(), d.getSha256(), d.getCnpjArquivo(), d.getNomeEmpresaArquivo());
    }
}
