package com.fiscalhub.backend.auditoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaEventoRepository extends JpaRepository<AuditoriaEvento, Long> {
    Page<AuditoriaEvento> findByEscritorioIdOrderByDataHoraDescIdDesc(Long escritorioId, Pageable pageable);
}
