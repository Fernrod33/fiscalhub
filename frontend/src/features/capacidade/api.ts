import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { Capacidade, Dfc, ParametrosEscritorio, ResultadoCapacidade, ResumoCapacidade } from '@/lib/api/tipos';
import { http } from '@/lib/http';

/** Sob ['documentos'] para atualizar junto quando uma importação termina. */
export const useCapacidade = (clienteId: number) => useQuery({
  queryKey: ['documentos', 'capacidade', clienteId],
  queryFn: () => http.get<Capacidade>(`/api/clientes/${clienteId}/capacidade`),
});

export const useResumoCapacidade = (clienteId: number) => useQuery({
  queryKey: ['documentos', 'capacidade-resumo', clienteId],
  queryFn: () => http.get<ResumoCapacidade>(`/api/clientes/${clienteId}/capacidade/resumo`),
});

/** Simulação do PR: recalcula no backend com CDAs quitadas ou parceladas; nada é gravado. */
export const useSimulacaoPr = (clienteId: number) => useMutation({
  mutationFn: (s: { quitar: string[]; parcelar: string[] }) =>
    http.post<ResultadoCapacidade>(`/api/clientes/${clienteId}/capacidade/pr/simulacao`, s),
});

export const useDfcExercicios = (clienteId: number) => useQuery({
  queryKey: ['documentos', 'dfc-exercicios', clienteId],
  queryFn: () => http.get<number[]>(`/api/clientes/${clienteId}/dfc/exercicios`),
});

export const useDfc = (clienteId: number, exercicio: number | undefined) => useQuery({
  queryKey: ['documentos', 'dfc', clienteId, exercicio],
  queryFn: () => http.get<Dfc>(`/api/clientes/${clienteId}/dfc?exercicio=${exercicio}`),
  enabled: exercicio != null,
});

export function useSalvarParametros() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (p: ParametrosEscritorio) => http.put<ParametrosEscritorio>('/api/escritorio/parametros-capacidade', p),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['documentos'] }),
  });
}
