package com.fiscalhub.backend.repository;

import com.fiscalhub.backend.entity.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {
    List<Empresa> findByUsuarioId(Long usuarioId);
    Optional<Empresa> findByIdAndUsuarioEmail(Long id, String email);
    Boolean existsByCnpjAndUsuarioId(String cnpj, Long usuarioId);
}
