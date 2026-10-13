package com.fiscalhub.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.dto.ParcelaDTO;
import com.fiscalhub.backend.dto.SimulacaoRequestDTO;
import com.fiscalhub.backend.dto.SimulacaoResultDTO;
import com.fiscalhub.backend.entity.Declaracao;
import com.fiscalhub.backend.entity.ParcelaSimulacao;
import com.fiscalhub.backend.entity.SimulacaoParcelamento;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.repository.SimulacaoParcelamentoRepository;
import com.fiscalhub.backend.service.calculo.CalculoParcelamentoStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    public SimulacaoParcelamento findById(Long id, String username) {
        return acessoRecursoService.simulacao(id, username);
    }

    public List<SimulacaoParcelamento> findByDeclaracaoId(Long declaracaoId, String username) {
        acessoRecursoService.declaracao(declaracaoId, username);
        return simulacaoParcelamentoRepository.findByDeclaracaoId(declaracaoId);
    }

    // ---------------------------------------------------------------
    // Cálculo e persistência
    // ---------------------------------------------------------------

    /**
     * Executa o cálculo de parcelamento sem persistir — apenas retorna o resultado.
     */
    @Transactional(readOnly = true)
    public SimulacaoResultDTO calcular(SimulacaoRequestDTO request, String username) {
        Declaracao declaracao = acessoRecursoService.declaracao(request.getDeclaracaoId(), username);
        CalculoParcelamentoStrategy strategy = resolverStrategy(request);
        return strategy.calcular(declaracao, request);
    }

    /**
     * Executa o cálculo e persiste a simulação (incluindo parcelas) no banco.
     */
    public SimulacaoResultDTO calcularESalvar(SimulacaoRequestDTO request, String username) {
        Declaracao declaracao = acessoRecursoService.declaracao(request.getDeclaracaoId(), username);
        CalculoParcelamentoStrategy strategy = resolverStrategy(request);
        SimulacaoResultDTO result = strategy.calcular(declaracao, request);

        SimulacaoParcelamento entidade = mapResultToEntity(result, declaracao);
        simulacaoParcelamentoRepository.save(entidade);

        result.setSimulacaoId(entidade.getId());
        return result;
    }

    /**
     * Aprova uma simulação salva, alterando sua flag aprovada para true.
     */
    public SimulacaoParcelamento aprovar(Long id, String username) {
        SimulacaoParcelamento simulacao = acessoRecursoService.simulacao(id, username);
        simulacao.setAprovada(true);
        return simulacaoParcelamentoRepository.save(simulacao);
    }

    public void delete(Long id, String username) {
        simulacaoParcelamentoRepository.delete(acessoRecursoService.simulacao(id, username));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private CalculoParcelamentoStrategy resolverStrategy(SimulacaoRequestDTO request) {
        return strategies.stream()
                .filter(s -> s.suporta(request.getModalidade()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "Modalidade de parcelamento não suportada: " + request.getModalidade()));
    }

    private SimulacaoParcelamento mapResultToEntity(SimulacaoResultDTO result, Declaracao declaracao) {
        SimulacaoParcelamento entity = new SimulacaoParcelamento();
        entity.setDeclaracao(declaracao);
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
        // Campos legados para compatibilidade
        entity.setTaxaJuros(result.getPercentualJurosMora());
        entity.setValorTotal(result.getValorAtualizado());
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
