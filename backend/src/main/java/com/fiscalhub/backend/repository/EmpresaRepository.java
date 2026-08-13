package com.fiscalhub.backend.repository;

import com.fiscalhub.backend.entity.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {
    Optional<Empresa> findByCnpj(String cnpj);
    List<Empresa> findByUsuarioId(Long usuarioId);
    Boolean existsByCnpjAndUsuarioId(String cnpj, Long usuarioId);
}
