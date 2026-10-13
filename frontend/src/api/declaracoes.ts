import client from './client';
import type { Declaracao } from '../types';

export async function getDeclaracoesByCliente(clienteId: number): Promise<Declaracao[]> {
  const res = await client.get<Declaracao[]>(`/api/declaracoes/cliente/${clienteId}`);
  return res.data;
}

export async function createDeclaracao(
  clienteId: number,
  data: Omit<Declaracao, 'id' | 'dataCriacao' | 'dataAtualizacao'>
): Promise<Declaracao> {
  const res = await client.post<Declaracao>(
    `/api/declaracoes?clienteId=${clienteId}`,
    data
  );
  return res.data;
}

export async function updateDeclaracao(id: number, data: Partial<Declaracao>): Promise<Declaracao> {
  const res = await client.put<Declaracao>(`/api/declaracoes/${id}`, data);
  return res.data;
}

export async function deleteDeclaracao(id: number): Promise<void> {
  await client.delete(`/api/declaracoes/${id}`);
}
