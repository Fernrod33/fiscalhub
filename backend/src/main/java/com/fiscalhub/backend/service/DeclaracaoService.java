package com.fiscalhub.backend.service;

import com.fiscalhub.backend.entity.Declaracao;
import com.fiscalhub.backend.entity.Empresa;
import com.fiscalhub.backend.exception.DuplicateResourceException;
import com.fiscalhub.backend.repository.DeclaracaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DeclaracaoService {

    private final DeclaracaoRepository declaracaoRepository;
    private final AcessoRecursoService acessoRecursoService;

    public DeclaracaoService(DeclaracaoRepository declaracaoRepository,
                             AcessoRecursoService acessoRecursoService) {
        this.declaracaoRepository = declaracaoRepository;
        this.acessoRecursoService = acessoRecursoService;
    }

    public Declaracao findById(Long id, String username) {
        return acessoRecursoService.declaracao(id, username);
    }

    public List<Declaracao> findByEmpresaId(Long empresaId, String username) {
        acessoRecursoService.empresa(empresaId, username);
        return declaracaoRepository.findByEmpresaId(empresaId);
    }

    public Declaracao create(Declaracao declaracao, Long empresaId, String username) {
        Empresa empresa = acessoRecursoService.empresa(empresaId, username);

        declaracao.setId(null); // id vindo do corpo faria o save() virar merge sobre registro alheio
        declaracao.setUsuario(empresa.getUsuario());
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

    public Declaracao update(Long id, Declaracao declaracaoDetails, String username) {
        Declaracao declaracao = acessoRecursoService.declaracao(id, username);

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

    public void delete(Long id, String username) {
        declaracaoRepository.delete(acessoRecursoService.declaracao(id, username));
    }
}
