package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;

import java.time.LocalDateTime;

/** Resposta pública de usuário: nunca inclui a senha. */
public record UsuarioResponseDTO(Long id, String nome, String email, String cpf, String telefone,
                                 Boolean ativo, Papel papel, Boolean trocarSenhaNoProximoLogin,
                                 Long escritorioId, LocalDateTime dataCriacao) {

    public static UsuarioResponseDTO fromEntity(Usuario usuario) {
        return new UsuarioResponseDTO(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getCpf(),
                usuario.getTelefone(), usuario.getAtivo(), usuario.getPapel(), usuario.getTrocarSenhaNoProximoLogin(),
                usuario.getEscritorio().getId(), usuario.getDataCriacao());
    }
}
