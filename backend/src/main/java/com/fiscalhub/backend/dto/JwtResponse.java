package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;

public record JwtResponse(String token, String type, Long id, String email, String nome,
                          Long escritorioId, Papel papel, Boolean trocarSenhaNoProximoLogin) {

    public static JwtResponse de(String token, Usuario u) {
        return new JwtResponse(token, "Bearer", u.getId(), u.getEmail(), u.getNome(),
                u.getEscritorio().getId(), u.getPapel(), u.getTrocarSenhaNoProximoLogin());
    }
}
