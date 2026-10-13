package com.fiscalhub.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "cliente")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String razaoSocial;

    @Size(max = 150)
    @Column(length = 150)
    private String nomeFantasia;

    @NotBlank
    @Size(max = 18)
    @Column(nullable = false, length = 18)
    private String cnpj;

    @Size(max = 20)
    @Column(length = 20)
    private String inscricaoEstadual;

    @Size(max = 20)
    @Column(length = 20)
    private String inscricaoMunicipal;

    @Size(max = 200)
    @Column(length = 200)
    private String endereco;

    @Size(max = 100)
    @Column(length = 100)
    private String cidade;

    @Size(max = 2)
    @Column(length = 2)
    private String estado;

    @Size(max = 10)
    @Column(length = 10)
    private String cep;

    @Size(max = 20)
    @Column(length = 20)
    private String telefone;

    @Column(nullable = false)
    private Boolean ativa = true;

    @CreationTimestamp
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @UpdateTimestamp
    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "escritorio_id", nullable = false)
    private Escritorio escritorio;

    /** Quem cadastrou (só registro): o cliente pertence ao escritório. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por_id", nullable = false)
    private Usuario criadoPor;

}
