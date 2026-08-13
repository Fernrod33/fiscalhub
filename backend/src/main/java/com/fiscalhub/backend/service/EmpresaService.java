package com.fiscalhub.backend.service;

import com.fiscalhub.backend.dto.EmpresaRequestDTO;
import com.fiscalhub.backend.dto.EmpresaResponseDTO;
import com.fiscalhub.backend.entity.Empresa;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.exception.DuplicateResourceException;
import com.fiscalhub.backend.exception.ForbiddenException;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.repository.EmpresaRepository;
import com.fiscalhub.backend.util.CnpjValidationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class EmpresaService {

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private UsuarioService usuarioService;

    /** Used internally by other services that need the Empresa entity. */
    public Empresa findById(Long id) {
        return empresaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa não encontrada com a ID: " + id));
    }

    public List<EmpresaResponseDTO> findAllByOwner(String username) {
        Usuario usuario = usuarioService.findByEmail(username);
        return empresaRepository.findByUsuarioId(usuario.getId())
                .stream()
                .map(EmpresaResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public EmpresaResponseDTO findByIdAndOwner(Long id, String username) {
        Empresa empresa = empresaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa não encontrada com a ID: " + id));
        checkOwnership(empresa, username);
        return EmpresaResponseDTO.fromEntity(empresa);
    }

    public EmpresaResponseDTO create(EmpresaRequestDTO dto, String username) {
        validarCamposEmpresa(dto);
        Usuario usuario = usuarioService.findByEmail(username);
        if (empresaRepository.existsByCnpjAndUsuarioId(dto.getCnpj(), usuario.getId())) {
            throw new DuplicateResourceException("Você já possui uma empresa cadastrada com o CNPJ: " + dto.getCnpj());
        }
        Empresa empresa = new Empresa();
        mapDtoToEntity(dto, empresa);
        empresa.setUsuario(usuario);
        return EmpresaResponseDTO.fromEntity(empresaRepository.save(empresa));
    }

    public EmpresaResponseDTO update(Long id, EmpresaRequestDTO dto, String username) {
        validarCamposEmpresa(dto);
        Empresa empresa = empresaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa não encontrada com a ID: " + id));
        checkOwnership(empresa, username);
        mapDtoToEntity(dto, empresa);
        return EmpresaResponseDTO.fromEntity(empresaRepository.save(empresa));
    }

    public void delete(Long id, String username) {
        Empresa empresa = empresaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa não encontrada com a ID: " + id));
        checkOwnership(empresa, username);
        empresaRepository.delete(empresa);
    }

    private void checkOwnership(Empresa empresa, String username) {
        if (!empresa.getUsuario().getEmail().equals(username)) {
            throw new ForbiddenException("Acesso negado: você não possui acesso a esta empresa.");
        }
    }

    private void validarCamposEmpresa(EmpresaRequestDTO dto) {
        if (dto.getCep() != null && !dto.getCep().isBlank()) {
            String cep = dto.getCep().replaceAll("\\D", "");
            if (!cep.matches("\\d{8}")) {
                throw new BusinessRuleException("CEP deve possuir exatamente 8 números.");
            }
            dto.setCep(cep);
        }

        if (dto.getCnpj() == null || dto.getCnpj().isBlank()) {
            throw new BusinessRuleException("CNPJ é obrigatório.");
        }

        String cnpj = CnpjValidationUtils.normalize(dto.getCnpj());
        if (!cnpj.matches("\\d{14}")) {
            throw new BusinessRuleException("CNPJ deve possuir 14 números.");
        }
        if (!CnpjValidationUtils.isValid(cnpj)) {
            throw new BusinessRuleException("CNPJ inválido.");
        }
        dto.setCnpj(cnpj);
    }

    private void mapDtoToEntity(EmpresaRequestDTO dto, Empresa empresa) {
        empresa.setRazaoSocial(dto.getRazaoSocial());
        empresa.setNomeFantasia(dto.getNomeFantasia());
        empresa.setCnpj(dto.getCnpj());
        empresa.setInscricaoEstadual(dto.getInscricaoEstadual());
        empresa.setInscricaoMunicipal(dto.getInscricaoMunicipal());
        empresa.setEndereco(dto.getEndereco());
        empresa.setCidade(dto.getCidade());
        empresa.setEstado(dto.getEstado());
        empresa.setCep(dto.getCep());
        empresa.setTelefone(dto.getTelefone());
        empresa.setAtiva(dto.getAtiva() != null ? dto.getAtiva() : true);
    }
}
