import { useParams } from 'react-router-dom';

/** Abas da área do cliente (cada uma é uma rota filha de /clientes/:clienteId). */
export const ABAS_CLIENTE = [
  ['visao-geral', 'Visão geral'],
  ['importacoes', 'Importações'],
  ['demonstracoes', 'Demonstrações'],
  ['tributos', 'Tributos'],
  ['capacidade', 'Capacidade'],
  ['simulacoes', 'Simulações'],
  ['pontos-de-atencao', 'Pontos de atenção'],
  ['relatorios', 'Relatórios'],
] as const;

/** Id do cliente da rota /clientes/:clienteId/... */
export const useClienteId = () => Number(useParams().clienteId);
