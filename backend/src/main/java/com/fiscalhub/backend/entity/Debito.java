package com.fiscalhub.backend.entity;

import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.entity.enums.SituacaoDebito;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Débito fiscal extraído de um documento importado. Nunca é digitado:
 * sempre aponta para o documento de origem e para o trecho dele (registro, página ou linha).
 */
@Entity
@Table(name = "debito")
@Data
@NoArgsConstructor
public class Debito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_origem_id", nullable = false)
    private DocumentoFiscal documentoOrigem;

    @Column(name = "referencia_origem", length = 200)
    private String referenciaOrigem;

    @Enumerated(EnumType.STRING)
    @Column(name = "orgao_credor", nullable = false, length = 20)
    private OrgaoCredor orgaoCredor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoTributo tributo;

    /** Primeiro dia do mês de competência. */
    private LocalDate competencia;

    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    @Column(name = "valor_principal", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorPrincipal;

    /** Multa e juros como informados no documento (opcionais). */
    @Column(name = "valor_multa", precision = 15, scale = 2)
    private BigDecimal valorMulta;

    @Column(name = "valor_juros", precision = 15, scale = 2)
    private BigDecimal valorJuros;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SituacaoDebito situacao;

    @Column(name = "numero_inscricao", length = 60)
    private String numeroInscricao;

    @CreationTimestamp
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;
}
