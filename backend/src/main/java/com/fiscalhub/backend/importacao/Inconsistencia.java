package com.fiscalhub.backend.importacao;

import com.fiscalhub.backend.entity.DocumentoFiscal;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Problema encontrado ao importar um arquivo, com o registro e a linha de origem quando houver. */
@Entity
@Table(name = "inconsistencia_importacao")
@Data
@NoArgsConstructor
public class Inconsistencia {

    public enum Severidade { ERRO, ALERTA, INFO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_id", nullable = false)
    private DocumentoFiscal documento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severidade severidade;

    @Column(length = 40)
    private String registro;

    private Long linha;

    @Column(nullable = false, length = 80)
    private String regra;

    @Column(nullable = false, length = 2000)
    private String mensagem;

    public Inconsistencia(Severidade severidade, String registro, Long linha, String regra, String mensagem) {
        this.severidade = severidade;
        this.registro = registro;
        this.linha = linha;
        this.regra = regra;
        this.mensagem = mensagem.length() > 2000 ? mensagem.substring(0, 1997) + "..." : mensagem;
    }
}
