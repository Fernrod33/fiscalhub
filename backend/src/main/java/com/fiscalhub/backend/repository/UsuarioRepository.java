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
    /** Estado conferido a cada requisição pelo filtro JWT (uma leitura: usuário, escritório e a sessão do token). */
    interface EstadoToken {
        Boolean getAtivo();
        Integer getVersaoToken();
        Boolean getTrocarSenhaNoProximoLogin();
        /** O escritório exige o segundo fator e o usuário ainda não o ativou. */
        Boolean getSegundoFatorPendente();
        Boolean getSessaoAberta();
        /** Versão dos termos que o usuário aceitou (nula = nunca). */
        String getTermosVersao();
    }

    @org.springframework.data.jpa.repository.Query(nativeQuery = true, value = """
            SELECT u.ativo AS ativo, u.versao_token AS versaoToken, u.trocar_senha_no_proximo_login AS trocarSenhaNoProximoLogin,
                   (e.exigir_segundo_fator AND NOT u.totp_ativo) AS segundoFatorPendente,
                   EXISTS (SELECT 1 FROM sessao s WHERE s.id = :sessao AND s.usuario_id = u.id AND s.revogada_em IS NULL
                           AND s.expira_em > now()) AS sessaoAberta, u.termos_versao AS termosVersao
            FROM usuario u JOIN escritorio e ON e.id = u.escritorio_id WHERE u.id = :id""")
    Optional<EstadoToken> estadoToken(@org.springframework.data.repository.query.Param("id") Long id,
                                      @org.springframework.data.repository.query.Param("sessao") java.util.UUID sessao);
}
