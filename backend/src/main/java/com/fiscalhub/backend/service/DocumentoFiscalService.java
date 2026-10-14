package com.fiscalhub.backend.service;

import com.fiscalhub.backend.dto.DocumentoFiscalRequestDTO;
import com.fiscalhub.backend.dto.ArquivoDocumento;
import com.fiscalhub.backend.dto.DocumentoFiscalResponseDTO;
import com.fiscalhub.backend.dto.StatusDocumentoDTO;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.exception.DuplicateResourceException;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.fiscalhub.backend.repository.JobProcessamentoRepository;
import com.fiscalhub.backend.storage.ArmazenamentoArquivos;
import com.fiscalhub.backend.util.DocumentoFiscalClassificationResolver;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class DocumentoFiscalService {

    /** Extensão permitida -> tipos de conteúdo aceitos (o navegador pode mandar mais de um para CSV). */
    private static final Map<String, Set<String>> TIPOS_PERMITIDOS = Map.of(
            ".txt", Set.of("text/plain"),
            ".pdf", Set.of("application/pdf"),
            ".csv", Set.of("text/csv", "text/plain", "application/vnd.ms-excel"),
            ".xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final AcessoRecursoService acessoRecursoService;
    private final UsuarioRepository usuarioRepository;
    private final ArmazenamentoArquivos armazenamento;
    private final ProcessamentoDocumentoService processamento;
    private final JobProcessamentoRepository jobRepository;



    public DocumentoFiscalService(DocumentoFiscalRepository documentoFiscalRepository,
                                 AcessoRecursoService acessoRecursoService,
                                  UsuarioRepository usuarioRepository,
                                 ArmazenamentoArquivos armazenamento,
                                 ProcessamentoDocumentoService processamento,
                                 JobProcessamentoRepository jobRepository) {
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.acessoRecursoService = acessoRecursoService;
        this.usuarioRepository = usuarioRepository;
        this.armazenamento = armazenamento;
        this.processamento = processamento;
        this.jobRepository = jobRepository;
    }

    public List<DocumentoFiscalResponseDTO> listarPorCliente(Long clienteId, UsuarioAutenticado ua) {
        acessoRecursoService.cliente(clienteId, ua);
        return documentoFiscalRepository.findByClienteIdOrderByDataImportacaoDesc(clienteId)
                .stream()
                .map(DocumentoFiscalResponseDTO::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<DocumentoFiscalResponseDTO> listarDoEscritorio(UsuarioAutenticado ua, Long clienteId, DocumentoTipo tipo,
                                                               DocumentoStatus status, Pageable pageable) {
        return documentoFiscalRepository.buscarDoEscritorio(ua.escritorioId(), clienteId, tipo, status, pageable)
                .map(DocumentoFiscalResponseDTO::fromEntity);
    }

    /** Quantidade de documentos do escritório por status (KPIs do painel). */
    @Transactional(readOnly = true)
    public Map<DocumentoStatus, Long> resumo(UsuarioAutenticado ua) {
        Map<DocumentoStatus, Long> resumo = new EnumMap<>(DocumentoStatus.class);
        documentoFiscalRepository.contarPorStatus(ua.escritorioId()).forEach(c -> resumo.put(c.getStatus(), c.getTotal()));
        return resumo;
    }

    public DocumentoFiscalResponseDTO consultarPorId(Long clienteId, Long documentoId, UsuarioAutenticado ua) {
        return DocumentoFiscalResponseDTO.fromEntity(acessoRecursoService.documento(clienteId, documentoId, ua));
    }

    public DocumentoFiscalResponseDTO importar(Long clienteId, MultipartFile arquivo, DocumentoFiscalRequestDTO dto, UsuarioAutenticado ua) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new BusinessRuleException("Arquivo é obrigatório.");
        }

        Cliente cliente = acessoRecursoService.cliente(clienteId, ua);
        validarArquivo(arquivo);
        validarMetadados(dto);

        String extensao = extrairExtensao(arquivo.getOriginalFilename());
        String mimeType = arquivo.getContentType() != null ? arquivo.getContentType() : "application/octet-stream";
        String nomeArquivoInterno = UUID.randomUUID() + extensao;
        String chave = "clientes/" + cliente.getId() + "/" + nomeArquivoInterno;

        // ponytail: upload em uma requisição só; upload em partes (retomável) quando houver arquivos acima
        // de 500 MB ou conexões instáveis
        String sha256 = gravarCalculandoSha256(chave, arquivo, mimeType);
        documentoFiscalRepository.findByClienteIdAndSha256(cliente.getId(), sha256).ifPresent(existente -> {
            armazenamento.excluir(chave);
            throw new DuplicateResourceException("Este arquivo já foi importado para o cliente.", existente.getId());
        });

        DocumentoFiscal documento = new DocumentoFiscal();
        documento.setCliente(cliente);
        documento.setUsuario(usuarioRepository.getReferenceById(ua.id()));
        documento.setTipo(dto.getTipo());
        documento.setEsfera(dto.getEsfera() != null ? dto.getEsfera()
                : DocumentoFiscalClassificationResolver.classificarEsfera(dto.getTributos()));
        documento.setJuridicao(dto.getJuridicao() != null ? dto.getJuridicao()
                : DocumentoFiscalClassificationResolver.classificarJuridicao(dto.getTributos()));
        documento.setTributos(Set.copyOf(dto.getTributos()));
        documento.setPeriodoInicio(dto.getPeriodoInicio());
        documento.setPeriodoFim(dto.getPeriodoFim());
        documento.setNomeOriginalArquivo(arquivo.getOriginalFilename());
        documento.setExtensao(extensao);
        documento.setMimeType(mimeType);
        documento.setTamanhoBytes(arquivo.getSize());
        documento.setStatus(DocumentoStatus.RECEBIDO);
        documento.setRetificacao(Boolean.TRUE.equals(dto.getRetificacao()));
        documento.setObservacao(dto.getObservacao());
        documento.setChaveArmazenamento(chave);
        documento.setSha256(sha256);

        documentoFiscalRepository.save(documento);
        processamento.enfileirar(documento);
        return DocumentoFiscalResponseDTO.fromEntity(documento);
    }

    @Transactional(readOnly = true)
    public ArquivoDocumento abrirArquivo(Long clienteId, Long documentoId, UsuarioAutenticado ua) {
        DocumentoFiscal documento = acessoRecursoService.documento(clienteId, documentoId, ua);
        if (!armazenamento.existe(documento.getChaveArmazenamento())) {
            throw new ResourceNotFoundException("Arquivo original não encontrado no armazenamento.");
        }
        return new ArquivoDocumento(documento.getNomeOriginalArquivo(), documento.getMimeType(),
                documento.getTamanhoBytes(), armazenamento.abrir(documento.getChaveArmazenamento()));
    }

    @Transactional(readOnly = true)
    public StatusDocumentoDTO status(Long clienteId, Long documentoId, UsuarioAutenticado ua) {
        DocumentoFiscal documento = acessoRecursoService.documento(clienteId, documentoId, ua);
        return jobRepository.findFirstByDocumentoIdOrderByIdDesc(documento.getId())
                .map(StatusDocumentoDTO::de)
                .orElseThrow(() -> new ResourceNotFoundException("Documento sem processamento registrado."));
    }

    /** Exclui registro e arquivo. Documento com débitos gerados não pode ser excluído (409 pela FK). */
    public void excluir(Long clienteId, Long documentoId, UsuarioAutenticado ua) {
        DocumentoFiscal documento = acessoRecursoService.documento(clienteId, documentoId, ua);
        documentoFiscalRepository.delete(documento);
        documentoFiscalRepository.flush(); // falha de FK antes de apagar o arquivo
        armazenamento.excluir(documento.getChaveArmazenamento());
    }

    private String gravarCalculandoSha256(String chave, MultipartFile arquivo, String mimeType) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream in = new DigestInputStream(arquivo.getInputStream(), digest)) {
            armazenamento.salvar(chave, in, arquivo.getSize(), mimeType);
        } catch (IOException e) {
            throw new BusinessRuleException("Não foi possível armazenar o arquivo importado.", e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Tamanho máximo é validado pelo multipart (spring.servlet.multipart.*): estoura com 413. */
    private void validarArquivo(MultipartFile arquivo) {
        Set<String> tiposAceitos = TIPOS_PERMITIDOS.get(extrairExtensao(arquivo.getOriginalFilename()));
        if (tiposAceitos == null) {
            throw new BusinessRuleException("Tipo de arquivo não permitido. Use TXT (SPED), PDF, CSV ou XLSX.");
        }
        if (arquivo.getContentType() != null && !tiposAceitos.contains(arquivo.getContentType())) {
            throw new BusinessRuleException("Tipo de conteúdo inválido para o arquivo importado.");
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
