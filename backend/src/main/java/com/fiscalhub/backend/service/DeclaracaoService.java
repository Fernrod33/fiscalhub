package com.fiscalhub.backend.service;

import com.fiscalhub.backend.entity.Declaracao;
import com.fiscalhub.backend.entity.Empresa;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.StatusDeclaracao;
import com.fiscalhub.backend.exception.DuplicateResourceException;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.repository.DeclaracaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DeclaracaoService {

    private final DeclaracaoRepository declaracaoRepository;
    private final UsuarioService usuarioService;
    private final EmpresaService empresaService;

    public DeclaracaoService(DeclaracaoRepository declaracaoRepository,
                             UsuarioService usuarioService,
                             EmpresaService empresaService) {
        this.declaracaoRepository = declaracaoRepository;
        this.usuarioService = usuarioService;
        this.empresaService = empresaService;
    }

    public List<Declaracao> findAll() {
        return declaracaoRepository.findAll();
    }

    public Declaracao findById(Long id) {
        return declaracaoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Declaração não encontrada com id: " + id));
    }

    public List<Declaracao> findByUsuarioId(Long usuarioId) {
        return declaracaoRepository.findByUsuarioId(usuarioId);
    }

    public List<Declaracao> findByEmpresaId(Long empresaId) {
        return declaracaoRepository.findByEmpresaId(empresaId);
    }

    public List<Declaracao> findByStatus(StatusDeclaracao status) {
        return declaracaoRepository.findByStatus(status);
    }

    public Declaracao create(Declaracao declaracao, Long usuarioId, Long empresaId) {
        Usuario usuario = usuarioService.findById(usuarioId);
        Empresa empresa = empresaService.findById(empresaId);

        declaracao.setUsuario(usuario);
        declaracao.setEmpresa(empresa);

        boolean exists = declaracaoRepository.existsByEmpresaAndTipoAndPeriodo(
                empresaId,
                declaracao.getTipo(),
                declaracao.getPeriodoInicio(),
                declaracao.getPeriodoFim()
        );

        if (exists) {
            throw new DuplicateResourceException("Já existe uma declaração deste tipo para o mesmo período e empresa.");
        }

        return declaracaoRepository.save(declaracao);
    }

    public Declaracao update(Long id, Declaracao declaracaoDetails) {
        Declaracao declaracao = findById(id);

        declaracao.setTipo(declaracaoDetails.getTipo());
        declaracao.setTributo(declaracaoDetails.getTributo());
        declaracao.setPeriodoInicio(declaracaoDetails.getPeriodoInicio());
        declaracao.setPeriodoFim(declaracaoDetails.getPeriodoFim());
        declaracao.setValorTotal(declaracaoDetails.getValorTotal());
        declaracao.setValorImposto(declaracaoDetails.getValorImposto());
        declaracao.setStatus(declaracaoDetails.getStatus());
        declaracao.setDataVencimento(declaracaoDetails.getDataVencimento());
        declaracao.setDataPagamento(declaracaoDetails.getDataPagamento());
        declaracao.setObservacoes(declaracaoDetails.getObservacoes());

        return declaracaoRepository.save(declaracao);
    }

    public void delete(Long id) {
        Declaracao declaracao = findById(id);
        declaracaoRepository.delete(declaracao);
    }
}
