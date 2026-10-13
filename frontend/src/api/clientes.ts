import client from './client';
import type { Cliente } from '../types';

export async function getClientes(): Promise<Cliente[]> {
  const res = await client.get<Cliente[]>('/api/clientes');
  return res.data;
}

export async function getCliente(id: number): Promise<Cliente> {
  const res = await client.get<Cliente>(`/api/clientes/${id}`);
  return res.data;
}

export async function createCliente(data: Omit<Cliente, 'id' | 'dataCriacao' | 'dataAtualizacao'>): Promise<Cliente> {
  const res = await client.post<Cliente>('/api/clientes', data);
  return res.data;
}

export async function updateCliente(id: number, data: Partial<Cliente>): Promise<Cliente> {
  const res = await client.put<Cliente>(`/api/clientes/${id}`, data);
  return res.data;
}

export async function deleteCliente(id: number): Promise<void> {
  await client.delete(`/api/clientes/${id}`);
}
