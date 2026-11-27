import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { AtivacaoSegundoFator, CodigosRecuperacao, ConfiguracaoSeguranca, SessaoDispositivo, Usuario } from '@/lib/api/tipos';
import { http } from '@/lib/http';
import { definirSessao } from '@/features/auth/sessao';

const SESSOES = ['seguranca', 'sessoes'] as const;
const CONFIGURACAO = ['seguranca', 'escritorio'] as const;

/**
 * Depois de ligar ou desligar o 2º fator, o /me muda (segundoFatorAtivo, segundoFatorPendente). Na ativação, só depois
 * de o usuário guardar os códigos de recuperação: a sessão nova redesenha a tela e eles sumiriam.
 */
export const recarregarSessao = () => http.get<Usuario>('/api/usuarios/me').then(definirSessao);

export const useSessoes = () => useQuery({ queryKey: SESSOES, queryFn: () => http.get<SessaoDispositivo[]>('/api/usuarios/me/sessoes') });

export function useEncerrarSessao() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id?: string) => http.delete(id ? `/api/usuarios/me/sessoes/${id}` : '/api/usuarios/me/sessoes'),
    onSuccess: () => qc.invalidateQueries({ queryKey: SESSOES }),
  });
}

export const useIniciarSegundoFator = () => useMutation({
  mutationFn: () => http.post<AtivacaoSegundoFator>('/api/usuarios/me/segundo-fator/iniciar'),
});

export const useAtivarSegundoFator = () => useMutation({
  mutationFn: (codigo: string) => http.post<CodigosRecuperacao>('/api/usuarios/me/segundo-fator/ativar', { codigo }),
});

export const useDesativarSegundoFator = () => useMutation({
  mutationFn: (senha: string) => http.post('/api/usuarios/me/segundo-fator/desativar', { senha }),
  onSuccess: recarregarSessao,
});

export const useNovosCodigos = () => useMutation({
  mutationFn: (senha: string) => http.post<CodigosRecuperacao>('/api/usuarios/me/segundo-fator/codigos', { senha }),
});

export const useConfiguracaoSeguranca = () => useQuery({
  queryKey: CONFIGURACAO, queryFn: () => http.get<ConfiguracaoSeguranca>('/api/escritorio/seguranca'),
});

export function useAlterarConfiguracaoSeguranca() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (dados: ConfiguracaoSeguranca) => http.put<ConfiguracaoSeguranca>('/api/escritorio/seguranca', dados),
    onSuccess: (c) => qc.setQueryData(CONFIGURACAO, c),
  });
}
