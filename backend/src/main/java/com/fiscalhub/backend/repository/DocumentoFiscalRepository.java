package com.fiscalhub.backend.repository;

import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DocumentoFiscalRepository extends JpaRepository<DocumentoFiscal, Long> {
    List<DocumentoFiscal> findByClienteIdOrderByDataImportacaoDesc(Long clienteId);

    Optional<DocumentoFiscal> findFirstByEscritorioIdAndSha256(Long escritorioId, String sha256);

    Optional<DocumentoFiscal> findByIdAndEscritorioId(Long id, Long escritorioId);

    /** Documentos do escritório com filtros opcionais (null = sem filtro). */
    @Query(value = """
            select d from DocumentoFiscal d left join fetch d.cliente c
            where d.escritorio.id = :escritorioId
              and (:clienteId is null or c.id = :clienteId)
              and (:tipo is null or d.tipo = :tipo)
              and (:status is null or d.status = :status)""",
            countQuery = """
            select count(d) from DocumentoFiscal d
            where d.escritorio.id = :escritorioId
              and (:clienteId is null or d.cliente.id = :clienteId)
              and (:tipo is null or d.tipo = :tipo)
              and (:status is null or d.status = :status)""")
    Page<DocumentoFiscal> buscarDoEscritorio(Long escritorioId, Long clienteId, DocumentoTipo tipo, DocumentoStatus status,
                                             Pageable pageable);

    interface ContagemPorStatus {
        DocumentoStatus getStatus();

        long getTotal();
    }

    @Query("select d.status as status, count(d) as total from DocumentoFiscal d"
            + " where d.escritorio.id = :escritorioId group by d.status")
    List<ContagemPorStatus> contarPorStatus(Long escritorioId);
}
