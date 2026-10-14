package com.fiscalhub.backend.service;

import com.fiscalhub.backend.dto.ArquivoDocumento;
import com.fiscalhub.backend.dto.ClienteRequestDTO;
import com.fiscalhub.backend.dto.DocumentoFiscalRequestDTO;
import com.fiscalhub.backend.dto.DocumentoFiscalResponseDTO;
import com.fiscalhub.backend.dto.InconsistenciaDTO;
import com.fiscalhub.backend.dto.StatusDocumentoDTO;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.entity.enums.StatusJob;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.exception.DuplicateResourceException;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.importacao.InconsistenciaRepository;
import com.fiscalhub.backend.importacao.ProcessamentoDocumentoService;
import com.fiscalhub.backend.repository.ClienteRepository;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.fiscalhub.backend.repository.EscritorioRepository;
import com.fiscalhub.backend.repository.JobProcessamentoRepository;
import com.fiscalhub.backend.repository.UsuarioRepository;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.storage.ArmazenamentoArquivos;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Documentos importados: upload (no escritório ou dentro de um cliente), consulta, reprocessamento e exclusão. */
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
    private final EscritorioRepository escritorioRepository;
    private final ClienteRepository clienteRepository;
    private final ClienteService clienteService;
    private final ArmazenamentoArquivos armazenamento;
    private final ProcessamentoDocumentoService processamento;
    private final JobProcessamentoRepository jobRepository;
    private final InconsistenciaRepository inconsistenciaRepository;

    public DocumentoFiscalService(DocumentoFiscalRepository documentoFiscalRepository, AcessoRecursoService acessoRecursoService,
                                  UsuarioRepository usuarioRepository, EscritorioRepository escritorioRepository,
                                  ClienteRepository clienteRepository, ClienteService clienteService,
                                  ArmazenamentoArquivos armazenamento, ProcessamentoDocumentoService processamento,
                                  JobProcessamentoRepository jobRepository, InconsistenciaRepository inconsistenciaRepository) {
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.acessoRecursoService = acessoRecursoService;
        this.usuarioRepository = usuarioRepository;
        this.escritorioRepository = escritorioRepository;
        this.clienteRepository = clienteRepository;
        this.clienteService = clienteService;
        this.armazenamento = armazenamento;
        this.processamento = processamento;
        this.jobRepository = jobRepository;
        this.inconsistenciaRepository = inconsistenciaRepository;
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

    @Transactional(readOnly = true)
    public DocumentoFiscalResponseDTO consultar(Long documentoId, UsuarioAutenticado ua) {
        return DocumentoFiscalResponseDTO.fromEntity(acessoRecursoService.documento(documentoId, ua));
    }

    /**
     * Guarda o arquivo e enfileira o processamento. clienteId vazio: upload no escritório, e o cliente vem do arquivo.
     * O tipo informado é só sugestão; o processamento detecta o tipo pelo conteúdo.
     */
    public DocumentoFiscalResponseDTO importar(Long clienteId, MultipartFile arquivo, DocumentoFiscalRequestDTO dto,
                                               UsuarioAutenticado ua) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new BusinessRuleException("Arquivo é obrigatório.");
        }
        Cliente cliente = clienteId == null ? null : acessoRecursoService.cliente(clienteId, ua);
        validarArquivo(arquivo);

        String extensao = extrairExtensao(arquivo.getOriginalFilename());
        String mimeType = arquivo.getContentType() != null ? arquivo.getContentType() : "application/octet-stream";
        String chave = "escritorios/" + ua.escritorioId() + "/" + UUID.randomUUID() + extensao;

        // ponytail: upload em uma requisição só; upload em partes (retomável) quando houver arquivos acima
        // de 500 MB ou conexões instáveis
        String sha256 = gravarCalculandoSha256(chave, arquivo, mimeType);
        documentoFiscalRepository.findFirstByEscritorioIdAndSha256(ua.escritorioId(), sha256).ifPresent(existente -> {
            armazenamento.excluir(chave);
            throw new DuplicateResourceException("Este arquivo já foi importado no escritório.", existente.getId());
        });

        DocumentoFiscal documento = new DocumentoFiscal();
        documento.setEscritorio(escritorioRepository.getReferenceById(ua.escritorioId()));
        documento.setCliente(cliente);
        documento.setUsuario(usuarioRepository.getReferenceById(ua.id()));
        DocumentoTipo informado = dto == null ? null : dto.getTipo();
        documento.setTipoInformado(informado);
        documento.setTipo(informado != null ? informado : DocumentoTipo.OUTRO); // até a detecção pelo conteúdo
        documento.setObservacao(dto == null ? null : dto.getObservacao());
        documento.setNomeOriginalArquivo(arquivo.getOriginalFilename());
        documento.setExtensao(extensao);
        documento.setMimeType(mimeType);
        documento.setTamanhoBytes(arquivo.getSize());
        documento.setChaveArmazenamento(chave);
        documento.setSha256(sha256);

        documentoFiscalRepository.save(documento);
        processamento.enfileirar(documento);
        return DocumentoFiscalResponseDTO.fromEntity(documento);
    }

    @Transactional(readOnly = true)
    public ArquivoDocumento abrirArquivo(Long documentoId, UsuarioAutenticado ua) {
        DocumentoFiscal documento = acessoRecursoService.documento(documentoId, ua);
        if (!armazenamento.existe(documento.getChaveArmazenamento())) {
            throw new ResourceNotFoundException("Arquivo original não encontrado no armazenamento.");
        }
        return new ArquivoDocumento(documento.getNomeOriginalArquivo(), documento.getMimeType(),
                documento.getTamanhoBytes(), armazenamento.abrir(documento.getChaveArmazenamento()));
    }

    @Transactional(readOnly = true)
    public StatusDocumentoDTO status(Long documentoId, UsuarioAutenticado ua) {
        DocumentoFiscal documento = acessoRecursoService.documento(documentoId, ua);
        return jobRepository.findFirstByDocumentoIdOrderByIdDesc(documento.getId())
                .map(StatusDocumentoDTO::de)
                .orElseThrow(() -> new ResourceNotFoundException("Documento sem processamento registrado."));
    }

    @Transactional(readOnly = true)
    public List<InconsistenciaDTO> inconsistencias(Long documentoId, UsuarioAutenticado ua) {
        DocumentoFiscal documento = acessoRecursoService.documento(documentoId, ua);
        return inconsistenciaRepository.findByDocumentoIdOrderByIdAsc(documento.getId()).stream()
                .map(InconsistenciaDTO::de)
                .toList();
    }

    /** Roda de novo a partir do arquivo no storage; o processador apaga o que a execução anterior gerou. */
    public DocumentoFiscalResponseDTO reprocessar(Long documentoId, UsuarioAutenticado ua) {
        DocumentoFiscal documento = acessoRecursoService.documento(documentoId, ua);
        exigirParado(documento);
        processamento.enfileirar(documento);
        return DocumentoFiscalResponseDTO.fromEntity(documento);
    }

    /**
     * Documento aguardando cliente: associa a um cliente existente (clienteId) ou cadastra o sugerido pelo arquivo
     * (CNPJ e nome lidos do 0000) e reprocessa. Nada é digitado.
     */
    public DocumentoFiscalResponseDTO confirmarCliente(Long documentoId, Long clienteId, UsuarioAutenticado ua) {
        DocumentoFiscal documento = acessoRecursoService.documento(documentoId, ua);
        if (documento.getStatus() != DocumentoStatus.AGUARDANDO_CLIENTE) {
            throw new BusinessRuleException("O documento não está aguardando cliente.");
        }
        Cliente cliente;
        if (clienteId != null) {
            cliente = acessoRecursoService.cliente(clienteId, ua);
        } else if (documento.getCnpjArquivo() != null) {
            cliente = clienteRepository.findByEscritorioIdAndCnpj(ua.escritorioId(), documento.getCnpjArquivo())
                    .orElseGet(() -> clienteRepository.getReferenceById(clienteService.create(sugestao(documento), ua).id()));
        } else {
            throw new BusinessRuleException("O arquivo não identifica a empresa: escolha um cliente.");
        }
        documento.setCliente(cliente);
        processamento.enfileirar(documento);
        return DocumentoFiscalResponseDTO.fromEntity(documento);
    }

    /** Exclui registro, dados derivados (cascata) e arquivo. Documento com débitos gerados não pode ser excluído (FK). */
    public void excluir(Long documentoId, UsuarioAutenticado ua) {
        DocumentoFiscal documento = acessoRecursoService.documento(documentoId, ua);
        exigirParado(documento);
        documentoFiscalRepository.delete(documento);
        documentoFiscalRepository.flush(); // falha de FK antes de apagar o arquivo
        armazenamento.excluir(documento.getChaveArmazenamento());
    }

    private void exigirParado(DocumentoFiscal documento) {
        if (jobRepository.existsByDocumentoIdAndStatusIn(documento.getId(), List.of(StatusJob.PENDENTE, StatusJob.PROCESSANDO))) {
            throw new BusinessRuleException("O documento ainda está em processamento. Aguarde terminar.");
        }
    }

    private static ClienteRequestDTO sugestao(DocumentoFiscal documento) {
        ClienteRequestDTO dto = new ClienteRequestDTO();
        String nome = documento.getNomeEmpresaArquivo() == null ? documento.getCnpjArquivo() : documento.getNomeEmpresaArquivo();
        dto.setRazaoSocial(nome.length() > 150 ? nome.substring(0, 150) : nome);
        dto.setCnpj(documento.getCnpjArquivo());
        return dto;
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

    private String extrairExtensao(String nomeArquivo) {
        if (nomeArquivo == null || nomeArquivo.isBlank()) {
            return "";
        }
        int indice = nomeArquivo.lastIndexOf('.');
        return indice >= 0 ? nomeArquivo.substring(indice).toLowerCase() : "";
    }
}
