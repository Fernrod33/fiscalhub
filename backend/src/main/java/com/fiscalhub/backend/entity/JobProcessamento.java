package com.fiscalhub.backend.entity;

import com.fiscalhub.backend.entity.enums.EtapaProcessamento;
import com.fiscalhub.backend.entity.enums.StatusJob;
import com.fiscalhub.backend.entity.enums.TipoJob;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_processamento")
@Data
@NoArgsConstructor
public class JobProcessamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoJob tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_id", nullable = false)
    private DocumentoFiscal documento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusJob status = StatusJob.PENDENTE;

    @Column(nullable = false)
    private Integer tentativas = 0;

    /** Gravados só pelo RegistroProgresso (transação própria), para a tela acompanhar durante o processamento. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EtapaProcessamento etapa = EtapaProcessamento.RECEBIDO;

    @Column(nullable = false)
    private Integer progresso = 0;

    @Column(length = 1000)
    private String mensagem;

    private LocalDateTime inicio;

    private LocalDateTime fim;

    @CreationTimestamp
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;
}
