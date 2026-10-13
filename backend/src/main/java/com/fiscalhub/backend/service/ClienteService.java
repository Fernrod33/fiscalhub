package com.fiscalhub.backend.service;

import com.fiscalhub.backend.dto.ClienteRequestDTO;
import com.fiscalhub.backend.dto.ClienteResponseDTO;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.exception.DuplicateResourceException;
import com.fiscalhub.backend.repository.ClienteRepository;
import com.fiscalhub.backend.repository.EscritorioRepository;
import com.fiscalhub.backend.repository.UsuarioRepository;
import com.fiscalhub.backend.util.CnpjValidationUtils;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final EscritorioRepository escritorioRepository;
    private final UsuarioRepository usuarioRepository;
    private final AcessoRecursoService acessoRecursoService;

    public ClienteService(ClienteRepository clienteRepository, EscritorioRepository escritorioRepository,
                          UsuarioRepository usuarioRepository, AcessoRecursoService acessoRecursoService) {
        this.clienteRepository = clienteRepository;
        this.escritorioRepository = escritorioRepository;
        this.usuarioRepository = usuarioRepository;
        this.acessoRecursoService = acessoRecursoService;
    }

    public List<ClienteResponseDTO> listar(UsuarioAutenticado ua) {
        return clienteRepository.findByEscritorioIdOrderByRazaoSocial(ua.escritorioId())
                .stream()
                .map(ClienteResponseDTO::fromEntity)
                .toList();
    }

    public ClienteResponseDTO buscar(Long id, UsuarioAutenticado ua) {
        return ClienteResponseDTO.fromEntity(acessoRecursoService.cliente(id, ua));
    }

    public ClienteResponseDTO create(ClienteRequestDTO dto, UsuarioAutenticado ua) {
        validarCamposCliente(dto);
        if (clienteRepository.existsByCnpjAndEscritorioId(dto.getCnpj(), ua.escritorioId())) {
            throw new DuplicateResourceException("O escritório já possui um cliente com o CNPJ: " + dto.getCnpj());
        }
        Cliente cliente = new Cliente();
        mapDtoToEntity(dto, cliente);
        cliente.setEscritorio(escritorioRepository.getReferenceById(ua.escritorioId()));
        cliente.setCriadoPor(usuarioRepository.getReferenceById(ua.id()));
        return ClienteResponseDTO.fromEntity(clienteRepository.save(cliente));
    }

    public ClienteResponseDTO update(Long id, ClienteRequestDTO dto, UsuarioAutenticado ua) {
        validarCamposCliente(dto);
        Cliente cliente = acessoRecursoService.cliente(id, ua);
        mapDtoToEntity(dto, cliente);
        return ClienteResponseDTO.fromEntity(clienteRepository.save(cliente));
    }

    public void delete(Long id, UsuarioAutenticado ua) {
        clienteRepository.delete(acessoRecursoService.cliente(id, ua));
    }

    private void validarCamposCliente(ClienteRequestDTO dto) {
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

    private void mapDtoToEntity(ClienteRequestDTO dto, Cliente cliente) {
        cliente.setRazaoSocial(dto.getRazaoSocial());
        cliente.setNomeFantasia(dto.getNomeFantasia());
        cliente.setCnpj(dto.getCnpj());
        cliente.setInscricaoEstadual(dto.getInscricaoEstadual());
        cliente.setInscricaoMunicipal(dto.getInscricaoMunicipal());
        cliente.setEndereco(dto.getEndereco());
        cliente.setCidade(dto.getCidade());
        cliente.setEstado(dto.getEstado());
        cliente.setCep(dto.getCep());
        cliente.setTelefone(dto.getTelefone());
        cliente.setAtiva(dto.getAtiva() != null ? dto.getAtiva() : true);
    }
}
