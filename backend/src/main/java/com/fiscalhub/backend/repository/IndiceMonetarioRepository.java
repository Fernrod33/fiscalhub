package com.fiscalhub.backend.repository;

import com.fiscalhub.backend.entity.IndiceMonetario;
import com.fiscalhub.backend.entity.enums.TipoIndice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IndiceMonetarioRepository extends JpaRepository<IndiceMonetario, Long> {

    Optional<IndiceMonetario> findByTipoAndAnoAndMes(TipoIndice tipo, Integer ano, Integer mes);

    @Query("SELECT i FROM IndiceMonetario i WHERE i.tipo = :tipo " +
           "AND (i.ano > :anoInicio OR (i.ano = :anoInicio AND i.mes >= :mesInicio)) " +
           "AND (i.ano < :anoFim OR (i.ano = :anoFim AND i.mes <= :mesFim)) " +
           "ORDER BY i.ano ASC, i.mes ASC")
    List<IndiceMonetario> findByTipoAndPeriodo(
            @Param("tipo") TipoIndice tipo,
            @Param("anoInicio") Integer anoInicio,
            @Param("mesInicio") Integer mesInicio,
            @Param("anoFim") Integer anoFim,
            @Param("mesFim") Integer mesFim);

    boolean existsByTipoAndAnoAndMes(TipoIndice tipo, Integer ano, Integer mes);
}
