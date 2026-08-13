export interface Usuario {
  id: number;
  nome: string;
  email: string;
  cpf: string;
  telefone: string;
  ativo: boolean;
  dataCriacao: string;
  dataAtualizacao: string;
}

export interface Empresa {
  id: number;
  razaoSocial: string;
  nomeFantasia: string;
  cnpj: string;
  inscricaoEstadual?: string;
  inscricaoMunicipal?: string;
  endereco: string;
  cidade: string;
  estado: string;
  cep: string;
  telefone: string;
  ativa: boolean;
  dataCriacao: string;
  dataAtualizacao: string;
}

export type TipoDeclaracao = 'ICMS' | 'ISS' | 'PIS' | 'COFINS' | 'IRPJ' | 'CSLL' | 'OUTRO';
export type StatusDeclaracao = 'PENDENTE' | 'ENVIADA' | 'PROCESSANDO' | 'APROVADA' | 'REJEITADA' | 'PAGO';

export interface Declaracao {
  id: number;
  tipo: TipoDeclaracao;
  periodoInicio: string;
  periodoFim: string;
  valorTotal: number;
  valorImposto: number;
  status: StatusDeclaracao;
  dataVencimento: string;
  dataPagamento?: string | null;
  observacoes?: string;
  dataCriacao: string;
  dataAtualizacao: string;
}

export interface Parcela {
  numero: number;
  dataVencimento: string;
  valorPrincipal: number;
  valorJuros: number;
  valorTotal: number;
}

export interface SimulacaoResultado {
  valorOriginal: number;
  numeroParcelas: number;
  taxaJurosMensal: number;
  valorTotalComJuros: number;
  valorTotalJuros: number;
  parcelas: Parcela[];
}

export interface SimulacaoRequest {
  valorDevido: number;
  numeroParcelas: number;
  taxaJurosMensal: number;
  dataInicio: string;
}

export interface AuthUser {
  id: number;
  email: string;
  nome: string;
  token: string;
}

export interface LoginRequest {
  email: string;
  senha: string;
}

export interface SignupRequest {
  nome: string;
  email: string;
  senha: string;
  cpf: string;
  telefone: string;
}
