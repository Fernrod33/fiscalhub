package com.fiscalhub.backend.entity.enums;

/** Situação do débito no órgão. EM_ABERTO e INSCRITO_DIVIDA_ATIVA (sem negociação) impedem a certidão. */
public enum SituacaoDebito {
    EM_ABERTO,
    INSCRITO_DIVIDA_ATIVA,
    PARCELADO,
    SUSPENSO,
    /** Inscrição em transação ou outra negociação em dia. */
    NEGOCIADO;

    public boolean impedeCertidao() {
        return this == EM_ABERTO || this == INSCRITO_DIVIDA_ATIVA;
    }
}
