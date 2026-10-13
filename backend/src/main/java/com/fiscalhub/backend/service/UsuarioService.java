package com.fiscalhub.backend.service;

import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Usuario> findAll() {
        return usuarioRepository.findAll();
    }

    public Usuario findById(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não localizado com a ID: " + id));
    }

    public Usuario findByEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não localizado com o email: " + email));
    }

    public Usuario create(Usuario usuario) {
        if (usuarioRepository.existsByEmail(usuario.getEmail())) {
            throw new BusinessRuleException("E-mail já cadastrado");
        }
        usuario.setSenha(passwordEncoder.encode(usuario.getSenha()));
        return usuarioRepository.save(usuario);
    }

    public Usuario update(Long id, Usuario usuarioDetails) {
        Usuario usuario = findById(id);
        
        usuario.setNome(usuarioDetails.getNome());
        usuario.setEmail(usuarioDetails.getEmail());
        usuario.setCpf(usuarioDetails.getCpf());
        usuario.setTelefone(usuarioDetails.getTelefone());
        usuario.setAtivo(usuarioDetails.getAtivo());
        
        return usuarioRepository.save(usuario);
    }

    public void delete(Long id) {
        Usuario usuario = findById(id);
        usuarioRepository.delete(usuario);
    }

    public boolean existsByEmail(String email) {
        return usuarioRepository.existsByEmail(email);
    }
}
