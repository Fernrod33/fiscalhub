package com.fiscalhub.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignupRequest {
    @NotBlank
    @Size(max = 100)
    private String nome;

    @NotBlank
    @Email
    @Size(max = 100)
    private String email;

    @NotBlank
    @Size(min = 6, max = 40)
    private String senha;

    @Size(max = 14)
    private String cpf;

    @Size(max = 20)
    private String telefone;

    @NotBlank
    @Size(max = 150)
    private String nomeEscritorio;

    /** CNPJ ou CPF do escritório (opcional). */
    @Size(max = 18)
    private String documentoEscritorio;
}
