package com.fiscalhub.backend.service;

import com.fiscalhub.backend.entity.Declaracao;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.exception.DuplicateResourceException;
import com.fiscalhub.backend.repository.DeclaracaoRepository;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DeclaracaoService {

    private final DeclaracaoRepository declaracaoRepository;
    private final AcessoRecursoService acessoRecursoService;
    private final UsuarioRepository usuarioRepository;

    public DeclaracaoService(DeclaracaoRepository declaracaoRepository,
                             AcessoRecursoService acessoRecursoService,
                             UsuarioRepository usuarioRepository) {
        this.declaracaoRepository = declaracaoRepository;
        this.acessoRecursoService = acessoRecursoService;
        this.usuarioRepository = usuarioRepository;
    }

    public Declaracao findById(Long id, UsuarioAutenticado ua) {
        return acessoRecursoService.declaracao(id, ua);
    }

    public List<Declaracao> findByClienteId(Long clienteId, UsuarioAutenticado ua) {
        acessoRecursoService.cliente(clienteId, ua);
        return declaracaoRepository.findByClienteId(clienteId);
    }

    public Declaracao create(Declaracao declaracao, Long clienteId, UsuarioAutenticado ua) {
        Cliente cliente = acessoRecursoService.cliente(clienteId, ua);

        declaracao.setId(null); // id vindo do corpo faria o save() virar merge sobre registro alheio
        declaracao.setUsuario(usuarioRepository.getReferenceById(ua.id()));
        declaracao.setCliente(cliente);

        boolean exists = declaracaoRepository.existsByClienteAndTipoAndPeriodo(
                clienteId,
                declaracao.getTipo(),
                declaracao.getPeriodoInicio(),
                declaracao.getPeriodoFim()
        );

        if (exists) {
            throw new DuplicateResourceException("Já existe uma declaração deste tipo para o mesmo período e cliente.");
        }

        return declaracaoRepository.save(declaracao);
    }

    public Declaracao update(Long id, Declaracao declaracaoDetails, UsuarioAutenticado ua) {
        Declaracao declaracao = acessoRecursoService.declaracao(id, ua);

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

    public void delete(Long id, UsuarioAutenticado ua) {
        declaracaoRepository.delete(acessoRecursoService.declaracao(id, ua));
    }
}
