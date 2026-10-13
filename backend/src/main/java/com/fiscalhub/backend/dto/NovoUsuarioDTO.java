package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.enums.Papel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Usuário criado pelo ADMIN com senha provisória (troca obrigatória no próximo login). */
public record NovoUsuarioDTO(@NotBlank @Size(max = 100) String nome,
                             @NotBlank @Email @Size(max = 100) String email,
                             @NotNull Papel papel,
                             @NotBlank @Size(min = 6, max = 40) String senhaProvisoria) {
}
