package com.fiscalhub.backend.entity.enums;

/** Papel do usuário no escritório. */
public enum Papel {
    ADMIN,      // sócio ou gestor: tudo, inclusive gerenciar usuários
    CONSULTOR,  // tudo o que envolve clientes, sem gerenciar usuários
    ASSISTENTE  // lê e importa arquivos; não exclui nem gerencia usuários
}
