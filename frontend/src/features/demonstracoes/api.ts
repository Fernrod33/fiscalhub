import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { Comparativo, Escrituracao, OrigemValor } from '@/lib/api/tipos';
import { http } from '@/lib/http';

export type TipoDemonstracao = 'BP' | 'DRE';
export type OrigemDemonstracao = 'REFERENCIAL' | 'PUBLICADA';

/** Sob ['documentos'] para atualizar junto quando uma importação termina ou é reprocessada. */
const chaves = {
  escrituracoes: (clienteId: number) => ['documentos', 'escrituracoes', clienteId] as const,
  comparativo: (clienteId: number, tipo: string, origem: string, exercicios: number[]) =>
    ['documentos', 'demonstracoes', clienteId, tipo, origem, exercicios] as const,
  origem: (clienteId: number, linhaId: number) => ['documentos', 'origem', clienteId, linhaId] as const,
};

export const useEscrituracoes = (clienteId: number) => useQuery({
  queryKey: chaves.escrituracoes(clienteId),
  queryFn: () => http.get<Escrituracao[]>(`/api/clientes/${clienteId}/escrituracoes`),
});

/** Comparativo lado a lado (AH e AV vêm calculadas do backend). Sem exercícios: todos os disponíveis. */
export const useComparativo = (clienteId: number, tipo: TipoDemonstracao, origem: OrigemDemonstracao, exercicios: number[]) => useQuery({
  queryKey: chaves.comparativo(clienteId, tipo, origem, exercicios),
  queryFn: () => http.get<Comparativo>(`/api/clientes/${clienteId}/demonstracoes?tipo=${tipo}&origem=${origem}`
    + (exercicios.length ? `&exercicios=${exercicios.join(',')}` : '')),
  placeholderData: keepPreviousData,
});

/** De onde vem um valor: só consulta quando o popover abre. */
export const useOrigem = (clienteId: number, linhaId: number, ativo: boolean) => useQuery({
  queryKey: chaves.origem(clienteId, linhaId),
  queryFn: () => http.get<OrigemValor>(`/api/clientes/${clienteId}/demonstracoes/linhas/${linhaId}/origem`),
  enabled: ativo,
  staleTime: Infinity,
});
