/** Resposta de GET /api/usuarios/me (nunca inclui senha). */
export interface Usuario {
  id: number;
  nome: string;
  email: string;
  cpf: string;
  telefone: string;
  ativo: boolean;
  papel: Papel;
  trocarSenhaNoProximoLogin: boolean;
  dataCriacao: string;
}

export interface Cliente {
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

export type DocumentoEsfera = 'FEDERAL' | 'ESTADUAL' | 'CONTABIL';
export type DocumentoJuridicao = 'UNIAO' | 'PARANA' | 'NAO_APLICAVEL';
export type DocumentoStatus = 'RECEBIDO' | 'PROCESSANDO' | 'PROCESSADO' | 'PROCESSADO_COM_ALERTAS' | 'ERRO';
export type DocumentoTipo = 'IRPJ' | 'CSLL' | 'PIS' | 'COFINS' | 'IPI' | 'DCTF' | 'DCTF_WEB' | 'EFD_CONTRIBUICOES' | 'ECD' | 'ECF' | 'EFD_ICMS_IPI' | 'ICMS' | 'DEMONSTRACAO_CONTABIL' | 'OUTRO';
export type TributoTipo = 'IRPJ' | 'CSLL' | 'PIS' | 'COFINS' | 'IPI' | 'ICMS' | 'OUTROS';

export interface DocumentoFiscal {
  id: number;
  clienteId: number;
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
  sha256?: string | null;
}

export type Papel = 'ADMIN' | 'CONSULTOR' | 'ASSISTENTE';

/** Resposta de POST /api/auth/login. */
export interface AuthUser {
  id: number;
  email: string;
  nome: string;
  token: string;
  escritorioId: number;
  papel: Papel;
  trocarSenhaNoProximoLogin: boolean;
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
  nomeEscritorio: string;
  documentoEscritorio: string;
}
