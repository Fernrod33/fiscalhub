package com.fiscalhub.backend.entity;

import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
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
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "escritorio_id", nullable = false)
    private Escritorio escritorio;

    /** Pode ficar vazio até o processamento ler a empresa no arquivo (upload no nível do escritório). */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DocumentoTipo tipo;

    /** Tipo informado no upload: só sugestão, vale o detectado no conteúdo. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_informado", length = 40)
    private DocumentoTipo tipoInformado;

    /** Período lido do arquivo (ou escolhido no assistente da planilha). */
    @Column(name = "periodo_inicio")
    private LocalDate periodoInicio;

    @Column(name = "periodo_fim")
    private LocalDate periodoFim;

    /** Empresa lida do arquivo: sugestão de cadastro quando não há cliente com esse CNPJ. */
    @Column(name = "cnpj_arquivo", length = 14)
    private String cnpjArquivo;

    @Column(name = "nome_empresa_arquivo", length = 255)
    private String nomeEmpresaArquivo;

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
