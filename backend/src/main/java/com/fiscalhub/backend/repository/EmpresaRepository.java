package com.fiscalhub.backend.repository;

import com.fiscalhub.backend.entity.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmpresaRepository extends JpaRepository<Empresa, Long> {
    Optional<Empresa> findByCnpj(String cnpj);
    List<Empresa> findByUsuarioId(Long usuarioId);
    Boolean existsByCnpj(String cnpj);
}
