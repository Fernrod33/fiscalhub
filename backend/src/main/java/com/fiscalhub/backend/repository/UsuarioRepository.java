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
    /** Estado conferido a cada requisição pelo filtro JWT (uma leitura por chave primária). */
    interface EstadoToken {
        Boolean getAtivo();
        Integer getVersaoToken();
        Boolean getTrocarSenhaNoProximoLogin();
    }

    Optional<EstadoToken> findEstadoTokenById(Long id);
}
