package com.fiscalhub.backend.repository;

import com.fiscalhub.backend.entity.DocumentoFiscal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentoFiscalRepository extends JpaRepository<DocumentoFiscal, Long> {
    List<DocumentoFiscal> findByClienteIdOrderByDataImportacaoDesc(Long clienteId);

    Optional<DocumentoFiscal> findByClienteIdAndSha256(Long clienteId, String sha256);

    Optional<DocumentoFiscal> findByIdAndClienteIdAndClienteEscritorioId(Long id, Long clienteId, Long escritorioId);
}
