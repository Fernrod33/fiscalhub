/** Rótulos dos enums da API. Tipados contra os tipos gerados: um valor novo no backend quebra o build aqui. */
import type { Tom } from '@/components/selo';
import type { Documento, DocumentoStatus, EtapaProcessamento, EventoAuditoria, Inconsistencia, Papel, Debito } from './api/tipos';

export const PAPEIS: Record<Papel, string> = { ADMIN: 'Administrador', CONSULTOR: 'Consultor', ASSISTENTE: 'Assistente' };

export const STATUS_DOCUMENTO: Record<DocumentoStatus, { rotulo: string; tom: Tom }> = {
  RECEBIDO: { rotulo: 'Recebido', tom: 'info' },
  PROCESSANDO: { rotulo: 'Processando', tom: 'warning' },
  PROCESSADO: { rotulo: 'Processado', tom: 'success' },
  PROCESSADO_COM_ALERTAS: { rotulo: 'Processado com alertas', tom: 'warning' },
  ERRO: { rotulo: 'Erro', tom: 'danger' },
  AGUARDANDO_CLIENTE: { rotulo: 'Aguardando cliente', tom: 'info' },
  AGUARDANDO_MAPEAMENTO: { rotulo: 'Aguardando mapeamento', tom: 'info' },
};

/** Status em que o processamento parou (terminou ou espera o usuário): o polling para. */
export const STATUS_FINAL: DocumentoStatus[] = ['PROCESSADO', 'PROCESSADO_COM_ALERTAS', 'ERRO', 'AGUARDANDO_CLIENTE', 'AGUARDANDO_MAPEAMENTO'];

/** Tipo detectado no conteúdo (rótulo curto: vai em selo e filtro). */
export const TIPOS_DOCUMENTO: Record<Documento['tipo'], string> = {
  ECD: 'ECD',
  ECF: 'ECF',
  EFD_CONTRIBUICOES: 'EFD-Contribuições',
  EFD_ICMS_IPI: 'EFD ICMS/IPI',
  DCTF: 'DCTF',
  DCTF_WEB: 'DCTFWeb',
  DEMONSTRACAO_CONTABIL: 'Demonstração contábil',
  BALANCETE: 'Balancete'
,
  IRPJ: 'IRPJ', CSLL: 'CSLL', PIS: 'PIS', COFINS: 'COFINS', IPI: 'IPI', ICMS: 'ICMS',
  OUTRO: 'Outro',
};

/** Etapas do processamento na ordem da linha do tempo (CONCLUIDO = pronto). */
export const ETAPAS: { etapa: EtapaProcessamento; rotulo: string }[] = [
  { etapa: 'RECEBIDO', rotulo: 'Recebido' },
  { etapa: 'LENDO', rotulo: 'Lendo' },
  { etapa: 'VALIDANDO', rotulo: 'Validando' },
  { etapa: 'GRAVANDO', rotulo: 'Gravando' },
  { etapa: 'CONCLUIDO', rotulo: 'Pronto' },
];

export const SEVERIDADES: Record<Inconsistencia['severidade'], { rotulo: string; plural: string; tom: Tom }> = {
  ERRO: { rotulo: 'Erro', plural: 'Erros', tom: 'danger' },
  ALERTA: { rotulo: 'Alerta', plural: 'Alertas', tom: 'warning' },
  INFO: { rotulo: 'Informação', plural: 'Informações', tom: 'info' },
};

export const SITUACOES_ESCRITURACAO: Record<string, { rotulo: string; tom: Tom }> = {
  VIGENTE: { rotulo: 'Vigente', tom: 'success' },
  SUBSTITUIDA: { rotulo: 'Substituída', tom: 'warning' },
};

export const ORIGENS_ESCRITURACAO: Record<string, string> = { ECD: 'ECD', PLANILHA: 'Balancete em planilha' };

/** 0000.COD_PLAN_REF dos planos carregados. */
export const PLANOS_REFERENCIAIS: Record<string, string> = { 1: 'PJ em geral — Lucro Real', 2: 'PJ em geral — Lucro Presumido' };


export const ACOES_AUDITORIA: Record<EventoAuditoria['acao'], string> = {
  LOGIN: 'Login',
  DOCUMENTO_IMPORTAR: 'Importou documento',
  DOCUMENTO_BAIXAR: 'Baixou documento',
  DOCUMENTO_EXCLUIR: 'Excluiu documento',
  DOCUMENTO_REPROCESSAR: 'Reprocessou documento',
  DOCUMENTO_CONFIRMAR_CLIENTE: 'Confirmou cliente do documento',
  CLIENTE_CRIAR: 'Cadastrou cliente',
  CLIENTE_EDITAR: 'Editou cliente',
  CLIENTE_EXCLUIR: 'Excluiu cliente',
  SIMULACAO_CRIAR: 'Criou simulação',
  USUARIO_CRIAR: 'Criou usuário',
  USUARIO_ALTERAR_PAPEL: 'Alterou papel',
  USUARIO_DESATIVAR: 'Desativou usuário',
};

export const RESULTADOS_AUDITORIA: Record<NonNullable<EventoAuditoria['resultado']>, { rotulo: string; tom: Tom }> = {
  SUCESSO: { rotulo: 'Sucesso', tom: 'success' },
  FALHA: { rotulo: 'Falha', tom: 'danger' },
};

/** Tipo de recurso gravado na trilha (AcaoAuditoria.recurso no backend). */
export const RECURSOS_AUDITORIA: Record<string, string> = {
  USUARIO: 'Usuário', CLIENTE: 'Cliente', DOCUMENTO: 'Documento', SIMULACAO: 'Simulação',
};

export const SITUACAO_DEBITO: Record<Debito['situacao'], string> = {
  EM_ABERTO: 'Em aberto', INSCRITO_DIVIDA_ATIVA: 'Inscrito em dívida ativa', PARCELADO: 'Parcelado', SUSPENSO: 'Suspenso',
};

export const ORGAO_CREDOR: Record<Debito['orgaoCredor'], string> = { RFB: 'RFB', PGFN: 'PGFN', SEFA_PR: 'SEFA-PR', PGE_PR: 'PGE-PR' };
