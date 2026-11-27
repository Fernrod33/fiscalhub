package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.security.SenhaForte;

import jakarta.validation.constraints.NotBlank;

public record TrocaSenhaDTO(@NotBlank String senhaAtual, @SenhaForte String novaSenha) {
}
