import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { Cliente, ClienteRequest } from '@/lib/api/tipos';
import { http } from '@/lib/http';

export const chavesClientes = {
  todos: ['clientes'] as const,
  um: (id: number) => ['clientes', id] as const,
};

export const useClientes = () => useQuery({
  queryKey: chavesClientes.todos,
  queryFn: () => http.get<Cliente[]>('/api/clientes'),
});

export const useCliente = (id: number) => useQuery({
  queryKey: chavesClientes.um(id),
  queryFn: () => http.get<Cliente>(`/api/clientes/${id}`),
});

export function useSalvarCliente() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, dados }: { id?: number; dados: Partial<ClienteRequest> }) =>
      id ? http.put<Cliente>(`/api/clientes/${id}`, dados) : http.post<Cliente>('/api/clientes', dados),
    onSuccess: () => qc.invalidateQueries({ queryKey: chavesClientes.todos }),
  });
}

/** Exclusão (LGPD, só ADMIN): pede o CNPJ como segunda confirmação; os dados somem depois do prazo de arrependimento. */
export function useSolicitarExclusao() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, confirmacao }: { id: number; confirmacao: string }) =>
      http.post<Cliente>(`/api/clientes/${id}/exclusao`, { confirmacao }),
    onSuccess: () => qc.invalidateQueries({ queryKey: chavesClientes.todos }),
  });
}

export function useCancelarExclusao() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => http.delete(`/api/clientes/${id}/exclusao`),
    onSuccess: () => qc.invalidateQueries({ queryKey: chavesClientes.todos }),
  });
}

/** Portabilidade (só ADMIN): ZIP com os arquivos originais, os relatórios emitidos e os dados em JSON. */
export async function exportarCliente(id: number) {
  const { blob, nome } = await http.baixar(`/api/clientes/${id}/exportacao`);
  const url = URL.createObjectURL(blob);
  const a = Object.assign(document.createElement('a'), { href: url, download: nome });
  a.click();
  URL.revokeObjectURL(url);
}
