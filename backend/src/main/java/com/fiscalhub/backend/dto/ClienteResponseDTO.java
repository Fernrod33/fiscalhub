package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.Cliente;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ClienteResponseDTO {

    private Long id;
    private String razaoSocial;
    private String nomeFantasia;
    private String cnpj;
    private String inscricaoEstadual;
    private String inscricaoMunicipal;
    private String endereco;
    private String cidade;
    private String estado;
    private String cep;
    private String telefone;
    private Boolean ativa;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;

    public static ClienteResponseDTO fromEntity(Cliente cliente) {
        ClienteResponseDTO dto = new ClienteResponseDTO();
        dto.setId(cliente.getId());
        dto.setRazaoSocial(cliente.getRazaoSocial());
        dto.setNomeFantasia(cliente.getNomeFantasia());
        dto.setCnpj(cliente.getCnpj());
        dto.setInscricaoEstadual(cliente.getInscricaoEstadual());
        dto.setInscricaoMunicipal(cliente.getInscricaoMunicipal());
        dto.setEndereco(cliente.getEndereco());
        dto.setCidade(cliente.getCidade());
        dto.setEstado(cliente.getEstado());
        dto.setCep(cliente.getCep());
        dto.setTelefone(cliente.getTelefone());
        dto.setAtiva(cliente.getAtiva());
        dto.setDataCriacao(cliente.getDataCriacao());
        dto.setDataAtualizacao(cliente.getDataAtualizacao());
        return dto;
    }
}
