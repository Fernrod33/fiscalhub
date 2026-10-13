package com.fiscalhub.backend.repository;

import com.fiscalhub.backend.entity.DocumentoFiscal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentoFiscalRepository extends JpaRepository<DocumentoFiscal, Long> {
    List<DocumentoFiscal> findByEmpresaIdOrderByDataImportacaoDesc(Long empresaId);

    Optional<DocumentoFiscal> findByIdAndEmpresaIdAndEmpresaUsuarioEmail(Long id, Long empresaId, String email);
}
