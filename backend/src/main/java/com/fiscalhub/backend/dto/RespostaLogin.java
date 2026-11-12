package com.fiscalhub.backend.dto;

/**
 * Resposta do login: o usuário (sessão aberta, cookie no Set-Cookie) ou, com o segundo fator ativo, só o desafio (vale
 * 5 minutos) para POST /api/auth/login/segundo-fator.
 */
public record RespostaLogin(UsuarioResponseDTO usuario, String desafio) {
}
