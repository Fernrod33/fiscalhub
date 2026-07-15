package com.fiscalhub.backend.repository;

import com.fiscalhub.backend.entity.ParcelaSimulacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParcelaSimulacaoRepository extends JpaRepository<ParcelaSimulacao, Long> {

    List<ParcelaSimulacao> findBySimulacaoIdOrderByNumeroParcela(Long simulacaoId);
}
