import type { Apuracao } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { ESFERAS, TRIBUTOS } from '@/lib/rotulos';

export type Periodicidade = 'MENSAL' | 'TRIMESTRAL' | 'ANUAL';

/**
 * Uma coluna de valor dentro de cada competência. Hoje só o apurado a recolher; a Fase 2b acrescenta Declarado, Pago e
 * Diferença passando outras métricas (cada uma calcula o valor da célula a partir das apurações dela).
 */
export type Metrica = { chave: string; rotulo: string; valor: (apuracoes: Apuracao[]) => number | null };

export const A_RECOLHER: Metrica = {
  chave: 'aRecolher', rotulo: 'A recolher',
  valor: (apuracoes) => apuracoes.reduce((total, a) => total + a.valorARecolher, 0),
};

/** Linha da matriz: um tributo (estaduais por UF). */
export type LinhaMatriz = { chave: string; tributo: string; uf: string | null; esfera: string; rotulo: string };
export type Celula = { linha: LinhaMatriz; competencia: string; rotuloCompetencia: string; apuracoes: Apuracao[] };

/** Competência pela data final do período: mensal 2025-01, trimestral 2025-T1, anual 2025. */
function competencia(dataFim: string, periodicidade: Periodicidade): { chave: string; rotulo: string } {
  const ano = dataFim.slice(0, 4);
  const mes = Number(dataFim.slice(5, 7));
  if (periodicidade === 'ANUAL') return { chave: ano, rotulo: ano };
  if (periodicidade === 'TRIMESTRAL') {
    const tri = Math.ceil(mes / 3);
    return { chave: `${ano}-T${tri}`, rotulo: `${tri}º tri/${ano}` };
  }
  return { chave: dataFim.slice(0, 7), rotulo: fmt.competencia(dataFim) };
}

const ORDEM_TRIBUTOS = Object.keys(TRIBUTOS);
export const ORDEM_ESFERAS = Object.keys(ESFERAS);

/** Linhas (tributo e UF) e colunas (competências) das apurações, já ordenadas. */
export function montarMatriz(apuracoes: Apuracao[], periodicidade: Periodicidade) {
  const linhas = new Map<string, LinhaMatriz>();
  const colunas = new Map<string, string>();
  const celulas = new Map<string, Apuracao[]>();
  for (const a of apuracoes) {
    const uf = a.esfera === 'ESTADUAL' ? (a.uf ?? null) : null;
    const chave = `${a.tributo}|${uf ?? ''}`;
    linhas.set(chave, { chave, tributo: a.tributo, uf, esfera: a.esfera,
      rotulo: (TRIBUTOS[a.tributo] ?? a.tributo) + (uf ? ` · ${uf}` : '') });
    const c = competencia(a.dataFim, periodicidade);
    colunas.set(c.chave, c.rotulo);
    const celula = `${chave}@${c.chave}`;
    celulas.set(celula, [...(celulas.get(celula) ?? []), a]);
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
