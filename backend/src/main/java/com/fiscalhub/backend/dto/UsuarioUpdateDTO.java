package com.fiscalhub.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dados editáveis do próprio usuário. E-mail (login) e senha não mudam por aqui. */
public record UsuarioUpdateDTO(@NotBlank @Size(max = 100) String nome,
                               @Size(max = 14) String cpf,
                               @Size(max = 20) String telefone) {
}
