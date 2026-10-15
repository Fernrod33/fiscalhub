import { useQuery } from '@tanstack/react-query';
import type { AlertaFiscal, Apuracao, DetalhamentoApuracao, SaldoFiscal } from '@/lib/api/tipos';
import { http } from '@/lib/http';

/** Sob ['documentos'] para atualizar junto quando uma importação termina ou é reprocessada. */
const chaves = {
  apuracoes: (clienteId: number) => ['documentos', 'apuracoes', clienteId] as const,
  detalhe: (clienteId: number, id: number) => ['documentos', 'apuracoes', clienteId, id] as const,
  saldos: (clienteId: number) => ['documentos', 'saldos-fiscais', clienteId] as const,
  alertas: (clienteId: number) => ['documentos', 'alertas-fiscais', clienteId] as const,
};

/** Todas as apurações vigentes do cliente (a matriz agrupa por competência no navegador). */
export const useApuracoes = (clienteId: number) => useQuery({
  queryKey: chaves.apuracoes(clienteId),
  queryFn: () => http.get<Apuracao[]>(`/api/clientes/${clienteId}/apuracoes`),
});

/** Linhas do cálculo e obrigações: só consulta quando aberto. */
export const useDetalheApuracao = (clienteId: number, id: number | undefined) => useQuery({
  queryKey: chaves.detalhe(clienteId, id ?? 0),
  queryFn: () => http.get<DetalhamentoApuracao>(`/api/clientes/${clienteId}/apuracoes/${id}/detalhe`),
  enabled: id != null,
});

export const useSaldosFiscais = (clienteId: number) => useQuery({
  queryKey: chaves.saldos(clienteId),
  queryFn: () => http.get<SaldoFiscal[]>(`/api/clientes/${clienteId}/saldos-fiscais`),
});

export const useAlertasFiscais = (clienteId: number) => useQuery({
  queryKey: chaves.alertas(clienteId),
  queryFn: () => http.get<AlertaFiscal[]>(`/api/clientes/${clienteId}/apuracoes/alertas`),
});
