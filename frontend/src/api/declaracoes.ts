import client from './client';
import type { Declaracao } from '../types';

export async function getDeclaracoesByEmpresa(empresaId: number): Promise<Declaracao[]> {
  const res = await client.get<Declaracao[]>(`/api/declaracoes/empresa/${empresaId}`);
  return res.data;
}

export async function getDeclaracao(id: number): Promise<Declaracao> {
  const res = await client.get<Declaracao>(`/api/declaracoes/${id}`);
  return res.data;
}

export async function createDeclaracao(
  usuarioId: number,
  empresaId: number,
  data: Omit<Declaracao, 'id' | 'dataCriacao' | 'dataAtualizacao'>
): Promise<Declaracao> {
  const res = await client.post<Declaracao>(
    `/api/declaracoes?usuarioId=${usuarioId}&empresaId=${empresaId}`,
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
