import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { Documento, DocumentoStatus, Inconsistencia, PaginaDocumentos, StatusDocumento } from '@/lib/api/tipos';
import { http } from '@/lib/http';
import { STATUS_FINAL } from '@/lib/rotulos';

export type FiltrosDocumentos = { clienteId?: number; status?: DocumentoStatus; tipo?: Documento['tipo']; pagina: number; tamanho: number };
export type ResumoDocumentos = Partial<Record<DocumentoStatus, number>>;

/** Tudo sob ['documentos']: importar, reprocessar ou excluir invalida listas, status e resumo de uma vez. */
export const chavesDocumentos = {
  todos: ['documentos'] as const,
  doCliente: (clienteId: number) => ['documentos', 'cliente', clienteId] as const,
  doEscritorio: (filtros: FiltrosDocumentos) => ['documentos', 'escritorio', filtros] as const,
  resumo: ['documentos', 'resumo'] as const,
  um: (id: number) => ['documentos', 'um', id] as const,
  status: (id: number) => ['documentos', 'status', id] as const,
  inconsistencias: (id: number) => ['documentos', 'inconsistencias', id] as const,
};

const finalizado = (status?: DocumentoStatus) => !!status && STATUS_FINAL.includes(status);

/** Enquanto houver documento em processamento, consulta de novo a cada 2 s; para quando todos chegam ao status final. */
const intervaloPolling = (docs: Documento[] | undefined) => (docs?.some((d) => !finalizado(d.status)) ? 2000 : false);

export const useDocumentos = (clienteId: number) => useQuery({
  queryKey: chavesDocumentos.doCliente(clienteId),
  queryFn: () => http.get<Documento[]>(`/api/clientes/${clienteId}/documentos`),
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
  refetchInterval: (q) => (Object.keys(q.state.data ?? {}).some((s) => !finalizado(s as DocumentoStatus)) ? 2000 : false),
});

export const useDocumento = (documentoId?: number) => useQuery({
  queryKey: chavesDocumentos.um(documentoId ?? 0),
  queryFn: () => http.get<Documento>(`/api/documentos/${documentoId}`),
  enabled: !!documentoId,
});

/**
 * Etapa e progresso do processamento; consulta a cada segundo até parar (terminou ou espera o usuário). Ao parar,
 * o documento é lido de novo (status, período e empresa lidos do arquivo).
 */
export function useStatusDocumento(documentoId?: number) {
  const qc = useQueryClient();
  return useQuery({
    queryKey: chavesDocumentos.status(documentoId ?? 0),
    queryFn: async () => {
      const status = await http.get<StatusDocumento>(`/api/documentos/${documentoId}/status`);
      if (finalizado(status.status)) qc.invalidateQueries({ queryKey: chavesDocumentos.um(documentoId ?? 0) });
      return status;
    },
    enabled: !!documentoId,
    refetchInterval: (q) => (finalizado(q.state.data?.status) ? false : 1000),
  });
}

export const useInconsistencias = (documentoId: number, ativo = true) => useQuery({
  queryKey: chavesDocumentos.inconsistencias(documentoId),
  queryFn: () => http.get<Inconsistencia[]>(`/api/documentos/${documentoId}/inconsistencias`),
  enabled: ativo,
});

/**
 * Envia um arquivo (tipo, período e empresa vêm do conteúdo). Sem clienteId vai para a central do escritório e o
 * cliente sai do arquivo. aoProgresso recebe a fração enviada (0 a 1).
 */
export function enviarDocumento(arquivo: File, clienteId: number | undefined, aoProgresso: (fracao: number) => void) {
  const corpo = new FormData();
  corpo.append('arquivo', arquivo);
  return http.enviarComProgresso<Documento>(clienteId ? `/api/clientes/${clienteId}/documentos` : '/api/documentos', corpo, aoProgresso);
}

/** Depois de qualquer ação num documento: listas, status e resumo voltam a ser consultados. */
function useAtualizarDocumentos() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: chavesDocumentos.todos });
}

export function useExcluirDocumento() {
  const atualizar = useAtualizarDocumentos();
  return useMutation({ mutationFn: (documentoId: number) => http.delete(`/api/documentos/${documentoId}`), onSuccess: atualizar });
}

export function useReprocessarDocumento() {
  const atualizar = useAtualizarDocumentos();
  return useMutation({
    mutationFn: (documentoId: number) => http.post<Documento>(`/api/documentos/${documentoId}/reprocessar`),
    onSuccess: atualizar,
  });
}

/** Sem clienteId: cadastra a empresa lida do arquivo. Com clienteId: associa a um cliente existente. */
export function useConfirmarCliente() {
  const atualizar = useAtualizarDocumentos();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ documentoId, clienteId }: { documentoId: number; clienteId?: number }) =>
      http.post<Documento>(`/api/documentos/${documentoId}/confirmar-cliente`, clienteId ? { clienteId } : {}),
    onSuccess: () => { atualizar(); qc.invalidateQueries({ queryKey: ['clientes'] }); },
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
