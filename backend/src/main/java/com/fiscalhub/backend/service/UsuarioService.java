package com.fiscalhub.backend.service;

import com.fiscalhub.backend.dto.NovoUsuarioDTO;
import com.fiscalhub.backend.dto.SignupRequest;
import com.fiscalhub.backend.dto.TrocaSenhaDTO;
import com.fiscalhub.backend.dto.UsuarioUpdateDTO;
import com.fiscalhub.backend.entity.Escritorio;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.repository.EscritorioRepository;
import com.fiscalhub.backend.repository.UsuarioRepository;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.util.CnpjValidationUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final EscritorioRepository escritorioRepository;
    private final AcessoRecursoService acessoRecursoService;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, EscritorioRepository escritorioRepository,
                          AcessoRecursoService acessoRecursoService, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.escritorioRepository = escritorioRepository;
        this.acessoRecursoService = acessoRecursoService;
        this.passwordEncoder = passwordEncoder;
    }

    public Usuario findByEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não localizado."));
    }

    public Usuario findById(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não localizado."));
    }

    /** Signup: cria o escritório e o primeiro usuário como ADMIN, na mesma transação. */
    public Usuario registrarEscritorio(SignupRequest req) {
        Escritorio escritorio = new Escritorio();
        escritorio.setNome(req.getNomeEscritorio());
        escritorio.setDocumento(validarDocumento(req.getDocumentoEscritorio()));
        escritorioRepository.save(escritorio);

        Usuario admin = novoUsuario(req.getNome(), req.getEmail(), req.getSenha(), Papel.ADMIN, escritorio);
        admin.setCpf(req.getCpf());
        admin.setTelefone(req.getTelefone());
        return usuarioRepository.save(admin);
    }

    public List<Usuario> listarDoEscritorio(UsuarioAutenticado ua) {
        return usuarioRepository.findByEscritorioIdOrderByNome(ua.escritorioId());
    }

    // ponytail: senha provisória definida pelo ADMIN; trocar por convite por e-mail quando houver serviço de e-mail
    public Usuario criarNoEscritorio(UsuarioAutenticado ua, NovoUsuarioDTO dto) {
        Usuario usuario = novoUsuario(dto.nome(), dto.email(), dto.senhaProvisoria(), dto.papel(),
                escritorioRepository.getReferenceById(ua.escritorioId()));
        usuario.setTrocarSenhaNoProximoLogin(true);
        return usuarioRepository.save(usuario);
    }

    public Usuario alterarPapel(UsuarioAutenticado ua, Long usuarioId, Papel papel) {
        Usuario usuario = acessoRecursoService.usuario(usuarioId, ua);
        if (papel != Papel.ADMIN) {
            garantirOutroAdminAtivo(usuario);
        }
        if (usuario.getPapel() != papel) {
            usuario.setPapel(papel);
            invalidarTokens(usuario);
        }
        return usuario;
    }

    /** Saída de usuário é desativação (soft delete): login bloqueado, nada fica órfão. */
    public Usuario desativar(UsuarioAutenticado ua, Long usuarioId) {
        Usuario usuario = acessoRecursoService.usuario(usuarioId, ua);
        garantirOutroAdminAtivo(usuario);
        usuario.setAtivo(false);
        invalidarTokens(usuario);
        return usuario;
    }

    public Usuario update(UsuarioAutenticado ua, UsuarioUpdateDTO dto) {
        Usuario usuario = findById(ua.id());
        usuario.setNome(dto.nome());
        usuario.setCpf(dto.cpf());
        usuario.setTelefone(dto.telefone());
        return usuario;
    }

    /** Troca a senha e invalida os tokens anteriores; quem chama emite um token novo. */
    public Usuario trocarSenha(UsuarioAutenticado ua, TrocaSenhaDTO dto) {
        Usuario usuario = findById(ua.id());
        if (!passwordEncoder.matches(dto.senhaAtual(), usuario.getSenha())) {
            throw new BusinessRuleException("Senha atual incorreta.");
        }
        usuario.setSenha(passwordEncoder.encode(dto.novaSenha()));
        usuario.setTrocarSenhaNoProximoLogin(false);
        invalidarTokens(usuario);
        return usuarioRepository.saveAndFlush(usuario);
    }

    private static void invalidarTokens(Usuario usuario) {
        usuario.setVersaoToken(usuario.getVersaoToken() + 1);
    }

    private Usuario novoUsuario(String nome, String email, String senha, Papel papel, Escritorio escritorio) {
        if (usuarioRepository.existsByEmail(email)) {
            throw new BusinessRuleException("E-mail já cadastrado");
        }
        Usuario usuario = new Usuario();
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenha(passwordEncoder.encode(senha));
        usuario.setPapel(papel);
        usuario.setEscritorio(escritorio);
        return usuario;
    }

    private void garantirOutroAdminAtivo(Usuario usuario) {
        if (usuario.getPapel() == Papel.ADMIN && usuario.getAtivo()
                && usuarioRepository.countByEscritorioIdAndPapelAndAtivoTrue(usuario.getEscritorio().getId(), Papel.ADMIN) <= 1) {
            throw new BusinessRuleException("O escritório precisa de pelo menos um ADMIN ativo.");
        }
    }

    private static String validarDocumento(String documento) {
        if (documento == null || documento.isBlank()) {
            return null;
        }
        String digitos = CnpjValidationUtils.normalize(documento);
        boolean valido = digitos.length() == 14 ? CnpjValidationUtils.isValid(digitos) : CnpjValidationUtils.isValidCpf(digitos);
        if (!valido) {
            throw new BusinessRuleException("CNPJ ou CPF do escritório inválido.");
        }
        return digitos;
    }
}
