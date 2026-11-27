package com.fiscalhub.backend.repository;

import com.fiscalhub.backend.entity.Debito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DebitoRepository extends JpaRepository<Debito, Long> {
    List<Debito> findByClienteIdOrderByDataVencimento(Long clienteId);
}
