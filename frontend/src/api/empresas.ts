import client from './client';
import type { Empresa } from '../types';

export async function getMinhasEmpresas(): Promise<Empresa[]> {
  const res = await client.get<Empresa[]>('/api/empresas/minhas');
  return res.data;
}

export async function getEmpresa(id: number): Promise<Empresa> {
  const res = await client.get<Empresa>(`/api/empresas/${id}`);
  return res.data;
}

export async function createEmpresa(data: Omit<Empresa, 'id' | 'dataCriacao' | 'dataAtualizacao'>): Promise<Empresa> {
  const res = await client.post<Empresa>('/api/empresas', data);
  return res.data;
}

export async function updateEmpresa(id: number, data: Partial<Empresa>): Promise<Empresa> {
  const res = await client.put<Empresa>(`/api/empresas/${id}`, data);
  return res.data;
}

export async function deleteEmpresa(id: number): Promise<void> {
  await client.delete(`/api/empresas/${id}`);
}
