package com.fiscalhub.backend;

import com.fiscalhub.backend.dto.EmpresaRequestDTO;
import com.fiscalhub.backend.dto.EmpresaResponseDTO;
import com.fiscalhub.backend.entity.Empresa;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.exception.DuplicateResourceException;
import com.fiscalhub.backend.exception.ForbiddenException;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.repository.EmpresaRepository;
import com.fiscalhub.backend.service.EmpresaService;
import com.fiscalhub.backend.service.UsuarioService;
import com.fiscalhub.backend.util.CnpjValidationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmpresaServiceTest {

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private UsuarioService usuarioService;

    @InjectMocks
    private EmpresaService empresaService;

    private Usuario usuarioA;
    private Usuario usuarioB;
    private Empresa empresaA;

    @BeforeEach
    void setUp() {
        usuarioA = new Usuario();
        usuarioA.setId(1L);
        usuarioA.setEmail("usuario-a@test.com");

        usuarioB = new Usuario();
        usuarioB.setId(2L);
        usuarioB.setEmail("usuario-b@test.com");

        empresaA = new Empresa();
        empresaA.setId(10L);
        empresaA.setRazaoSocial("Empresa A Ltda");
        empresaA.setCnpj("00000000000100");
        empresaA.setAtiva(true);
        empresaA.setUsuario(usuarioA);
    }

    @Test
    void findAllByOwner_retornaEmpresasDoUsuario() {
        when(usuarioService.findByEmail("usuario-a@test.com")).thenReturn(usuarioA);
        when(empresaRepository.findByUsuarioId(1L)).thenReturn(List.of(empresaA));

        List<EmpresaResponseDTO> resultado = empresaService.findAllByOwner("usuario-a@test.com");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getRazaoSocial()).isEqualTo("Empresa A Ltda");
    }

    @Test
    void findByIdAndOwner_retornaEmpresaDoProprioUsuario() {
        when(empresaRepository.findById(10L)).thenReturn(Optional.of(empresaA));

        EmpresaResponseDTO dto = empresaService.findByIdAndOwner(10L, "usuario-a@test.com");

        assertThat(dto.getId()).isEqualTo(10L);
    }

    @Test
    void findByIdAndOwner_lancaForbiddenParaOutroUsuario() {
        when(empresaRepository.findById(10L)).thenReturn(Optional.of(empresaA));

        assertThatThrownBy(() -> empresaService.findByIdAndOwner(10L, "usuario-b@test.com"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void findByIdAndOwner_lancaNotFoundParaEmpresaInexistente() {
        when(empresaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> empresaService.findByIdAndOwner(99L, "usuario-a@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_cadastraEmpresaParaUsuarioAutenticado() {
        EmpresaRequestDTO dto = buildRequestDTO("12345678000195");
        when(usuarioService.findByEmail("usuario-a@test.com")).thenReturn(usuarioA);
        when(empresaRepository.existsByCnpjAndUsuarioId("12345678000195", 1L)).thenReturn(false);
        when(empresaRepository.save(any(Empresa.class))).thenAnswer(inv -> {
            Empresa e = inv.getArgument(0);
            e.setId(20L);
            return e;
        });

        EmpresaResponseDTO result = empresaService.create(dto, "usuario-a@test.com");

        assertThat(result.getCnpj()).isEqualTo("12345678000195");
        verify(empresaRepository).save(any(Empresa.class));
    }

    @Test
    void create_lancaDuplicateExceptionParaCnpjJaCadastrado() {
        EmpresaRequestDTO dto = buildRequestDTO("12345678000195");
        when(usuarioService.findByEmail("usuario-a@test.com")).thenReturn(usuarioA);
        when(empresaRepository.existsByCnpjAndUsuarioId("12345678000195", 1L)).thenReturn(true);

        assertThatThrownBy(() -> empresaService.create(dto, "usuario-a@test.com"))
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
        EmpresaRequestDTO dto = buildRequestDTO("11.111.111/1111-11");
        dto.setCep("12345678");

        assertThatThrownBy(() -> empresaService.create(dto, "usuario-a@test.com"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("CNPJ inválido.");
    }

    @Test
    void create_rejeitaCepInvalido() {
        EmpresaRequestDTO dto = buildRequestDTO("12345678000195");
        dto.setCep("12345");

        assertThatThrownBy(() -> empresaService.create(dto, "usuario-a@test.com"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("CEP deve possuir exatamente 8 números.");
    }

    @Test
    void update_atualizaEmpresaDoProprioUsuario() {
        EmpresaRequestDTO dto = buildRequestDTO("12345678000195");
        dto.setRazaoSocial("Empresa A Atualizada");
        when(empresaRepository.findById(10L)).thenReturn(Optional.of(empresaA));
        when(empresaRepository.save(any(Empresa.class))).thenAnswer(inv -> inv.getArgument(0));

        EmpresaResponseDTO result = empresaService.update(10L, dto, "usuario-a@test.com");

        assertThat(result.getRazaoSocial()).isEqualTo("Empresa A Atualizada");
    }

    @Test
    void update_lancaForbiddenParaOutroUsuario() {
        EmpresaRequestDTO dto = buildRequestDTO("12345678000195");
        when(empresaRepository.findById(10L)).thenReturn(Optional.of(empresaA));

        assertThatThrownBy(() -> empresaService.update(10L, dto, "usuario-b@test.com"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void delete_removeEmpresaDoProprioUsuario() {
        when(empresaRepository.findById(10L)).thenReturn(Optional.of(empresaA));

        empresaService.delete(10L, "usuario-a@test.com");

        verify(empresaRepository).delete(empresaA);
    }

    @Test
    void delete_lancaForbiddenParaOutroUsuario() {
        when(empresaRepository.findById(10L)).thenReturn(Optional.of(empresaA));

        assertThatThrownBy(() -> empresaService.delete(10L, "usuario-b@test.com"))
                .isInstanceOf(ForbiddenException.class);
        verify(empresaRepository, never()).delete(any());
    }

    private EmpresaRequestDTO buildRequestDTO(String cnpj) {
        EmpresaRequestDTO dto = new EmpresaRequestDTO();
        dto.setRazaoSocial("Empresa Teste");
        dto.setCnpj(cnpj);
        dto.setAtiva(true);
        return dto;
    }
}
