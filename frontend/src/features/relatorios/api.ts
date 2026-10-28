import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ExercicioCarga, Identidade, PedidoRelatorio, RelatorioEmitido } from '@/lib/api/tipos';
import { http } from '@/lib/http';

const chave = (clienteId: number) => ['relatorios', clienteId] as const;

export const useRelatoriosEmitidos = (clienteId: number) => useQuery({
  queryKey: chave(clienteId),
  queryFn: () => http.get<RelatorioEmitido[]>(`/api/clientes/${clienteId}/relatorios`),
});

/** HTML da prévia gerado pelo backend (o mesmo template do PDF); nada é gravado. */
export const usePrevia = (clienteId: number, pedido: PedidoRelatorio) => useQuery({
  queryKey: ['relatorios', clienteId, 'previa', pedido],
  queryFn: () => http.texto(`/api/clientes/${clienteId}/relatorios/previa`, pedido),
  placeholderData: (anterior) => anterior,
});

export function useEmitirRelatorio(clienteId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (pedido: PedidoRelatorio) => http.post<RelatorioEmitido>(`/api/clientes/${clienteId}/relatorios`, pedido),
    onSuccess: () => qc.invalidateQueries({ queryKey: chave(clienteId) }),
  });
}

/** Baixa o arquivo gravado na emissão (sempre o mesmo). */
export async function baixarRelatorio(clienteId: number, relatorioId: number, formato: 'pdf' | 'xlsx') {
  const { blob, nome } = await http.baixar(`/api/clientes/${clienteId}/relatorios/${relatorioId}/${formato}`);
  const url = URL.createObjectURL(blob);
  Object.assign(document.createElement('a'), { href: url, download: nome }).click();
  URL.revokeObjectURL(url);
}

/** Sob ['documentos'] para atualizar junto quando uma importação termina. */
export const useCargaTributaria = (clienteId: number) => useQuery({
  queryKey: ['documentos', 'carga-tributaria', clienteId],
  queryFn: () => http.get<ExercicioCarga[]>(`/api/clientes/${clienteId}/carga-tributaria`),
});

export const CHAVE_IDENTIDADE = ['escritorio', 'identidade'] as const;

export const useIdentidade = () => useQuery({
  queryKey: CHAVE_IDENTIDADE,
  queryFn: () => http.get<Identidade>('/api/escritorio/identidade'),
});
