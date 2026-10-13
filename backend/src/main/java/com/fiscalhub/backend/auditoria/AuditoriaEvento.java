package com.fiscalhub.backend.auditoria;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/** Evento de auditoria: só inserção (@Immutable aqui, trigger no banco). Sem dados sensíveis em detalhes. */
@Entity
@Immutable
@Table(name = "auditoria_evento")
@Getter
@Setter
@NoArgsConstructor
public class AuditoriaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "escritorio_id")
    private Long escritorioId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private AcaoAuditoria acao;

    @Column(name = "recurso_tipo", length = 40)
    private String recursoTipo;

    @Column(name = "recurso_id")
    private Long recursoId;

    @CreationTimestamp
    @Column(name = "data_hora", nullable = false, updatable = false)
    private LocalDateTime dataHora;

    @Column(length = 45)
    private String ip;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> detalhes;
}
