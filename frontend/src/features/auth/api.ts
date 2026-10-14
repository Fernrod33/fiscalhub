import { useMutation } from '@tanstack/react-query';
import type { LoginRequest, Sessao, SignupRequest, TrocaSenha } from '@/lib/api/tipos';
import { http } from '@/lib/http';
import { definirSessao } from './sessao';

export const useLogin = () => useMutation({
  mutationFn: (dados: LoginRequest) => http.post<Sessao>('/api/auth/login', dados),
  onSuccess: definirSessao,
});

export const useSignup = () => useMutation({
  mutationFn: (dados: Partial<SignupRequest>) => http.post<{ message: string }>('/api/auth/signup', dados),
});

/** A troca invalida o token atual: a resposta traz a sessão nova. */
export const useTrocarSenha = () => useMutation({
  mutationFn: (dados: TrocaSenha) => http.put<Sessao>('/api/usuarios/me/senha', dados),
  onSuccess: definirSessao,
});
