package com.fiscalhub.backend.security;

import com.fiscalhub.backend.entity.enums.Papel;

import java.security.Principal;

/** Usuário da requisição, montado a partir das claims do JWT (sem ir ao banco nas checagens dos services). */
public record UsuarioAutenticado(Long id, String email, Long escritorioId, Papel papel, int versaoToken, java.util.UUID sessaoId)
        implements Principal {

    /** Sem sessão (processos do sistema e testes de serviço). */
    public UsuarioAutenticado(Long id, String email, Long escritorioId, Papel papel, int versaoToken) {
        this(id, email, escritorioId, papel, versaoToken, null);
    }

    @Override
    public String getName() {
        return email;
    }
}
