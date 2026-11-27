package com.fiscalhub.backend.repository;

import com.fiscalhub.backend.entity.JobProcessamento;
import com.fiscalhub.backend.entity.enums.StatusJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobProcessamentoRepository extends JpaRepository<JobProcessamento, Long> {

    Optional<JobProcessamento> findFirstByDocumentoIdOrderByIdDesc(Long documentoId);

    List<JobProcessamento> findByStatus(StatusJob status);

    boolean existsByDocumentoIdAndStatusIn(Long documentoId, Collection<StatusJob> status);

    /** Commit próprio: os jobs só são despachados depois que a volta para PENDENTE estiver gravada. */
    @Transactional
    @Modifying
    @Query("UPDATE JobProcessamento j SET j.status = 'PENDENTE' WHERE j.status = 'PROCESSANDO'")
    int devolverPresosParaFila();
}
