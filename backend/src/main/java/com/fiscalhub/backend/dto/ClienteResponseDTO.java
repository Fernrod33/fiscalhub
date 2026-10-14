package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.Cliente;

import java.time.LocalDateTime;

public record ClienteResponseDTO(Long id, String razaoSocial, String nomeFantasia, String cnpj, String inscricaoEstadual,
                                 String inscricaoMunicipal, String endereco, String cidade, String estado, String cep,
                                 String telefone, Boolean ativa, LocalDateTime dataCriacao, LocalDateTime dataAtualizacao) {

    public static ClienteResponseDTO fromEntity(Cliente c) {
        return new ClienteResponseDTO(c.getId(), c.getRazaoSocial(), c.getNomeFantasia(), c.getCnpj(),
                c.getInscricaoEstadual(), c.getInscricaoMunicipal(), c.getEndereco(), c.getCidade(), c.getEstado(),
                c.getCep(), c.getTelefone(), c.getAtiva(), c.getDataCriacao(), c.getDataAtualizacao());
    }
}
