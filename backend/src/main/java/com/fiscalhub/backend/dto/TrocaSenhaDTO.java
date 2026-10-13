package com.fiscalhub.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrocaSenhaDTO(@NotBlank String senhaAtual, @NotBlank @Size(min = 6, max = 40) String novaSenha) {
}
