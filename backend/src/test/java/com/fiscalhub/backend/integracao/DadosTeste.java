package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Escritorio;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.repository.EscritorioRepository;
import com.fiscalhub.backend.repository.UsuarioRepository;
import com.fiscalhub.backend.security.JwtUtils;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Fixture dos testes de integração: escritórios, usuários com papel e tokens. */
@TestComponent
public class DadosTeste {

    public static final String SENHA = "senha-de-teste";

    private final EscritorioRepository escritorioRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private String senhaCodificada;

    public DadosTeste(EscritorioRepository escritorioRepository, UsuarioRepository usuarioRepository,
                      PasswordEncoder passwordEncoder, JwtUtils jwtUtils) {
        this.escritorioRepository = escritorioRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    public Escritorio escritorio(String nome) {
        Escritorio e = new Escritorio();
        e.setNome(nome);
        return escritorioRepository.save(e);
    }

    public Usuario usuario(Escritorio escritorio, String email, Papel papel) {
        if (senhaCodificada == null) {
            senhaCodificada = passwordEncoder.encode(SENHA); // BCrypt é lento: codifica uma vez só
        }
        Usuario u = new Usuario();
        u.setNome(email);
        u.setEmail(email);
        u.setSenha(senhaCodificada);
        u.setPapel(papel);
        u.setEscritorio(escritorio);
        return usuarioRepository.save(u);
    }

    public String token(Usuario usuario) {
        return "Bearer " + jwtUtils.gerarToken(usuario);
    }
}
