package com.fiscalhub.backend.repository;

import com.fiscalhub.backend.entity.Declaracao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DeclaracaoRepository extends JpaRepository<Declaracao, Long> {

    List<Declaracao> findByEmpresaId(Long empresaId);

    Optional<Declaracao> findByIdAndUsuarioEmail(Long id, String email);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END " +
           "FROM Declaracao d WHERE d.empresa.id = :empresaId " +
           "AND d.tipo = :tipo " +
           "AND d.periodoInicio = :periodoInicio " +
           "AND d.periodoFim = :periodoFim")
    boolean existsByEmpresaAndTipoAndPeriodo(
            @Param("empresaId") Long empresaId,
            @Param("tipo") String tipo,
            @Param("periodoInicio") LocalDate periodoInicio,
            @Param("periodoFim") LocalDate periodoFim);
}
