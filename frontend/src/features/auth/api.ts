import { useMutation } from '@tanstack/react-query';
import type { LoginRequest, RespostaLogin, SegundoFatorLogin, SignupRequest, TrocaSenha, Usuario } from '@/lib/api/tipos';
import { http } from '@/lib/http';
import { definirSessao } from './sessao';

/**
 * O cookie da sessão vem no Set-Cookie; o corpo traz o usuário. Com o 2º fator ativo, só o desafio: a sessão abre
 * depois do código (useSegundoFator).
 */
export const useLogin = () => useMutation({
  mutationFn: (dados: LoginRequest) => http.post<RespostaLogin>('/api/auth/login', dados),
  onSuccess: (r) => { if (r.usuario) definirSessao(r.usuario); },
});

export const useSegundoFator = () => useMutation({
  mutationFn: (dados: SegundoFatorLogin) => http.post<RespostaLogin>('/api/auth/login/segundo-fator', dados),
  onSuccess: (r) => { if (r.usuario) definirSessao(r.usuario); },
});

/** Mesma regra do backend (SenhaForte): de 12 a 64 caracteres; a lista de vazadas é conferida lá. */
export const SENHA_MINIMA = 12;
export const SENHA_MAXIMA = 64;
export const DICA_SENHA = `De ${SENHA_MINIMA} a ${SENHA_MAXIMA} caracteres. Uma frase com palavras soltas é mais forte e mais fácil de lembrar.`;

export const useSignup = () => useMutation({
  mutationFn: (dados: Partial<SignupRequest>) => http.post<{ message: string }>('/api/auth/signup', dados),
});

/** A troca invalida o token atual: a resposta traz o cookie novo. */
export const useTrocarSenha = () => useMutation({
  mutationFn: (dados: TrocaSenha) => http.put<Usuario>('/api/usuarios/me/senha', dados),
  onSuccess: definirSessao,
});
