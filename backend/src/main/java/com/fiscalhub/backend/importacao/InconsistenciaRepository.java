package com.fiscalhub.backend.importacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface InconsistenciaRepository extends JpaRepository<Inconsistencia, Long> {

    List<Inconsistencia> findByDocumentoIdOrderByIdAsc(Long documentoId);

    @Modifying
    @Query("delete from Inconsistencia i where i.documento.id = :documentoId")
    void apagarDoDocumento(Long documentoId);
}
