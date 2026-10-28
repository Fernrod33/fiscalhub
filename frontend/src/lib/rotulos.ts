/** Rótulos dos enums da API. Tipados contra os tipos gerados: um valor novo no backend quebra o build aqui. */
import type { Tom } from '@/components/selo';
import type { Debito, Documento, DocumentoStatus, EtapaProcessamento, EventoAuditoria, Inconsistencia, Papel, StatusConciliacao, TipoRelatorio } from './api/tipos';

export const PAPEIS: Record<Papel, string> = { ADMIN: 'Administrador', CONSULTOR: 'Consultor', ASSISTENTE: 'Assistente' };

export const STATUS_DOCUMENTO: Record<DocumentoStatus, { rotulo: string; tom: Tom }> = {
  RECEBIDO: { rotulo: 'Recebido', tom: 'info' },
  PROCESSANDO: { rotulo: 'Processando', tom: 'warning' },
  PROCESSADO: { rotulo: 'Processado', tom: 'success' },
  PROCESSADO_COM_ALERTAS: { rotulo: 'Processado com alertas', tom: 'warning' },
  ERRO: { rotulo: 'Erro', tom: 'danger' },
  AGUARDANDO_CLIENTE: { rotulo: 'Aguardando cliente', tom: 'info' },
  AGUARDANDO_MAPEAMENTO: { rotulo: 'Aguardando mapeamento', tom: 'info' },
  LAYOUT_NAO_RECONHECIDO: { rotulo: 'Layout não reconhecido', tom: 'warning' },
};

/** Status em que o processamento parou (terminou ou espera o usuário): o polling para. */
export const STATUS_FINAL: DocumentoStatus[] = ['PROCESSADO', 'PROCESSADO_COM_ALERTAS', 'ERRO', 'AGUARDANDO_CLIENTE', 'AGUARDANDO_MAPEAMENTO',
  'LAYOUT_NAO_RECONHECIDO'];

/** Tipo detectado no conteúdo (rótulo curto: vai em selo e filtro). */
export const TIPOS_DOCUMENTO: Record<Documento['tipo'], string> = {
  ECD: 'ECD',
  ECF: 'ECF',
  EFD_CONTRIBUICOES: 'EFD-Contribuições',
  EFD_ICMS_IPI: 'EFD ICMS/IPI',
  BALANCETE: 'Balancete',
  RELATORIO_SITUACAO_FISCAL: 'Situação fiscal',
  EXTRATO_REGULARIZE: 'Regularize',
  CAPAG: 'CAPAG',
  DCTF: 'DCTF',
  DCTFWEB: 'DCTFWeb',
  DARF: 'DARF',
  RELATORIO_PAGAMENTOS: 'Pagamentos',
  CONTA_CORRENTE_PR: 'Conta corrente PR',
  RTI: 'RTI',
  GR_PR: 'GR-PR',
  RELACAO_PAGAMENTOS_GR_PR: 'Relação GR-PR',
  PDF: 'PDF',
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
  PLANILHA_MAPEAR_COLUNAS: 'Mapeou colunas da planilha',
  PLANILHA_SALVAR_DEPARA: 'Salvou de-para da planilha',
  CLIENTE_CRIAR: 'Cadastrou cliente',
  CLIENTE_EDITAR: 'Editou cliente',
  CLIENTE_EXCLUIR: 'Excluiu cliente',
  SIMULACAO_CRIAR: 'Criou simulação',
  USUARIO_CRIAR: 'Criou usuário',
  USUARIO_ALTERAR_PAPEL: 'Alterou papel',
  USUARIO_DESATIVAR: 'Desativou usuário',
  IDENTIDADE_ALTERAR: 'Alterou a identidade do escritório',
  IDENTIDADE_LOGO: 'Trocou o logotipo do escritório',
  RELATORIO_EMITIR: 'Emitiu relatório',
  RELATORIO_BAIXAR: 'Baixou relatório',
};

export const RESULTADOS_AUDITORIA: Record<NonNullable<EventoAuditoria['resultado']>, { rotulo: string; tom: Tom }> = {
  SUCESSO: { rotulo: 'Sucesso', tom: 'success' },
  FALHA: { rotulo: 'Falha', tom: 'danger' },
};

/** Tipo de recurso gravado na trilha (AcaoAuditoria.recurso no backend). */
export const RECURSOS_AUDITORIA: Record<string, string> = {
  USUARIO: 'Usuário', CLIENTE: 'Cliente', DOCUMENTO: 'Documento', SIMULACAO: 'Simulação', ESCRITORIO: 'Escritório',
  RELATORIO: 'Relatório',
};

export const TIPOS_RELATORIO: Record<TipoRelatorio, { rotulo: string; descricao: string }> = {
  TRIBUTOS: { rotulo: 'Tributos apurados, declarados e pagos', descricao: 'Conciliação por tributo e competência, totais, divergências com acréscimos e origem.' },
  CARGA_TRIBUTARIA: { rotulo: 'Carga tributária efetiva', descricao: 'Tributos ÷ receita bruta e IRPJ + CSLL ÷ lucro antes do IR, por exercício.' },
  SITUACAO_FISCAL: { rotulo: 'Situação fiscal resumida', descricao: 'Débitos, parcelamentos, pendências, CAPAG e o que impede a certidão.' },
};

export const SITUACAO_DEBITO: Record<Debito['situacao'], { rotulo: string; tom: Tom }> = {
  EM_ABERTO: { rotulo: 'Em aberto', tom: 'danger' }, INSCRITO_DIVIDA_ATIVA: { rotulo: 'Inscrito em dívida ativa', tom: 'danger' },
  PARCELADO: { rotulo: 'Parcelado', tom: 'info' }, SUSPENSO: { rotulo: 'Suspenso', tom: 'info' },
  NEGOCIADO: { rotulo: 'Negociado', tom: 'info' },
};

/**
 * Tom (cor de estado) de cada status da conciliação; ordem = gravidade (a célula agregada mostra o pior). O rótulo vem
 * da API (statusRotulo, fonte única no backend), não daqui.
 */
export const TOM_STATUS: Record<StatusConciliacao, Tom> = {
  NAO_PAGO: 'danger', PAGO_A_MENOR: 'danger', PAGO_EM_ATRASO_SEM_ACRESCIMOS: 'danger', NAO_DECLARADO: 'danger',
  DIVERGENCIA_APURADO_DECLARADO: 'warning', PAGO_A_MAIOR: 'warning', PARCELADO: 'info', COMPENSADO_OU_SUSPENSO: 'info',
  CONCILIADO: 'success',
};

export const SITUACAO_PARCELAMENTO: Record<string, { rotulo: string; tom: Tom }> = {
  EM_DIA: { rotulo: 'Em dia', tom: 'success' }, EM_ATRASO: { rotulo: 'Em atraso', tom: 'danger' },
  RESCINDIDO: { rotulo: 'Rescindido', tom: 'danger' },
};

/** Fonte da capacidade de pagamento oficial. */
export const FONTES_CAPACIDADE: Record<string, string> = { PGFN: 'PGFN (CAPAG)', SEFA_PR: 'Receita Estadual do PR (RTI)' };

export const ORGAO_CREDOR: Record<Debito['orgaoCredor'], string> = { RFB: 'RFB', PGFN: 'PGFN', SEFA_PR: 'SEFA-PR', PGE_PR: 'PGE-PR' };

/** Tributos apurados (TipoTributo), na ordem da matriz dentro de cada esfera. */
export const TRIBUTOS: Record<string, string> = {
  IRPJ: 'IRPJ', CSLL: 'CSLL', PIS: 'PIS', COFINS: 'COFINS', IPI: 'IPI', INSS: 'Previdenciárias', TERCEIROS: 'Terceiros',
  IRRF: 'IRRF',
  ICMS: 'ICMS', ICMS_ST: 'ICMS-ST', ICMS_DIFAL: 'ICMS DIFAL', ICMS_FCP: 'FCP',
};

export const ESFERAS: Record<string, string> = { FEDERAL: 'Federais', ESTADUAL: 'Estaduais' };

export const REGIMES: Record<string, string> = {
  LUCRO_REAL: 'Lucro Real', LUCRO_PRESUMIDO: 'Lucro Presumido', LUCRO_ARBITRADO: 'Lucro Arbitrado',
  NAO_CUMULATIVO: 'Não cumulativo', CUMULATIVO: 'Cumulativo',
};

/** Saldos que passam de um período para outro (saldo_fiscal.tipo). */
export const SALDOS_FISCAIS: Record<string, string> = {
  PREJUIZO_FISCAL: 'Prejuízo fiscal', BASE_NEGATIVA_CSLL: 'Base negativa da CSLL',
  CREDITO_PIS: 'Créditos de PIS', CREDITO_COFINS: 'Créditos de COFINS',
  SALDO_CREDOR_ICMS: 'Saldo credor de ICMS', SALDO_CREDOR_ICMS_ST: 'Saldo credor de ICMS-ST',
  SALDO_CREDOR_DIFAL: 'Saldo credor de DIFAL', SALDO_CREDOR_FCP: 'Saldo credor de FCP', SALDO_CREDOR_IPI: 'Saldo credor de IPI',
};

/** Campos de valor de uma apuração (chaves de Apuracao.origem). */
export const CAMPOS_APURACAO: [campo: 'baseCalculo' | 'valorApurado' | 'deducoes' | 'valorARecolher' | 'saldoCredor', rotulo: string][] = [
  ['baseCalculo', 'Base de cálculo'], ['valorApurado', 'Apurado'], ['deducoes', 'Deduções e créditos'],
  ['valorARecolher', 'A recolher'], ['saldoCredor', 'Saldo credor'],
];
