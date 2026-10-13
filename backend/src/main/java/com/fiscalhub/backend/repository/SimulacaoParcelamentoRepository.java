package com.fiscalhub.backend.repository;

import com.fiscalhub.backend.entity.SimulacaoParcelamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SimulacaoParcelamentoRepository extends JpaRepository<SimulacaoParcelamento, Long> {
    List<SimulacaoParcelamento> findByClienteIdOrderByDataCriacaoDesc(Long clienteId);
    Optional<SimulacaoParcelamento> findByIdAndClienteEscritorioId(Long id, Long escritorioId);
}
