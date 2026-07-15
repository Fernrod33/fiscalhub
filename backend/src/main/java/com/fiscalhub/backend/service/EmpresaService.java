package com.fiscalhub.backend.service;

import com.fiscalhub.backend.entity.Empresa;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.exception.DuplicateResourceException;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.repository.EmpresaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class EmpresaService {

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private UsuarioService usuarioService;

    public List<Empresa> findAll() {
        return empresaRepository.findAll();
    }

    public Empresa findById(Long id) {
        return empresaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa não encontrada com a ID: " + id));
    }

    public List<Empresa> findByUsuarioId(Long usuarioId) {
        return empresaRepository.findByUsuarioId(usuarioId);
    }

    public Empresa create(Empresa empresa, Long usuarioId) {
        if (existsByCnpj(empresa.getCnpj())) {
            throw new DuplicateResourceException("Já existe uma empresa cadastrada com o CNPJ: " + empresa.getCnpj());
        }

        Usuario usuario = usuarioService.findById(usuarioId);
        empresa.setUsuario(usuario);
        return empresaRepository.save(empresa);
    }

    public Empresa update(Long id, Empresa empresaDetails) {
        Empresa empresa = findById(id);
        
        empresa.setRazaoSocial(empresaDetails.getRazaoSocial());
        empresa.setNomeFantasia(empresaDetails.getNomeFantasia());
        empresa.setCnpj(empresaDetails.getCnpj());
        empresa.setInscricaoEstadual(empresaDetails.getInscricaoEstadual());
        empresa.setInscricaoMunicipal(empresaDetails.getInscricaoMunicipal());
        empresa.setEndereco(empresaDetails.getEndereco());
        empresa.setCidade(empresaDetails.getCidade());
        empresa.setEstado(empresaDetails.getEstado());
        empresa.setCep(empresaDetails.getCep());
        empresa.setTelefone(empresaDetails.getTelefone());
        empresa.setAtiva(empresaDetails.getAtiva());

        return empresaRepository.save(empresa);
    }

    public void delete(Long id) {
        Empresa empresa = findById(id);
        empresaRepository.delete(empresa);
    }

    public boolean existsByCnpj(String cnpj) {
        return empresaRepository.existsByCnpj(cnpj);
    }
}
