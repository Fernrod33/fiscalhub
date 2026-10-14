// DADOS DE EXEMPLO — só para a referência do design system (rota de desenvolvimento). Nunca vêm da API.

export const kpisExemplo = [
  { rotulo: 'Documentos importados', valor: 48, formato: 'inteiro' as const, estado: 'neutro' as const },
  { rotulo: 'Débitos em aberto', valor: 1_284_937.42, formato: 'moeda' as const, estado: 'neutro' as const },
  { rotulo: 'Processados', valor: 45, formato: 'inteiro' as const, estado: 'neutro' as const },
  { rotulo: 'Com erro', valor: 1, formato: 'inteiro' as const, estado: 'perigo' as const },
];

export type DocumentoExemplo = { arquivo: string; tipo: string; competencia: string; tamanho: number; importadoEm: string };

export const documentosExemplo: DocumentoExemplo[] = [
  { arquivo: 'ECD_2024_Metalurgica_Exemplo_Ltda_escrituracao_contabil_digital.txt', tipo: 'ECD', competencia: '2024', tamanho: 18_402_311, importadoEm: '2026-10-12T14:02:00' },
  { arquivo: 'EFD_Contrib_09-2026.txt', tipo: 'EFD-Contribuições', competencia: '09/2026', tamanho: 4_118_902, importadoEm: '2026-10-11T09:41:00' },
  { arquivo: 'Situacao_Fiscal_RFB.pdf', tipo: 'Relatório de Situação Fiscal', competencia: '10/2026', tamanho: 612_044, importadoEm: '2026-10-10T17:20:00' },
  { arquivo: 'EFD_ICMS_IPI_08-2026.txt', tipo: 'EFD ICMS/IPI', competencia: '08/2026', tamanho: 9_870_115, importadoEm: '2026-10-09T11:05:00' },
  { arquivo: 'Balancete_Q3.xlsx', tipo: 'Demonstração contábil', competencia: '3º tri/2026', tamanho: 231_877, importadoEm: '2026-10-08T16:48:00' },
];
