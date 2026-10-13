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

export type DocumentoEsfera = 'FEDERAL' | 'ESTADUAL' | 'CONTABIL';
export type DocumentoJuridicao = 'UNIAO' | 'PARANA' | 'NAO_APLICAVEL';
export type DocumentoStatus = 'RECEBIDO' | 'PROCESSANDO' | 'PROCESSADO' | 'PROCESSADO_COM_ALERTAS' | 'ERRO';
export type DocumentoTipo = 'IRPJ' | 'CSLL' | 'PIS' | 'COFINS' | 'IPI' | 'DCTF' | 'DCTF_WEB' | 'EFD_CONTRIBUICOES' | 'ECD' | 'ECF' | 'EFD_ICMS_IPI' | 'ICMS' | 'DEMONSTRACAO_CONTABIL' | 'OUTRO';
export type TributoTipo = 'IRPJ' | 'CSLL' | 'PIS' | 'COFINS' | 'IPI' | 'ICMS' | 'OUTROS';

export interface DocumentoFiscal {
  id: number;
  empresaId: number;
  usuarioId: number;
  tipo: DocumentoTipo;
  esfera: DocumentoEsfera;
  juridicao: DocumentoJuridicao;
  tributos: TributoTipo[];
  periodoInicio: string;
  periodoFim: string;
  nomeOriginalArquivo: string;
  nomeArquivoInterno: string;
  extensao: string;
  mimeType: string;
  tamanhoBytes: number;
  dataImportacao: string;
  status: DocumentoStatus;
  retificacao: boolean;
  observacao?: string;
  documentoOriginalId?: number | null;
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
