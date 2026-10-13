package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.enums.Papel;
import jakarta.validation.constraints.NotNull;

public record AlteracaoPapelDTO(@NotNull Papel papel) {
}
