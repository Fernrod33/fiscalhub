package com.fiscalhub.backend.repository;

import com.fiscalhub.backend.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    List<Cliente> findByEscritorioIdOrderByRazaoSocial(Long escritorioId);
    Optional<Cliente> findByIdAndEscritorioId(Long id, Long escritorioId);
    Optional<Cliente> findByEscritorioIdAndCnpj(Long escritorioId, String cnpj);
    List<Cliente> findByEscritorioIdAndCnpjStartingWith(Long escritorioId, String raiz);
    boolean existsByCnpjAndEscritorioId(String cnpj, Long escritorioId);
}
