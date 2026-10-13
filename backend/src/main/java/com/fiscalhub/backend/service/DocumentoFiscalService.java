package com.fiscalhub.backend.service;

import com.fiscalhub.backend.dto.DocumentoFiscalRequestDTO;
import com.fiscalhub.backend.dto.DocumentoFiscalResponseDTO;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.Empresa;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.DocumentoEsfera;
import com.fiscalhub.backend.entity.enums.DocumentoJuridicao;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.exception.ForbiddenException;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class DocumentoFiscalService {

    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final EmpresaService empresaService;
    private final UsuarioService usuarioService;

    @Value("${fiscalhub.upload.max-size-bytes:5242880}")
    private long maxUploadSizeBytes;

    @Value("${fiscalhub.upload.directory:./uploads/documentos}")
    private String uploadDirectory;

    public DocumentoFiscalService(DocumentoFiscalRepository documentoFiscalRepository,
                                 EmpresaService empresaService,
                                 UsuarioService usuarioService) {
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.empresaService = empresaService;
        this.usuarioService = usuarioService;
    }

    public List<DocumentoFiscalResponseDTO> listarPorEmpresa(Long empresaId, String username) {
        Empresa empresa = empresaService.findById(empresaId);
        validarAcesso(empresa, username);
        return documentoFiscalRepository.findByEmpresaIdOrderByDataImportacaoDesc(empresaId)
                .stream()
                .map(DocumentoFiscalResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public DocumentoFiscalResponseDTO consultarPorId(Long empresaId, Long documentoId, String username) {
        Empresa empresa = empresaService.findById(empresaId);
        validarAcesso(empresa, username);
        DocumentoFiscal documento = documentoFiscalRepository.findByIdAndEmpresaId(documentoId, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento não encontrado para a empresa informada."));
        return DocumentoFiscalResponseDTO.fromEntity(documento);
    }

    public DocumentoFiscalResponseDTO importar(Long empresaId, MultipartFile arquivo, DocumentoFiscalRequestDTO dto, String username) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new BusinessRuleException("Arquivo é obrigatório.");
        }

        Empresa empresa = empresaService.findById(empresaId);
        validarAcesso(empresa, username);
        validarArquivo(arquivo);
        validarMetadados(dto);

        Usuario usuario = usuarioService.findByEmail(username);

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
        documento.setUsuario(usuario);
        documento.setTipo(dto.getTipo() != null ? dto.getTipo() : DocumentoTipo.OUTRO);
        documento.setEsfera(resolveEsfera(dto.getTributos(), dto.getEsfera()));
        documento.setJuridicao(resolveJuridicao(dto.getTributos(), dto.getJuridicao()));
        documento.setTributos(normalizarTributos(dto.getTributos()));
        documento.setPeriodoInicio(dto.getPeriodoInicio() != null ? dto.getPeriodoInicio() : LocalDate.now());
        documento.setPeriodoFim(dto.getPeriodoFim() != null ? dto.getPeriodoFim() : documento.getPeriodoInicio());
        documento.setNomeOriginalArquivo(arquivo.getOriginalFilename());
        documento.setNomeArquivoInterno(nomeArquivoInterno);
        documento.setExtensao(extensao);
        documento.setMimeType(mimeType);
        documento.setTamanhoBytes(arquivo.getSize());
        documento.setStatus(DocumentoStatus.RECEBIDO);
        documento.setRetificacao(Boolean.TRUE.equals(dto.getRetificacao()));
        documento.setObservacao(dto.getObservacao());
        documento.setCaminhoArquivo(destino.toString());

        if (documento.getPeriodoInicio().isAfter(documento.getPeriodoFim())) {
            throw new BusinessRuleException("Período final deve ser maior ou igual ao período inicial.");
        }

        DocumentoFiscal saved = documentoFiscalRepository.save(documento);
        return DocumentoFiscalResponseDTO.fromEntity(saved);
    }

    public byte[] downloadArquivo(Long empresaId, Long documentoId, String username) {
        Empresa empresa = empresaService.findById(empresaId);
        validarAcesso(empresa, username);
        DocumentoFiscal documento = documentoFiscalRepository.findByIdAndEmpresaId(documentoId, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento não encontrado para a empresa informada."));

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

    private void validarAcesso(Empresa empresa, String username) {
        if (!empresa.getUsuario().getEmail().equals(username)) {
            throw new ForbiddenException("Acesso negado: você não possui acesso a este documento.");
        }
    }

    private void validarArquivo(MultipartFile arquivo) {
        if (arquivo.getSize() > maxUploadSizeBytes) {
            throw new BusinessRuleException("Arquivo excede o tamanho máximo permitido de " + maxUploadSizeBytes + " bytes.");
        }

        String nome = arquivo.getOriginalFilename() != null ? arquivo.getOriginalFilename().toLowerCase() : "";
        String extensao = extrairExtensao(nome);
        Set<String> permitidas = Set.of(".pdf", ".xml", ".csv", ".xlsx");
        if (!permitidas.contains(extensao)) {
            throw new BusinessRuleException("Tipo de arquivo não permitido. Use PDF, XML, CSV ou XLSX.");
        }

        String mimeType = arquivo.getContentType();
        Set<String> mimePermitidos = Set.of(
                "application/pdf",
                "application/xml",
                "text/xml",
                "text/csv",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
        if (mimeType != null && !mimePermitidos.contains(mimeType)) {
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
        if (dto.getTributos() == null || dto.getTributos().isEmpty()) {
            throw new BusinessRuleException("Informe pelo menos um tributo relacionado ao documento.");
        }
        if (dto.getEsfera() == null && dto.getTributos() != null && dto.getTributos().contains(TipoTributo.ICMS)) {
            dto.setEsfera(DocumentoEsfera.ESTADUAL);
        }
        if (dto.getJuridicao() == null && dto.getTributos() != null && dto.getTributos().contains(TipoTributo.ICMS)) {
            dto.setJuridicao(DocumentoJuridicao.PARANA);
        }
    }

    private Set<TipoTributo> normalizarTributos(Set<TipoTributo> tributos) {
        if (tributos == null || tributos.isEmpty()) {
            throw new BusinessRuleException("Informe pelo menos um tributo relacionado ao documento.");
        }
        return Set.copyOf(tributos);
    }

    private DocumentoEsfera resolveEsfera(Set<TipoTributo> tributos, DocumentoEsfera informada) {
        if (informada != null) {
            return informada;
        }
        return DocumentoFiscalClassificationResolver.classificarEsfera(tributos);
    }

    private DocumentoJuridicao resolveJuridicao(Set<TipoTributo> tributos, DocumentoJuridicao informada) {
        if (informada != null) {
            return informada;
        }
        return DocumentoFiscalClassificationResolver.classificarJuridicao(tributos);
    }

    private String extrairExtensao(String nomeArquivo) {
        if (nomeArquivo == null || nomeArquivo.isBlank()) {
            return "";
        }
        int indice = nomeArquivo.lastIndexOf('.');
        return indice >= 0 ? nomeArquivo.substring(indice).toLowerCase() : "";
    }
}
