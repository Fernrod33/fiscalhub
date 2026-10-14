/**
 * Sessão do usuário (fora do React, para o cliente HTTP ler o token antes do primeiro render).
 * ponytail: token em localStorage (exposto a XSS); trocar por cookie httpOnly quando o backend emitir o cookie.
 */
import { useSyncExternalStore } from 'react';
import type { Sessao } from '@/lib/api/tipos';
import { configurarSessao } from '@/lib/http';
import { queryClient } from '@/lib/query';

const CHAVE = 'fiscalhub.sessao';
const ouvintes = new Set<() => void>();

function ler(): Sessao | null {
  try {
    return JSON.parse(localStorage.getItem(CHAVE) ?? 'null');
  } catch {
    return null;
  }
}

let atual: Sessao | null = ler();

export function definirSessao(sessao: Sessao | null) {
  atual = sessao;
  try {
    if (sessao) localStorage.setItem(CHAVE, JSON.stringify(sessao));
    else localStorage.removeItem(CHAVE);
  } catch {
    // storage indisponível: a sessão vale só nesta aba
  }
  if (!sessao) queryClient.clear(); // nada do usuário anterior fica em cache
  ouvintes.forEach((f) => f());
}

export const sair = () => definirSessao(null);

export function useSessao(): Sessao | null {
  return useSyncExternalStore(
    (f) => { ouvintes.add(f); return () => ouvintes.delete(f); },
    () => atual,
  );
}

// 401 com sessão aberta (token expirado ou invalidado): encerra; o guard de rota leva ao login preservando a rota.
configurarSessao({ obterToken: () => atual?.token ?? null, aoExpirar: sair });
