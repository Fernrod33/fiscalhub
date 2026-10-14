import { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { Documento, DocumentoStatus, PaginaDocumentos } from '@/lib/api/tipos';
import { http } from '@/lib/http';
import { STATUS_FINAL } from '@/lib/rotulos';

export type FiltrosDocumentos = { clienteId?: number; status?: DocumentoStatus; tipo?: Documento['tipo']; pagina: number; tamanho: number };
export type ResumoDocumentos = Partial<Record<DocumentoStatus, number>>;

/** Tudo sob ['documentos']: importar ou excluir invalida as listas do cliente, do escritório e o resumo de uma vez. */
const chavesDocumentos = {
  todos: ['documentos'] as const,
  doCliente: (clienteId: number) => ['documentos', 'cliente', clienteId] as const,
  doEscritorio: (filtros: FiltrosDocumentos) => ['documentos', 'escritorio', filtros] as const,
  resumo: ['documentos', 'resumo'] as const,
};

/** Enquanto houver documento em processamento, consulta de novo a cada 2 s; para quando todos chegam ao status final. */
const intervaloPolling = (docs: Documento[] | undefined) =>
  docs?.some((d) => !STATUS_FINAL.includes(d.status)) ? 2000 : false;

const buscarDoCliente = (clienteId: number) => http.get<Documento[]>(`/api/clientes/${clienteId}/documentos`);

export const useDocumentos = (clienteId: number) => useQuery({
  queryKey: chavesDocumentos.doCliente(clienteId),
  queryFn: () => buscarDoCliente(clienteId),
  refetchInterval: (q) => intervaloPolling(q.state.data),
});

/** Uma página dos documentos de todos os clientes do escritório, com filtros (GET /api/documentos). */
export function useDocumentosDoEscritorio(filtros: FiltrosDocumentos) {
  const busca = new URLSearchParams();
  Object.entries(filtros).forEach(([chave, valor]) => valor !== undefined && busca.set(chave, String(valor)));
  return useQuery({
    queryKey: chavesDocumentos.doEscritorio(filtros),
    queryFn: () => http.get<PaginaDocumentos>(`/api/documentos?${busca}`),
    placeholderData: keepPreviousData,
    refetchInterval: (q) => intervaloPolling(q.state.data?.content),
  });
}

/** Quantidade de documentos do escritório por status; acompanha enquanto houver algum em andamento. */
export const useResumoDocumentos = () => useQuery({
  queryKey: chavesDocumentos.resumo,
  queryFn: () => http.get<ResumoDocumentos>('/api/documentos/resumo'),
  refetchInterval: (q) => Object.keys(q.state.data ?? {}).some((s) => !STATUS_FINAL.includes(s as DocumentoStatus)) ? 2000 : false,
});

/**
 * Envia o arquivo; o tipo, o período e a empresa vêm do conteúdo. Sem clienteId, vai para a central do escritório
 * (o cliente sai do arquivo). `progresso` é a fração já enviada (0 a 1).
 */
export function useImportarDocumento(clienteId?: number) {
  const qc = useQueryClient();
  const [progresso, setProgresso] = useState(0);
  const mutacao = useMutation({
    mutationFn: (arquivo: File) => {
      const corpo = new FormData();
      corpo.append('arquivo', arquivo);
      setProgresso(0);
      return http.enviarComProgresso<Documento>(clienteId ? `/api/clientes/${clienteId}/documentos` : '/api/documentos', corpo, setProgresso);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: chavesDocumentos.todos }),
  });
  return { ...mutacao, progresso };
}

export function useExcluirDocumento() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (documentoId: number) => http.delete(`/api/documentos/${documentoId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: chavesDocumentos.todos }),
  });
}

/** Baixa o arquivo original e dispara o download no navegador. */
export async function baixarDocumento(documentoId: number) {
  const { blob, nome } = await http.baixar(`/api/documentos/${documentoId}/arquivo`);
  const url = URL.createObjectURL(blob);
  const a = Object.assign(document.createElement('a'), { href: url, download: nome });
  a.click();
  URL.revokeObjectURL(url);
}
