package com.fiscalhub.backend.service;

import com.fiscalhub.backend.dto.DocumentoFiscalRequestDTO;
import com.fiscalhub.backend.dto.DocumentoFiscalResponseDTO;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.Empresa;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.fiscalhub.backend.util.DocumentoFiscalClassificationResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class DocumentoFiscalService {

    private static final Set<String> EXTENSOES_PERMITIDAS = Set.of(".pdf", ".xml", ".csv", ".xlsx");
    private static final Set<String> MIME_PERMITIDOS = Set.of(
            "application/pdf",
            "application/xml",
            "text/xml",
            "text/csv",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final AcessoRecursoService acessoRecursoService;

    @Value("${fiscalhub.upload.max-size-bytes:5242880}")
    private long maxUploadSizeBytes;

    @Value("${fiscalhub.upload.directory:./uploads/documentos}")
    private String uploadDirectory;

    public DocumentoFiscalService(DocumentoFiscalRepository documentoFiscalRepository,
                                 AcessoRecursoService acessoRecursoService) {
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.acessoRecursoService = acessoRecursoService;
    }

    public List<DocumentoFiscalResponseDTO> listarPorEmpresa(Long empresaId, String username) {
        acessoRecursoService.empresa(empresaId, username);
        return documentoFiscalRepository.findByEmpresaIdOrderByDataImportacaoDesc(empresaId)
                .stream()
                .map(DocumentoFiscalResponseDTO::fromEntity)
                .toList();
    }

    public DocumentoFiscalResponseDTO consultarPorId(Long empresaId, Long documentoId, String username) {
        return DocumentoFiscalResponseDTO.fromEntity(acessoRecursoService.documento(empresaId, documentoId, username));
    }

    public DocumentoFiscalResponseDTO importar(Long empresaId, MultipartFile arquivo, DocumentoFiscalRequestDTO dto, String username) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new BusinessRuleException("Arquivo é obrigatório.");
        }

        Empresa empresa = acessoRecursoService.empresa(empresaId, username);
        validarArquivo(arquivo);
        validarMetadados(dto);

        String extensao = extrairExtensao(arquivo.getOriginalFilename());
        String mimeType = arquivo.getContentType() != null ? arquivo.getContentType() : "application/octet-stream";
        String nomeArquivoInterno = UUID.randomUUID() + extensao;
        Path destino = Paths.get(uploadDirectory).resolve(nomeArquivoInterno).normalize();

        try {
            Files.createDirectories(destino.getParent());
            Files.copy(arquivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessRuleException("Não foi possível armazenar o arquivo importado.", e);
        }

        DocumentoFiscal documento = new DocumentoFiscal();
        documento.setEmpresa(empresa);
        documento.setUsuario(empresa.getUsuario());
        documento.setTipo(dto.getTipo());
        documento.setEsfera(dto.getEsfera() != null ? dto.getEsfera()
                : DocumentoFiscalClassificationResolver.classificarEsfera(dto.getTributos()));
        documento.setJuridicao(dto.getJuridicao() != null ? dto.getJuridicao()
                : DocumentoFiscalClassificationResolver.classificarJuridicao(dto.getTributos()));
        documento.setTributos(Set.copyOf(dto.getTributos()));
        documento.setPeriodoInicio(dto.getPeriodoInicio());
        documento.setPeriodoFim(dto.getPeriodoFim());
        documento.setNomeOriginalArquivo(arquivo.getOriginalFilename());
        documento.setNomeArquivoInterno(nomeArquivoInterno);
        documento.setExtensao(extensao);
        documento.setMimeType(mimeType);
        documento.setTamanhoBytes(arquivo.getSize());
        documento.setStatus(DocumentoStatus.RECEBIDO);
        documento.setRetificacao(Boolean.TRUE.equals(dto.getRetificacao()));
        documento.setObservacao(dto.getObservacao());
        documento.setCaminhoArquivo(destino.toString());

        return DocumentoFiscalResponseDTO.fromEntity(documentoFiscalRepository.save(documento));
    }

    public byte[] downloadArquivo(Long empresaId, Long documentoId, String username) {
        DocumentoFiscal documento = acessoRecursoService.documento(empresaId, documentoId, username);

        Path path = Paths.get(documento.getCaminhoArquivo());
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("Arquivo original não encontrado no armazenamento.");
        }

        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new BusinessRuleException("Não foi possível recuperar o arquivo solicitado.", e);
        }
    }

    private void validarArquivo(MultipartFile arquivo) {
        if (arquivo.getSize() > maxUploadSizeBytes) {
            throw new BusinessRuleException("Arquivo excede o tamanho máximo permitido de " + maxUploadSizeBytes + " bytes.");
        }
        if (!EXTENSOES_PERMITIDAS.contains(extrairExtensao(arquivo.getOriginalFilename()))) {
            throw new BusinessRuleException("Tipo de arquivo não permitido. Use PDF, XML, CSV ou XLSX.");
        }
        String mimeType = arquivo.getContentType();
        if (mimeType != null && !MIME_PERMITIDOS.contains(mimeType)) {
            throw new BusinessRuleException("MIME type inválido para o arquivo importado.");
        }
    }

    private void validarMetadados(DocumentoFiscalRequestDTO dto) {
        if (dto == null) {
            throw new BusinessRuleException("Dados do documento são obrigatórios.");
        }
        if (dto.getTipo() == null) {
            throw new BusinessRuleException("Tipo do documento é obrigatório.");
        }
        if (dto.getPeriodoInicio() == null || dto.getPeriodoFim() == null) {
            throw new BusinessRuleException("Período inicial e final são obrigatórios.");
        }
        if (dto.getPeriodoInicio().isAfter(dto.getPeriodoFim())) {
            throw new BusinessRuleException("Período final deve ser maior ou igual ao período inicial.");
        }
        if (dto.getTributos() == null || dto.getTributos().isEmpty()) {
            throw new BusinessRuleException("Informe pelo menos um tributo relacionado ao documento.");
        }
    }

    private String extrairExtensao(String nomeArquivo) {
        if (nomeArquivo == null || nomeArquivo.isBlank()) {
            return "";
        }
        int indice = nomeArquivo.lastIndexOf('.');
        return indice >= 0 ? nomeArquivo.substring(indice).toLowerCase() : "";
    }
}
