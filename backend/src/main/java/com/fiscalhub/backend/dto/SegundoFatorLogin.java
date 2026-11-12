package com.fiscalhub.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Segunda etapa do login: o desafio da primeira e o código do aplicativo (6 dígitos) ou um código de recuperação. */
public record SegundoFatorLogin(@NotBlank @Size(max = 1000) String desafio, @NotBlank @Size(max = 20) String codigo) {
}
