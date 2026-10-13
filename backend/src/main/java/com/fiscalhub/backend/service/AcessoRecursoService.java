package com.fiscalhub.backend.service;

import com.fiscalhub.backend.entity.Declaracao;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.Empresa;
import com.fiscalhub.backend.entity.SimulacaoParcelamento;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.repository.DeclaracaoRepository;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.fiscalhub.backend.repository.EmpresaRepository;
import com.fiscalhub.backend.repository.SimulacaoParcelamentoRepository;
import org.springframework.stereotype.Service;

/**
 * Ponto único de autorização por dono: carrega o recurso já filtrado pelo e-mail do usuário autenticado.
 * Recurso inexistente ou de outro usuário gera 404 (nunca 403), para não revelar que ele existe.
 */
@Service
public class AcessoRecursoService {

    private final EmpresaRepository empresaRepository;
    private final DeclaracaoRepository declaracaoRepository;
    private final SimulacaoParcelamentoRepository simulacaoRepository;
    private final DocumentoFiscalRepository documentoFiscalRepository;

    public AcessoRecursoService(EmpresaRepository empresaRepository,
                                DeclaracaoRepository declaracaoRepository,
                                SimulacaoParcelamentoRepository simulacaoRepository,
                                DocumentoFiscalRepository documentoFiscalRepository) {
        this.empresaRepository = empresaRepository;
        this.declaracaoRepository = declaracaoRepository;
        this.simulacaoRepository = simulacaoRepository;
        this.documentoFiscalRepository = documentoFiscalRepository;
    }

    public Empresa empresa(Long id, String email) {
        return empresaRepository.findByIdAndUsuarioEmail(id, email)
                .orElseThrow(() -> naoEncontrado("Empresa", id));
    }

    public Declaracao declaracao(Long id, String email) {
        return declaracaoRepository.findByIdAndUsuarioEmail(id, email)
                .orElseThrow(() -> naoEncontrado("Declaração", id));
    }

    public SimulacaoParcelamento simulacao(Long id, String email) {
        return simulacaoRepository.findByIdAndDeclaracaoUsuarioEmail(id, email)
                .orElseThrow(() -> naoEncontrado("Simulação", id));
    }

    public DocumentoFiscal documento(Long empresaId, Long documentoId, String email) {
        return documentoFiscalRepository.findByIdAndEmpresaIdAndEmpresaUsuarioEmail(documentoId, empresaId, email)
                .orElseThrow(() -> naoEncontrado("Documento", documentoId));
    }

    private static ResourceNotFoundException naoEncontrado(String recurso, Long id) {
        return new ResourceNotFoundException(recurso + " não encontrado(a) com a ID: " + id);
    }
}
