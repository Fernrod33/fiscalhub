import { z } from 'zod';
import type { Cliente, ClienteRequest } from '@/lib/api/tipos';
import { cnpjValido, somenteDigitos } from '@/lib/cnpj';

const opcional = (max: number) => z.string().trim().max(max, `Máximo de ${max} caracteres.`);

/** Validação do cadastro de cliente (mesmas regras do backend: CNPJ válido, CEP com 8 números). */
export const esquemaCliente = z.object({
  razaoSocial: z.string().trim().min(1, 'Informe a razão social.').max(150),
  nomeFantasia: opcional(150),
  cnpj: z.string().trim().min(1, 'Informe o CNPJ.').refine(cnpjValido, 'CNPJ inválido.'),
  inscricaoEstadual: opcional(20),
  inscricaoMunicipal: opcional(20),
  endereco: opcional(200),
  cidade: opcional(100),
  estado: z.string().trim().toUpperCase().refine((v) => v === '' || /^[A-Z]{2}$/.test(v), 'Use a sigla com 2 letras.'),
  cep: z.string().trim().refine((v) => v === '' || somenteDigitos(v).length === 8, 'CEP deve ter 8 números.'),
  telefone: opcional(20),
  ativa: z.boolean(),
});

export type ValoresCliente = z.infer<typeof esquemaCliente>;

export const valoresIniciais = (c?: Cliente): ValoresCliente => ({
  razaoSocial: c?.razaoSocial ?? '',
  nomeFantasia: c?.nomeFantasia ?? '',
  cnpj: c?.cnpj ?? '',
  inscricaoEstadual: c?.inscricaoEstadual ?? '',
  inscricaoMunicipal: c?.inscricaoMunicipal ?? '',
  endereco: c?.endereco ?? '',
  cidade: c?.cidade ?? '',
  estado: c?.estado ?? '',
  cep: c?.cep ?? '',
  telefone: c?.telefone ?? '',
  ativa: c?.ativa ?? true,
});

/** Formulário -> corpo da API (CNPJ e CEP só com dígitos). */
export const paraRequisicao = (v: ValoresCliente): Partial<ClienteRequest> => ({
  ...v,
  cnpj: somenteDigitos(v.cnpj),
  cep: somenteDigitos(v.cep),
});
