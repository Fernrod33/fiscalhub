package com.fiscalhub.backend.service;

import com.fiscalhub.backend.entity.Declaracao;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.SimulacaoParcelamento;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.repository.DeclaracaoRepository;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.fiscalhub.backend.repository.ClienteRepository;
import com.fiscalhub.backend.repository.SimulacaoParcelamentoRepository;
import com.fiscalhub.backend.repository.UsuarioRepository;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import org.springframework.stereotype.Service;

/**
 * Ponto único de autorização por escritório: carrega o recurso já filtrado pelo escritório do usuário autenticado.
 * Recursos filhos (documentos, declarações, simulações) herdam o escopo pelo cliente.
 * Recurso inexistente ou de outro escritório gera 404 (nunca 403), para não revelar que ele existe.
 * O papel (o que cada um pode fazer) fica no @PreAuthorize dos controllers.
 */
@Service
public class AcessoRecursoService {

    private final ClienteRepository clienteRepository;
    private final DeclaracaoRepository declaracaoRepository;
    private final SimulacaoParcelamentoRepository simulacaoRepository;
    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final UsuarioRepository usuarioRepository;

    public AcessoRecursoService(ClienteRepository clienteRepository,
                                DeclaracaoRepository declaracaoRepository,
                                SimulacaoParcelamentoRepository simulacaoRepository,
                                DocumentoFiscalRepository documentoFiscalRepository,
                                UsuarioRepository usuarioRepository) {
        this.clienteRepository = clienteRepository;
        this.declaracaoRepository = declaracaoRepository;
        this.simulacaoRepository = simulacaoRepository;
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public Cliente cliente(Long id, UsuarioAutenticado ua) {
        return clienteRepository.findByIdAndEscritorioId(id, ua.escritorioId())
                .orElseThrow(() -> naoEncontrado("Cliente", id));
    }

    public Declaracao declaracao(Long id, UsuarioAutenticado ua) {
        return declaracaoRepository.findByIdAndClienteEscritorioId(id, ua.escritorioId())
                .orElseThrow(() -> naoEncontrado("Declaração", id));
    }

    public SimulacaoParcelamento simulacao(Long id, UsuarioAutenticado ua) {
        return simulacaoRepository.findByIdAndDeclaracaoClienteEscritorioId(id, ua.escritorioId())
                .orElseThrow(() -> naoEncontrado("Simulação", id));
    }

    public DocumentoFiscal documento(Long clienteId, Long documentoId, UsuarioAutenticado ua) {
        return documentoFiscalRepository.findByIdAndClienteIdAndClienteEscritorioId(documentoId, clienteId, ua.escritorioId())
                .orElseThrow(() -> naoEncontrado("Documento", documentoId));
    }

    /** Usuário do mesmo escritório. */
    public Usuario usuario(Long id, UsuarioAutenticado ua) {
        return usuarioRepository.findByIdAndEscritorioId(id, ua.escritorioId())
                .orElseThrow(() -> naoEncontrado("Usuário", id));
    }

    private static ResourceNotFoundException naoEncontrado(String recurso, Long id) {
        return new ResourceNotFoundException(recurso + " não encontrado(a) com a ID: " + id);
    }
}
