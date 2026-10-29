import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { PedidoParcelamento, Simulacao, SimulacaoSalva } from '@/lib/api/tipos';
import { http } from '@/lib/http';

const base = (clienteId: number) => `/api/clientes/${clienteId}/simulacoes`;

/** Simula sem gravar (todos os papéis). */
export const useSimular = (clienteId: number) => useMutation({
  mutationFn: (p: PedidoParcelamento) => http.post<Simulacao>(`${base(clienteId)}/calcular`, p),
});

/** Sob ['documentos'] para atualizar junto quando uma importação termina. */
export const useSimulacoesSalvas = (clienteId: number) => useQuery({
  queryKey: ['documentos', 'simulacoes', clienteId],
  queryFn: () => http.get<SimulacaoSalva[]>(base(clienteId)),
});

export const useSimulacao = (clienteId: number, id: number | undefined) => useQuery({
  queryKey: ['documentos', 'simulacao', clienteId, id],
  queryFn: () => http.get<Simulacao>(`${base(clienteId)}/${id}`),
  enabled: id != null,
});

/** A simulação salva mais recente com recomendação; null quando não há (204). */
export const useMelhorSimulacao = (clienteId: number) => useQuery({
  queryKey: ['documentos', 'simulacao-melhor', clienteId],
  queryFn: async () => (await http.get<SimulacaoSalva | undefined>(`${base(clienteId)}/melhor`)) ?? null,
});

function useAlterar<T>(fn: (v: T) => Promise<unknown>) {
  const qc = useQueryClient();
  return useMutation({ mutationFn: fn, onSuccess: () => qc.invalidateQueries({ queryKey: ['documentos'] }) });
}

export const useSalvarSimulacao = (clienteId: number) =>
  useAlterar((p: PedidoParcelamento) => http.post<Simulacao>(base(clienteId), p));

export const useRecalcular = (clienteId: number) =>
  useAlterar((id: number) => http.post<Simulacao>(`${base(clienteId)}/${id}/recalcular`));

export const useExcluirSimulacao = (clienteId: number) =>
  useAlterar((id: number) => http.delete(`${base(clienteId)}/${id}`));
