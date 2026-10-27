import type { ItemConciliacao, StatusConciliacao } from '@/lib/api/tipos';
import { ESFERAS, STATUS_CONCILIACAO, TRIBUTOS } from '@/lib/rotulos';

export type Periodicidade = 'MENSAL' | 'TRIMESTRAL' | 'ANUAL';

/** Uma coluna de valor dentro de cada competência, calculada dos itens da conciliação da célula. */
export type Metrica = { chave: string; rotulo: string; valor: (itens: ItemConciliacao[]) => number | null };

const soma = (itens: ItemConciliacao[], valor: (i: ItemConciliacao) => number | null | undefined) => {
  const valores = itens.map(valor).filter((v): v is number => v != null);
  return valores.length ? valores.reduce((a, b) => a + b, 0) : null;
};

/** Apurado (SPED), declarado (DCTF, DCTFWeb, conta corrente), pago (DARF, GR-PR) e a diferença. */
export const METRICAS: Metrica[] = [
  { chave: 'apurado', rotulo: 'Apurado', valor: (i) => soma(i, (x) => x.apurado?.valor) },
  { chave: 'declarado', rotulo: 'Declarado', valor: (i) => soma(i, (x) => x.declarado?.valor) },
  { chave: 'pago', rotulo: 'Pago', valor: (i) => soma(i, (x) => x.pago?.valor) },
  { chave: 'diferenca', rotulo: 'Diferença', valor: (i) => soma(i, (x) => x.diferenca) },
];

/** Linha da matriz: um tributo (estaduais por UF). */
export type LinhaMatriz = { chave: string; tributo: string; uf: string | null; esfera: string; rotulo: string };
export type Celula = { linha: LinhaMatriz; competencia: string; rotuloCompetencia: string; itens: ItemConciliacao[] };

const GRAVIDADE = Object.keys(STATUS_CONCILIACAO) as StatusConciliacao[];

/** O pior status entre os itens (célula com vários meses, na visão trimestral ou anual). */
export const piorStatus = (itens: ItemConciliacao[]) =>
  itens.map((i) => i.status).sort((a, b) => GRAVIDADE.indexOf(a) - GRAVIDADE.indexOf(b))[0];

/** Competência pelo período de apuração (aaaa-mm): mensal 2025-01, trimestral 2025-T1, anual 2025. */
function competencia(periodo: string, periodicidade: Periodicidade): { chave: string; rotulo: string } {
  const ano = periodo.slice(0, 4);
  const mes = Number(periodo.slice(5, 7));
  if (periodicidade === 'ANUAL') return { chave: ano, rotulo: ano };
  if (periodicidade === 'TRIMESTRAL') {
    const tri = Math.ceil(mes / 3);
    return { chave: `${ano}-T${tri}`, rotulo: `${tri}º tri/${ano}` };
  }
  return { chave: periodo, rotulo: `${periodo.slice(5, 7)}/${ano}` };
}

const ORDEM_TRIBUTOS = Object.keys(TRIBUTOS);
export const ORDEM_ESFERAS = Object.keys(ESFERAS);

/** Linhas (tributo e UF) e colunas (competências) dos itens da conciliação, já ordenadas. */
export function montarMatriz(itens: ItemConciliacao[], periodicidade: Periodicidade) {
  const linhas = new Map<string, LinhaMatriz>();
  const colunas = new Map<string, string>();
  const celulas = new Map<string, ItemConciliacao[]>();
  for (const i of itens) {
    const uf = i.esfera === 'ESTADUAL' ? (i.uf ?? null) : null;
    const chave = `${i.tributo}|${uf ?? ''}`;
    linhas.set(chave, { chave, tributo: i.tributo, uf, esfera: i.esfera,
      rotulo: (TRIBUTOS[i.tributo] ?? i.tributo) + (uf ? ` · ${uf}` : '') });
    const c = competencia(i.periodo, periodicidade);
    colunas.set(c.chave, c.rotulo);
    const celula = `${chave}@${c.chave}`;
    celulas.set(celula, [...(celulas.get(celula) ?? []), i]);
  }
  const ordenadas = [...linhas.values()].sort((x, y) =>
    ORDEM_ESFERAS.indexOf(x.esfera) - ORDEM_ESFERAS.indexOf(y.esfera)
    || ORDEM_TRIBUTOS.indexOf(x.tributo) - ORDEM_TRIBUTOS.indexOf(y.tributo)
    || (x.uf ?? '').localeCompare(y.uf ?? ''));
  return {
    linhas: ordenadas,
    colunas: [...colunas.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([chave, rotulo]) => ({ chave, rotulo })),
    celula: (linha: string, coluna: string) => celulas.get(`${linha}@${coluna}`) ?? [],
  };
}
