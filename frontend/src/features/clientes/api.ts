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

export function useExcluirCliente() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => http.delete(`/api/clientes/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: chavesClientes.todos }),
  });
}
