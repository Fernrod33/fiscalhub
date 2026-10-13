package com.fiscalhub.backend.repository;

import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    Boolean existsByEmail(String email);
    List<Usuario> findByEscritorioIdOrderByNome(Long escritorioId);
    Optional<Usuario> findByIdAndEscritorioId(Long id, Long escritorioId);
    long countByEscritorioIdAndPapelAndAtivoTrue(Long escritorioId, Papel papel);
    /** Usado a cada requisição: token de usuário desativado ou com papel alterado deixa de valer. */
    boolean existsByIdAndAtivoTrueAndPapel(Long id, Papel papel);
}
