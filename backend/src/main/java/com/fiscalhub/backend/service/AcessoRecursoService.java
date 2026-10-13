package com.fiscalhub.backend.service;

import com.fiscalhub.backend.entity.Debito;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.SimulacaoParcelamento;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.repository.DebitoRepository;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.fiscalhub.backend.repository.ClienteRepository;
import com.fiscalhub.backend.repository.SimulacaoParcelamentoRepository;
import com.fiscalhub.backend.repository.UsuarioRepository;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Ponto único de autorização por escritório: carrega o recurso já filtrado pelo escritório do usuário autenticado.
 * Recursos filhos (documentos, débitos, simulações) herdam o escopo pelo cliente.
 * Recurso inexistente ou de outro escritório gera 404 (nunca 403), para não revelar que ele existe.
 * O papel (o que cada um pode fazer) fica no @PreAuthorize dos controllers.
 */
@Service
public class AcessoRecursoService {

    private final ClienteRepository clienteRepository;
    private final DebitoRepository debitoRepository;
    private final SimulacaoParcelamentoRepository simulacaoRepository;
    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final UsuarioRepository usuarioRepository;

    public AcessoRecursoService(ClienteRepository clienteRepository,
                                DebitoRepository debitoRepository,
                                SimulacaoParcelamentoRepository simulacaoRepository,
                                DocumentoFiscalRepository documentoFiscalRepository,
                                UsuarioRepository usuarioRepository) {
        this.clienteRepository = clienteRepository;
        this.debitoRepository = debitoRepository;
        this.simulacaoRepository = simulacaoRepository;
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public Cliente cliente(Long id, UsuarioAutenticado ua) {
        return clienteRepository.findByIdAndEscritorioId(id, ua.escritorioId())
                .orElseThrow(() -> naoEncontrado("Cliente", id));
    }

    /** Todos os débitos pedidos, do cliente indicado e do escritório; se algum faltar, 404. */
    public List<Debito> debitos(Long clienteId, Collection<Long> ids, UsuarioAutenticado ua) {
        Set<Long> pedidos = Set.copyOf(ids);
        List<Debito> debitos = debitoRepository.findByIdInAndClienteIdAndClienteEscritorioId(pedidos, clienteId, ua.escritorioId());
        if (debitos.size() != pedidos.size()) {
            throw new ResourceNotFoundException("Débito não encontrado para o cliente informado.");
        }
        return debitos;
    }

    public SimulacaoParcelamento simulacao(Long id, UsuarioAutenticado ua) {
        return simulacaoRepository.findByIdAndClienteEscritorioId(id, ua.escritorioId())
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
