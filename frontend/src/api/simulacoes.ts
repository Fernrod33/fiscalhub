import client from './client';
import type { SimulacaoRequest, SimulacaoResultado } from '../types';

export async function simularParcelamento(data: SimulacaoRequest): Promise<SimulacaoResultado> {
  const res = await client.post<SimulacaoResultado>('/api/simulacoes/calcular', data);
  return res.data;
}
