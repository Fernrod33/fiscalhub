package com.fiscalhub.backend;

import com.fiscalhub.backend.dto.ClienteRequestDTO;
import com.fiscalhub.backend.dto.ClienteResponseDTO;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.exception.DuplicateResourceException;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.repository.ClienteRepository;
import com.fiscalhub.backend.repository.EscritorioRepository;
import com.fiscalhub.backend.repository.UsuarioRepository;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.AcessoRecursoService;
import com.fiscalhub.backend.service.ClienteService;
import com.fiscalhub.backend.util.CnpjValidationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private EscritorioRepository escritorioRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AcessoRecursoService acessoRecursoService;

    @InjectMocks
    private ClienteService clienteService;

    private static final UsuarioAutenticado UA = new UsuarioAutenticado(1L, "usuario-a@test.com", 5L, Papel.ADMIN, 0);
    private static final UsuarioAutenticado UB = new UsuarioAutenticado(2L, "usuario-b@test.com", 6L, Papel.ADMIN, 0);
    private Cliente clienteA;

    @BeforeEach
    void setUp() {
        clienteA = new Cliente();
        clienteA.setId(10L);
        clienteA.setRazaoSocial("Cliente A Ltda");
        clienteA.setCnpj("00000000000100");
        clienteA.setAtiva(true);
    }

    @Test
    void listar_retornaClientesDoEscritorio() {
        when(clienteRepository.findByEscritorioIdOrderByRazaoSocial(5L)).thenReturn(List.of(clienteA));

        List<ClienteResponseDTO> resultado = clienteService.listar(UA);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).razaoSocial()).isEqualTo("Cliente A Ltda");
    }

    @Test
    void buscar_retornaClienteDoProprioUsuario() {
        when(acessoRecursoService.cliente(10L, UA)).thenReturn(clienteA);

        ClienteResponseDTO dto = clienteService.buscar(10L, UA);

        assertThat(dto.id()).isEqualTo(10L);
    }

    @Test
    void buscar_propagaNotFoundDoControleDeAcesso() {
        when(acessoRecursoService.cliente(10L, UB))
                .thenThrow(new ResourceNotFoundException("Cliente não encontrado(a) com a ID: 10"));

        assertThatThrownBy(() -> clienteService.buscar(10L, UB))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_cadastraClienteParaUsuarioAutenticado() {
        ClienteRequestDTO dto = buildRequestDTO("12345678000195");
        when(clienteRepository.existsByCnpjAndEscritorioId("12345678000195", 5L)).thenReturn(false);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente e = inv.getArgument(0);
            e.setId(20L);
            return e;
        });

        ClienteResponseDTO result = clienteService.create(dto, UA);

        assertThat(result.cnpj()).isEqualTo("12345678000195");
        verify(clienteRepository).save(any(Cliente.class));
    }

    @Test
    void create_lancaDuplicateExceptionParaCnpjJaCadastrado() {
        ClienteRequestDTO dto = buildRequestDTO("12345678000195");
        when(clienteRepository.existsByCnpjAndEscritorioId("12345678000195", 5L)).thenReturn(true);

        assertThatThrownBy(() -> clienteService.create(dto, UA))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void cnpjValidationUtils_reconheceCnpjValidoEComMascara() {
        assertThat(CnpjValidationUtils.isValid("12345678000195")).isTrue();
        assertThat(CnpjValidationUtils.isValid("12.345.678/0001-95")).isTrue();
    }

    @Test
    void cnpjValidationUtils_rejeitaCnpjsInvalidos() {
        assertThat(CnpjValidationUtils.isValid("11111111111111")).isFalse();
        assertThat(CnpjValidationUtils.isValid("12345678000196")).isFalse();
        assertThat(CnpjValidationUtils.isValid("1234567800019")).isFalse();
    }

    @Test
    void create_rejeitaCnpjInvalido() {
        ClienteRequestDTO dto = buildRequestDTO("11.111.111/1111-11");
        dto.setCep("12345678");

        assertThatThrownBy(() -> clienteService.create(dto, UA))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("CNPJ inválido.");
    }

    @Test
    void create_rejeitaCepInvalido() {
        ClienteRequestDTO dto = buildRequestDTO("12345678000195");
        dto.setCep("12345");

        assertThatThrownBy(() -> clienteService.create(dto, UA))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("CEP deve possuir exatamente 8 números.");
    }

    @Test
    void update_atualizaClienteDoProprioUsuario() {
        ClienteRequestDTO dto = buildRequestDTO("12345678000195");
        dto.setRazaoSocial("Cliente A Atualizada");
        when(acessoRecursoService.cliente(10L, UA)).thenReturn(clienteA);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        ClienteResponseDTO result = clienteService.update(10L, dto, UA);

        assertThat(result.razaoSocial()).isEqualTo("Cliente A Atualizada");
    }

    @Test
    void delete_removeClienteDoProprioUsuario() {
        when(acessoRecursoService.cliente(10L, UA)).thenReturn(clienteA);

        clienteService.delete(10L, UA);

        verify(clienteRepository).delete(clienteA);
    }

    private ClienteRequestDTO buildRequestDTO(String cnpj) {
        ClienteRequestDTO dto = new ClienteRequestDTO();
        dto.setRazaoSocial("Cliente Teste");
        dto.setCnpj(cnpj);
        dto.setAtiva(true);
        return dto;
    }
}
