package com.fiscalhub.backend.repository;

import com.fiscalhub.backend.entity.Declaracao;
import com.fiscalhub.backend.entity.enums.StatusDeclaracao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface DeclaracaoRepository extends JpaRepository<Declaracao, Long> {

    List<Declaracao> findByUsuarioId(Long usuarioId);

    List<Declaracao> findByEmpresaId(Long empresaId);

    List<Declaracao> findByStatus(StatusDeclaracao status);

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
