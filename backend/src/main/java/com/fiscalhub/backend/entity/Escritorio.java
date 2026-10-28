package com.fiscalhub.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "escritorio")
@Data
@NoArgsConstructor
public class Escritorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    /** CNPJ ou CPF, só dígitos (opcional). */
    @Column(length = 14)
    private String documento;

    @Column(nullable = false)
    private Boolean ativo = true;

    /** Identidade nos relatórios (Configurações → Escritório). Sem nome de exibição, vale o nome. */
    @Column(name = "nome_exibicao", length = 150)
    private String nomeExibicao;

    /** Chave do logotipo (PNG redimensionado) no storage; cada upload ganha uma chave nova (relatórios emitidos apontam a antiga). */
    @Column(name = "logo_chave", length = 300)
    private String logoChave;

    @Column(name = "cor_principal", length = 7)
    private String corPrincipal;

    @Column(name = "rodape_relatorio", length = 500)
    private String rodapeRelatorio;

    @Column(name = "responsavel_nome", length = 150)
    private String responsavelNome;

    @Column(name = "responsavel_registro", length = 60)
    private String responsavelRegistro;

    @CreationTimestamp
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @UpdateTimestamp
    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;
}
