package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.security.SenhaForte;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpos das rotas públicas do convite e da recuperação de senha (o token vem do link do e-mail). */
public final class ContaPorEmailDTO {

    private ContaPorEmailDTO() {
    }

    public record AceiteConvite(@NotBlank @Size(max = 100) String token, @SenhaForte String senha) {
    }

    public record PedidoRecuperacao(@NotBlank @Email @Size(max = 100) String email) {
    }

    /** codigo: do aplicativo autenticador ou de recuperação, obrigatório se o 2º fator estiver ativo. */
    public record Redefinicao(@NotBlank @Size(max = 100) String token, @SenhaForte String novaSenha, @Size(max = 20) String codigo) {
    }
}
