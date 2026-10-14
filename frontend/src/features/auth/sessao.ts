/**
 * Sessão do usuário (fora do React, para o cliente HTTP consultar antes do primeiro render).
 * O JWT fica num cookie httpOnly que o JS não lê: o estado vem de GET /api/usuarios/me.
 */
import { useSyncExternalStore } from 'react';
import type { Usuario } from '@/lib/api/tipos';
import { configurarSessao, http } from '@/lib/http';
import { queryClient } from '@/lib/query';

const ouvintes = new Set<() => void>();
let atual: Usuario | null = null;

export function definirSessao(usuario: Usuario | null) {
  atual = usuario;
  if (!usuario) queryClient.clear(); // nada do usuário anterior fica em cache
  ouvintes.forEach((f) => f());
}

/** Na abertura do app: há sessão se o cookie ainda vale (401 ou falha de rede = sem sessão). */
export async function carregarSessao() {
  atual = await http.get<Usuario>('/api/usuarios/me').catch(() => null);
}

/** Logout no servidor (invalida o token e apaga o cookie); a sessão local fecha mesmo se a API falhar. */
export const sair = () => http.post('/api/auth/logout').catch(() => {}).finally(() => definirSessao(null));

export function useSessao(): Usuario | null {
  return useSyncExternalStore(
    (f) => { ouvintes.add(f); return () => ouvintes.delete(f); },
    () => atual,
  );
}

// 401 com sessão aberta (token expirado ou invalidado): fecha só a local; o guard leva ao login preservando a rota.
configurarSessao({ aberta: () => atual !== null, aoExpirar: () => definirSessao(null) });
