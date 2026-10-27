import { useQuery } from '@tanstack/react-query';
import type { CapacidadeOficial, Conciliacao, Debito, Parcelamento, Pendencia } from '@/lib/api/tipos';
import { http } from '@/lib/http';

/** Sob ['documentos'] para atualizar junto quando uma importação termina ou é reprocessada. */
const chave = (recurso: string, clienteId: number) => ['documentos', recurso, clienteId] as const;

/** Conciliação apurado × declarado × pago (todos os períodos; o filtro de status é na tela). */
export const useConciliacao = (clienteId: number) => useQuery({
  queryKey: chave('conciliacao', clienteId),
  queryFn: () => http.get<Conciliacao>(`/api/clientes/${clienteId}/conciliacao`),
});

/** Um débito por dívida, com as origens de todas as fontes. */
export const useDebitos = (clienteId: number) => useQuery({
  queryKey: chave('debitos', clienteId),
  queryFn: () => http.get<Debito[]>(`/api/clientes/${clienteId}/debitos`),
});

export const useParcelamentos = (clienteId: number) => useQuery({
  queryKey: chave('parcelamentos', clienteId),
  queryFn: () => http.get<Parcelamento[]>(`/api/clientes/${clienteId}/parcelamentos`),
});

export const usePendencias = (clienteId: number) => useQuery({
  queryKey: chave('pendencias', clienteId),
  queryFn: () => http.get<Pendencia[]>(`/api/clientes/${clienteId}/pendencias`),
});

export const useCapacidadeOficial = (clienteId: number) => useQuery({
  queryKey: chave('capacidade-oficial', clienteId),
  queryFn: () => http.get<CapacidadeOficial[]>(`/api/clientes/${clienteId}/capacidade-oficial`),
});
