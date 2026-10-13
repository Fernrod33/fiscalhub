package com.fiscalhub.backend.repository;

import com.fiscalhub.backend.entity.Declaracao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DeclaracaoRepository extends JpaRepository<Declaracao, Long> {

    List<Declaracao> findByClienteId(Long clienteId);

    Optional<Declaracao> findByIdAndClienteEscritorioId(Long id, Long escritorioId);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END " +
           "FROM Declaracao d WHERE d.cliente.id = :clienteId " +
           "AND d.tipo = :tipo " +
           "AND d.periodoInicio = :periodoInicio " +
           "AND d.periodoFim = :periodoFim")
    boolean existsByClienteAndTipoAndPeriodo(
            @Param("clienteId") Long clienteId,
            @Param("tipo") String tipo,
            @Param("periodoInicio") LocalDate periodoInicio,
            @Param("periodoFim") LocalDate periodoFim);
}
