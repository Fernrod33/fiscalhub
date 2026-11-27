package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.enums.Papel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Usuário criado pelo ADMIN: recebe por e-mail o convite para definir a própria senha. */
public record NovoUsuarioDTO(@NotBlank @Size(max = 100) String nome,
                             @NotBlank @Email @Size(max = 100) String email,
                             @NotNull Papel papel) {
}
