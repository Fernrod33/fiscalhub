package com.fiscalhub.backend.entity;

import com.fiscalhub.backend.entity.enums.DocumentoEsfera;
import com.fiscalhub.backend.entity.enums.DocumentoJuridicao;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "documento_fiscal")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DocumentoTipo tipo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentoEsfera esfera;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentoJuridicao juridicao;

    @ElementCollection(targetClass = TipoTributo.class)
    @CollectionTable(name = "documento_fiscal_tributos", joinColumns = @JoinColumn(name = "documento_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tributo", nullable = false, length = 30)
    private Set<TipoTributo> tributos = new HashSet<>();

    @NotNull
    @Column(name = "periodo_inicio", nullable = false)
    private LocalDate periodoInicio;

    @NotNull
    @Column(name = "periodo_fim", nullable = false)
    private LocalDate periodoFim;

    @NotNull
    @Size(max = 255)
    @Column(name = "nome_original_arquivo", nullable = false, length = 255)
    private String nomeOriginalArquivo;

    @NotNull
    @Size(max = 20)
    @Column(nullable = false, length = 20)
    private String extensao;

    @NotNull
    @Size(max = 150)
    @Column(name = "mime_type", nullable = false, length = 150)
    private String mimeType;

    @NotNull
    @Column(name = "tamanho_bytes", nullable = false)
    private Long tamanhoBytes;

    @CreationTimestamp
    @Column(name = "data_importacao", nullable = false, updatable = false)
    private LocalDateTime dataImportacao;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentoStatus status = DocumentoStatus.RECEBIDO;

    @Column(name = "retificacao")
    private Boolean retificacao = false;

    @Size(max = 1000)
    @Column(length = 1000)
    private String observacao;

    /** Chave do arquivo no storage (local ou S3), nunca um caminho informado pelo cliente. */
    @Size(max = 500)
    @Column(name = "chave_armazenamento", nullable = false, length = 500)
    private String chaveArmazenamento;

    /** SHA-256 do conteúdo, calculado durante o upload: o mesmo arquivo não entra duas vezes no cliente. */
    @Column(length = 64)
    private String sha256;

    @UpdateTimestamp
    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;
}
