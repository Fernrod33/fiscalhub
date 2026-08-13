package com.fiscalhub.backend.dto;

import com.fiscalhub.backend.entity.Empresa;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EmpresaResponseDTO {

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

    public static EmpresaResponseDTO fromEntity(Empresa empresa) {
        EmpresaResponseDTO dto = new EmpresaResponseDTO();
        dto.setId(empresa.getId());
        dto.setRazaoSocial(empresa.getRazaoSocial());
        dto.setNomeFantasia(empresa.getNomeFantasia());
        dto.setCnpj(empresa.getCnpj());
        dto.setInscricaoEstadual(empresa.getInscricaoEstadual());
        dto.setInscricaoMunicipal(empresa.getInscricaoMunicipal());
        dto.setEndereco(empresa.getEndereco());
        dto.setCidade(empresa.getCidade());
        dto.setEstado(empresa.getEstado());
        dto.setCep(empresa.getCep());
        dto.setTelefone(empresa.getTelefone());
        dto.setAtiva(empresa.getAtiva());
        dto.setDataCriacao(empresa.getDataCriacao());
        dto.setDataAtualizacao(empresa.getDataAtualizacao());
        return dto;
    }
}
