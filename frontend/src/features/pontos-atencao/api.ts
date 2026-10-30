import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ConfiguracaoRegra, PainelEscritorio, PontoAtencao, RegraNaoAvaliada, ResultadoAvaliacao } from '@/lib/api/tipos';
import { http } from '@/lib/http';

const base = (clienteId: number) => `/api/clientes/${clienteId}/pontos-atencao`;

/** Sob ['documentos'] para atualizar junto quando uma importação termina (a reavaliação roda depois de cada uma). */
export const usePontos = (clienteId: number) => useQuery({
  queryKey: ['documentos', 'pontos', clienteId],
  queryFn: () => http.get<PontoAtencao[]>(base(clienteId)),
});

export const usePonto = (clienteId: number, id: number | undefined) => useQuery({
  queryKey: ['documentos', 'ponto', clienteId, id],
  queryFn: () => http.get<PontoAtencao>(`${base(clienteId)}/${id}`),
  enabled: id != null,
});

export const useNaoAvaliadas = (clienteId: number) => useQuery({
  queryKey: ['documentos', 'pontos-nao-avaliadas', clienteId],
  queryFn: () => http.get<RegraNaoAvaliada[]>(`${base(clienteId)}/nao-avaliadas`),
});

export const usePainel = () => useQuery({
  queryKey: ['documentos', 'painel-escritorio'],
  queryFn: () => http.get<PainelEscritorio>('/api/escritorio/pontos-atencao/resumo'),
});

export const useConfiguracaoRegras = () => useQuery({
  queryKey: ['regras-pontos'],
  queryFn: () => http.get<ConfiguracaoRegra[]>('/api/escritorio/pontos-atencao/regras'),
});

function useInvalidar<T, R>(fn: (v: T) => Promise<R>, chave: string) {
  const qc = useQueryClient();
  return useMutation({ mutationFn: fn, onSuccess: () => qc.invalidateQueries({ queryKey: [chave] }) });
}

export const useReavaliar = (clienteId: number) =>
  useInvalidar(() => http.post<ResultadoAvaliacao>(`${base(clienteId)}/reavaliar`), 'documentos');

export const useMudarEstado = (clienteId: number) =>
  useInvalidar((p: { id: number; estado: string; comentario?: string }) =>
    http.patch<PontoAtencao>(`${base(clienteId)}/${p.id}/estado`, { estado: p.estado, comentario: p.comentario }), 'documentos');

export const useAlterarRegra = () =>
  useInvalidar((p: { codigo: string; parametros: Record<string, unknown>; severidade?: string }) =>
    http.put<ConfiguracaoRegra>(`/api/escritorio/pontos-atencao/regras/${p.codigo}`, { parametros: p.parametros, severidade: p.severidade }), 'regras-pontos');
