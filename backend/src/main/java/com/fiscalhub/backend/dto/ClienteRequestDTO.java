package com.fiscalhub.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClienteRequestDTO {

    @NotBlank
    @Size(max = 150)
    private String razaoSocial;

    @Size(max = 150)
    private String nomeFantasia;

    @NotBlank(message = "CNPJ é obrigatório.")
    @Pattern(regexp = "^(\\d{14}|\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})$", message = "CNPJ deve possuir 14 números.")
    private String cnpj;

    @Size(max = 20)
    private String inscricaoEstadual;

    @Size(max = 20)
    private String inscricaoMunicipal;

    @Size(max = 200)
    private String endereco;

    @Size(max = 100)
    private String cidade;

    @Size(max = 2)
    private String estado;

    @Pattern(regexp = "^(|\\d{8})$", message = "CEP deve possuir exatamente 8 números.")
    private String cep;

    @Size(max = 20)
    private String telefone;

    private Boolean ativa = true;
}
