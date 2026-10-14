// DADOS DE EXEMPLO — só para o laboratório de design (rota de desenvolvimento). Nunca vêm da API.

export type StatusExemplo = 'RECEBIDO' | 'PROCESSANDO' | 'PROCESSADO' | 'ERRO';

export const clienteExemplo = {
  razaoSocial: 'Metalúrgica Exemplo Ltda',
  cnpj: '11.222.333/0001-81',
};

export const kpisExemplo = [
  { rotulo: 'Documentos importados', valor: 48, formato: 'inteiro' as const },
  { rotulo: 'Débitos em aberto', valor: 1_284_937.42, formato: 'moeda' as const },
  { rotulo: 'Processados', valor: 45, formato: 'inteiro' as const },
  { rotulo: 'Com erro', valor: 1, formato: 'inteiro' as const },
];

export const documentosExemplo: {
  arquivo: string;
  tipo: string;
  competencia: string;
  tamanho: number;
  importadoEm: string;
  status: StatusExemplo;
}[] = [
  { arquivo: 'ECD_2024.txt', tipo: 'ECD', competencia: '2024', tamanho: 18_402_311, importadoEm: '2026-10-12T14:02:00', status: 'PROCESSANDO' },
  { arquivo: 'EFD_Contrib_09-2026.txt', tipo: 'EFD-Contribuições', competencia: '09/2026', tamanho: 4_118_902, importadoEm: '2026-10-11T09:41:00', status: 'PROCESSADO' },
  { arquivo: 'Situacao_Fiscal_RFB.pdf', tipo: 'Relatório de Situação Fiscal', competencia: '10/2026', tamanho: 612_044, importadoEm: '2026-10-10T17:20:00', status: 'PROCESSADO' },
  { arquivo: 'EFD_ICMS_IPI_08-2026.txt', tipo: 'EFD ICMS/IPI', competencia: '08/2026', tamanho: 9_870_115, importadoEm: '2026-10-09T11:05:00', status: 'ERRO' },
  { arquivo: 'Balancete_Q3.xlsx', tipo: 'Demonstração contábil', competencia: '3º tri/2026', tamanho: 231_877, importadoEm: '2026-10-08T16:48:00', status: 'RECEBIDO' },
];

export const pontosAtencaoExemplo = [
  { nivel: 'danger' as const, texto: 'Débito de IRPJ (03/2026) inscrito em dívida ativa da União.' },
  { nivel: 'warning' as const, texto: 'EFD ICMS/IPI de 08/2026 com erro de leitura no registro C170.' },
  { nivel: 'info' as const, texto: 'ECD 2024 em processamento; os saldos aparecem ao concluir.' },
];
