package com.fiscalhub.backend.service;

import com.fiscalhub.backend.dto.DebitoImportado;
import com.fiscalhub.backend.dto.DebitoResponseDTO;
import com.fiscalhub.backend.entity.Debito;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.repository.DebitoRepository;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Débitos só nascem de importação: não há criação manual. */
@Service
@Transactional
public class DebitoService {

    private final DebitoRepository debitoRepository;
    private final AcessoRecursoService acessoRecursoService;

    public DebitoService(DebitoRepository debitoRepository, AcessoRecursoService acessoRecursoService) {
        this.debitoRepository = debitoRepository;
        this.acessoRecursoService = acessoRecursoService;
    }

    @Transactional(readOnly = true)
    public List<DebitoResponseDTO> listarPorCliente(Long clienteId, UsuarioAutenticado ua) {
        acessoRecursoService.cliente(clienteId, ua);
        return debitoRepository.findByClienteIdOrderByDataVencimento(clienteId).stream()
                .map(DebitoResponseDTO::fromEntity)
                .toList();
    }

    /** Uso interno dos parsers: o cliente vem do documento de origem, nunca de parâmetro. */
    public List<Debito> registrarDebitosImportados(DocumentoFiscal origem, List<DebitoImportado> itens) {
        return debitoRepository.saveAll(itens.stream().map(i -> {
            Debito d = new Debito();
            d.setCliente(origem.getCliente());
            d.setDocumentoOrigem(origem);
            d.setReferenciaOrigem(i.referenciaOrigem());
            d.setOrgaoCredor(i.orgaoCredor());
            d.setTributo(i.tributo());
            d.setCompetencia(i.competencia());
            d.setDataVencimento(i.dataVencimento());
            d.setValorPrincipal(i.valorPrincipal());
            d.setValorMulta(i.valorMulta());
            d.setValorJuros(i.valorJuros());
            d.setSituacao(i.situacao());
            d.setNumeroInscricao(i.numeroInscricao());
            return d;
        }).toList());
    }
}
