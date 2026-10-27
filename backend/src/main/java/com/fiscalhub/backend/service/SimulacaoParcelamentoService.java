package com.fiscalhub.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.dto.ParcelaDTO;
import com.fiscalhub.backend.dto.SimulacaoRequestDTO;
import com.fiscalhub.backend.dto.SimulacaoResultDTO;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.ParcelaSimulacao;
import com.fiscalhub.backend.entity.SimulacaoParcelamento;
import com.fiscalhub.backend.entity.Debito;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.repository.SimulacaoParcelamentoRepository;
import com.fiscalhub.backend.service.calculo.CalculoParcelamentoStrategy;
import com.fiscalhub.backend.service.calculo.EntradaCalculo;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class SimulacaoParcelamentoService {

    private final SimulacaoParcelamentoRepository simulacaoParcelamentoRepository;
    private final AcessoRecursoService acessoRecursoService;
    private final List<CalculoParcelamentoStrategy> strategies;
    private final ObjectMapper objectMapper;

    public SimulacaoParcelamentoService(
            SimulacaoParcelamentoRepository simulacaoParcelamentoRepository,
            AcessoRecursoService acessoRecursoService,
            List<CalculoParcelamentoStrategy> strategies,
            ObjectMapper objectMapper) {
        this.simulacaoParcelamentoRepository = simulacaoParcelamentoRepository;
        this.acessoRecursoService = acessoRecursoService;
        this.strategies = strategies;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------

    public SimulacaoParcelamento findById(Long id, UsuarioAutenticado ua) {
        return acessoRecursoService.simulacao(id, ua);
    }

    public List<SimulacaoParcelamento> findByClienteId(Long clienteId, UsuarioAutenticado ua) {
        acessoRecursoService.cliente(clienteId, ua);
        return simulacaoParcelamentoRepository.findByClienteIdOrderByDataCriacaoDesc(clienteId);
    }

    // ---------------------------------------------------------------
    // Cálculo e persistência
    // ---------------------------------------------------------------

    /**
     * Executa o cálculo de parcelamento sem persistir — apenas retorna o resultado.
     */
    @Transactional(readOnly = true)
    public SimulacaoResultDTO calcular(SimulacaoRequestDTO request, UsuarioAutenticado ua) {
        return resolverStrategy(request).calcular(entrada(request, ua));
    }

    /**
     * Executa o cálculo e persiste a simulação (incluindo parcelas) no banco.
     */
    public SimulacaoResultDTO calcularESalvar(SimulacaoRequestDTO request, UsuarioAutenticado ua) {
        EntradaCalculo entrada = entrada(request, ua);
        SimulacaoResultDTO result = resolverStrategy(request).calcular(entrada);

        SimulacaoParcelamento entidade = mapResultToEntity(result, entrada);
        simulacaoParcelamentoRepository.save(entidade);

        result.setSimulacaoId(entidade.getId());
        return result;
    }

    /**
     * Aprova uma simulação salva, alterando sua flag aprovada para true.
     */
    public SimulacaoParcelamento aprovar(Long id, UsuarioAutenticado ua) {
        SimulacaoParcelamento simulacao = acessoRecursoService.simulacao(id, ua);
        simulacao.setAprovada(true);
        return simulacaoParcelamentoRepository.save(simulacao);
    }

    public void delete(Long id, UsuarioAutenticado ua) {
        simulacaoParcelamentoRepository.delete(acessoRecursoService.simulacao(id, ua));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private EntradaCalculo entrada(SimulacaoRequestDTO request, UsuarioAutenticado ua) {
        Cliente cliente = acessoRecursoService.cliente(request.getClienteId(), ua);
        List<Debito> debitos = acessoRecursoService.debitos(cliente.getId(), request.getDebitoIds(), ua);
        debitos.stream().filter(d -> d.getDataVencimento() == null || d.getValorPrincipal() == null).findFirst().ifPresent(d -> {
            throw new BusinessRuleException("O débito " + (d.getNumeroInscricao() != null ? d.getNumeroInscricao() : d.getId())
                    + " não traz vencimento ou principal no documento (inscrição em dívida ativa). A simulação desses débitos "
                    + "(transação e parcelamento da PGFN) entra na Fase 5.");
        });
        return new EntradaCalculo(cliente, debitos, request);
    }

    private CalculoParcelamentoStrategy resolverStrategy(SimulacaoRequestDTO request) {
        return strategies.stream()
                .filter(s -> s.suporta(request.getModalidade()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "Modalidade de parcelamento não suportada: " + request.getModalidade()));
    }

    private SimulacaoParcelamento mapResultToEntity(SimulacaoResultDTO result, EntradaCalculo entrada) {
        SimulacaoParcelamento entity = new SimulacaoParcelamento();
        entity.setCliente(entrada.cliente());
        entity.setDebitos(new ArrayList<>(entrada.debitos()));
        entity.setOrgao(result.getOrgao());
        entity.setModalidade(result.getModalidade());
        entity.setValorOriginal(result.getValorOriginal());
        entity.setDataVencimentoOriginal(result.getDataVencimentoOriginal());
        entity.setDataCalculo(result.getDataCalculo() != null ? result.getDataCalculo() : LocalDate.now());
        entity.setDiasEmAtraso(result.getDiasEmAtraso());
        entity.setPercentualMultaMora(result.getPercentualMultaMora());
        entity.setValorMultaMora(result.getValorMultaMora());
        entity.setPercentualMultaOficio(result.getPercentualMultaOficio());
        entity.setValorMultaOficio(result.getValorMultaOficio());
        entity.setPercentualJurosMora(result.getPercentualJurosMora());
        entity.setValorJuros(result.getValorJurosMora());
        entity.setIndiceUtilizado(result.getIndiceUtilizado());
        entity.setValorAtualizado(result.getValorAtualizado());
        entity.setValorEntrada(result.getValorEntrada());
        entity.setNumeroParcelas(result.getNumeroParcelas());
        entity.setValorParcela(result.getValorParcela());
        entity.setCustoTotal(result.getCustoTotal());
        entity.setEconomiaObtida(result.getEconomiaObtida());
        entity.setAprovada(false);

        // Serializa memória de cálculo como JSON
        try {
            entity.setMemoriaCalculoJson(objectMapper.writeValueAsString(result.getMemoriaCalculo()));
        } catch (JsonProcessingException e) {
            entity.setMemoriaCalculoJson("[]");
        }

        // Monta parcelas filhas
        if (result.getTabelaParcelas() != null) {
            for (ParcelaDTO dto : result.getTabelaParcelas()) {
                ParcelaSimulacao parcela = new ParcelaSimulacao();
                parcela.setSimulacao(entity);
                parcela.setNumeroParcela(dto.getNumeroParcela());
                parcela.setDataVencimento(dto.getDataVencimento());
                parcela.setValorParcela(dto.getValorParcela());
                parcela.setSaldoRestante(dto.getSaldoRestante());
                entity.getParcelas().add(parcela);
            }
        }

        return entity;
    }
}
