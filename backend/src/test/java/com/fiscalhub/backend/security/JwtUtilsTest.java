package com.fiscalhub.backend.security;

import com.fiscalhub.backend.entity.Escritorio;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsTest {

    private final JwtUtils jwtUtils = new JwtUtils("x".repeat(32), 60_000);

    @Test
    void recusaSegredoComMenosDe256Bits() {
        assertThatThrownBy(() -> new JwtUtils("curto-demais", 60_000))
                .isInstanceOf(WeakKeyException.class);
    }

    @Test
    void tokenCarregaUsuarioEscritorioEPapel() {
        Escritorio escritorio = new Escritorio();
        escritorio.setId(7L);
        Usuario usuario = new Usuario();
        usuario.setId(3L);
        usuario.setEmail("a@a.com");
        usuario.setPapel(Papel.CONSULTOR);
        usuario.setEscritorio(escritorio);

        assertThat(jwtUtils.autenticar(jwtUtils.gerarToken(usuario)))
                .contains(new UsuarioAutenticado(3L, "a@a.com", 7L, Papel.CONSULTOR));
    }

    @Test
    void tokenInvalidoOuDeOutraChaveNaoAutentica() {
        assertThat(jwtUtils.autenticar("lixo")).isEmpty();
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail("a@a.com");
        usuario.setPapel(Papel.ADMIN);
        usuario.setEscritorio(new Escritorio());
        String deOutraChave = new JwtUtils("y".repeat(32), 60_000).gerarToken(usuario);
        assertThat(jwtUtils.autenticar(deOutraChave)).isEmpty();
    }
}
