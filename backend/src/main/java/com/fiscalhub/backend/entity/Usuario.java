package com.fiscalhub.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fiscalhub.backend.entity.enums.Papel;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String nome;

    @NotBlank
    @Email
    @Size(max = 100)
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @JsonIgnore
    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String senha;

    @Size(max = 14)
    @Column(length = 14)
    private String cpf;

    @Size(max = 20)
    @Column(length = 20)
    private String telefone;

    @Column(nullable = false)
    private Boolean ativo = true;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "escritorio_id", nullable = false)
    private Escritorio escritorio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Papel papel;

    @Column(name = "trocar_senha_no_proximo_login", nullable = false)
    private Boolean trocarSenhaNoProximoLogin = false;

    @CreationTimestamp
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @UpdateTimestamp
    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;


}
