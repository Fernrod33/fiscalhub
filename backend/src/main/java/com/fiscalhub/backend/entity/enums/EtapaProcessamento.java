package com.fiscalhub.backend.entity.enums;

/** Etapas do job de processamento, na ordem em que acontecem. */
public enum EtapaProcessamento {
    RECEBIDO,
    LENDO,
    VALIDANDO,
    GRAVANDO,
    CONCLUIDO
}
