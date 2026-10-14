import { useMutation } from '@tanstack/react-query';
import type { LoginRequest, SignupRequest, TrocaSenha, Usuario } from '@/lib/api/tipos';
import { http } from '@/lib/http';
import { definirSessao } from './sessao';

/** O cookie da sessão vem no Set-Cookie; o corpo traz o usuário. */
export const useLogin = () => useMutation({
  mutationFn: (dados: LoginRequest) => http.post<Usuario>('/api/auth/login', dados),
  onSuccess: definirSessao,
});

export const useSignup = () => useMutation({
  mutationFn: (dados: Partial<SignupRequest>) => http.post<{ message: string }>('/api/auth/signup', dados),
});

/** A troca invalida o token atual: a resposta traz o cookie novo. */
export const useTrocarSenha = () => useMutation({
  mutationFn: (dados: TrocaSenha) => http.put<Usuario>('/api/usuarios/me/senha', dados),
  onSuccess: definirSessao,
});
