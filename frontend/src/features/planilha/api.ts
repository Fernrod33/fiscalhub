import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ContaReferencial, DeparaPlanilha, PedidoMapeamento, PreviaPlanilha } from '@/lib/api/tipos';
import { http } from '@/lib/http';
import { chavesDocumentos } from '@/features/documentos/api';

const chaves = {
  previa: (id: number, aba?: string) => ['planilha', id, 'previa', aba ?? ''] as const,
  depara: (id: number) => ['planilha', id, 'depara'] as const,
  busca: (plano: string, exercicio: number, busca: string) => ['referencial', plano, exercicio, busca] as const,
};

export const usePreviaPlanilha = (documentoId: number, aba?: string) => useQuery({
  queryKey: chaves.previa(documentoId, aba),
  queryFn: () => http.get<PreviaPlanilha>(`/api/documentos/${documentoId}/planilha${aba ? `?aba=${encodeURIComponent(aba)}` : ''}`),
});

/** Salva colunas + período (o mapeamento fica para o cliente) e processa de novo. */
export function useMapearPlanilha(documentoId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (pedido: PedidoMapeamento) => http.post(`/api/documentos/${documentoId}/planilha/mapeamento`, pedido),
    onSuccess: () => qc.invalidateQueries({ queryKey: chavesDocumentos.todos }),
  });
}

export const useDepara = (documentoId: number, ativo: boolean) => useQuery({
  queryKey: chaves.depara(documentoId),
  queryFn: () => http.get<DeparaPlanilha>(`/api/documentos/${documentoId}/planilha/depara`),
  enabled: ativo,
});

/** Grava as associações (null = desassociar) e reprocessa o balancete com elas. */
export function useAssociar(documentoId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (associacoes: { codigoConta: string; codigoReferencial: string | null }[]) =>
      http.put(`/api/documentos/${documentoId}/planilha/depara`, associacoes),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: chavesDocumentos.todos });
      qc.invalidateQueries({ queryKey: chaves.depara(documentoId) });
    },
  });
}

/** Contas referenciais analíticas por código ou descrição (até 50). */
export const useBuscaReferencial = (plano: string, exercicio: number, busca: string) => useQuery({
  queryKey: chaves.busca(plano, exercicio, busca),
  queryFn: () => http.get<ContaReferencial[]>(`/api/referencial/contas?plano=${plano}&exercicio=${exercicio}&busca=${encodeURIComponent(busca)}`),
  placeholderData: keepPreviousData,
  staleTime: Infinity, // tabela oficial: não muda durante o uso
});
