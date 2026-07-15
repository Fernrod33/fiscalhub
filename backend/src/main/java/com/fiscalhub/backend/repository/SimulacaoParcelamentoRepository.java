package com.fiscalhub.backend.repository;

import com.fiscalhub.backend.entity.SimulacaoParcelamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SimulacaoParcelamentoRepository extends JpaRepository<SimulacaoParcelamento, Long> {
    List<SimulacaoParcelamento> findByDeclaracaoId(Long declaracaoId);
    List<SimulacaoParcelamento> findByAprovada(Boolean aprovada);
}
