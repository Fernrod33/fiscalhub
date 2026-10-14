import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import type { Documento, DocumentoRequest } from '@/lib/api/tipos';
import { http } from '@/lib/http';
import { STATUS_FINAL } from '@/lib/rotulos';
import { useClientes } from '@/features/clientes/api';

const chavesDocumentos = { doCliente: (clienteId: number) => ['documentos', clienteId] as const };

/** Enquanto houver documento em processamento, consulta de novo a cada 2 s; para quando todos chegam ao status final. */
const intervaloPolling = (docs: Documento[] | undefined) =>
  docs?.some((d) => !STATUS_FINAL.includes(d.status)) ? 2000 : false;

const buscarDoCliente = (clienteId: number) => http.get<Documento[]>(`/api/clientes/${clienteId}/documentos`);

export const useDocumentos = (clienteId: number) => useQuery({
  queryKey: chavesDocumentos.doCliente(clienteId),
  queryFn: () => buscarDoCliente(clienteId),
  refetchInterval: (q) => intervaloPolling(q.state.data),
});

/**
 * Documentos de todos os clientes do escritório (painel e lista de importações).
 * ponytail: uma requisição por cliente; endpoint agregado no backend quando houver muitos clientes.
 */
export function useDocumentosDoEscritorio() {
  const clientes = useClientes();
  return useQueries({
    queries: (clientes.data ?? []).map((c) => ({
      queryKey: chavesDocumentos.doCliente(c.id),
      queryFn: () => buscarDoCliente(c.id),
      refetchInterval: (q: { state: { data?: Documento[] } }) => intervaloPolling(q.state.data),
    })),
    combine: (resultados) => ({
      carregando: clientes.isPending || resultados.some((r) => r.isPending),
      documentos: resultados.flatMap((r) => r.data ?? []),
      clientes: clientes.data ?? [],
    }),
  });
}

export type MetadadosImportacao = Pick<DocumentoRequest, 'tipo' | 'tributos' | 'periodoInicio' | 'periodoFim'>;

export function useImportarDocumento(clienteId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ arquivo, metadados }: { arquivo: File; metadados: MetadadosImportacao }) => {
      const corpo = new FormData();
      corpo.append('arquivo', arquivo);
      corpo.append('documento', new Blob([JSON.stringify(metadados)], { type: 'application/json' }));
      return http.post<Documento>(`/api/clientes/${clienteId}/documentos`, corpo);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: chavesDocumentos.doCliente(clienteId) }),
  });
}

export function useExcluirDocumento(clienteId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (documentoId: number) => http.delete(`/api/clientes/${clienteId}/documentos/${documentoId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: chavesDocumentos.doCliente(clienteId) }),
  });
}

/** Baixa o arquivo original e dispara o download no navegador. */
export async function baixarDocumento(clienteId: number, documentoId: number) {
  const { blob, nome } = await http.baixar(`/api/clientes/${clienteId}/documentos/${documentoId}/arquivo`);
  const url = URL.createObjectURL(blob);
  const a = Object.assign(document.createElement('a'), { href: url, download: nome });
  a.click();
  URL.revokeObjectURL(url);
}
