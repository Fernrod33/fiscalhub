package com.fiscalhub.backend.repository;

import com.fiscalhub.backend.entity.ParcelaSimulacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParcelaSimulacaoRepository extends JpaRepository<ParcelaSimulacao, Long> {

    List<ParcelaSimulacao> findBySimulacaoIdOrderByNumeroParcela(Long simulacaoId);
}
