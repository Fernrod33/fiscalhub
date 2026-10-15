import { Fragment } from 'react';
import type { Apuracao } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { ESFERAS } from '@/lib/rotulos';
import { cn } from '@/lib/utils';
import { A_RECOLHER, montarMatriz, ORDEM_ESFERAS, type Celula, type Metrica, type Periodicidade } from '../matriz';

/**
 * Matriz tributo × competência: tributos agrupados por esfera nas linhas, competências nas colunas e, em cada célula,
 * as métricas (hoje, o valor a recolher apurado). A célula abre o detalhe das apurações dela.
 */
export function MatrizTributos({ apuracoes, periodicidade, metricas = [A_RECOLHER], aoAbrir }: {
  apuracoes: Apuracao[]; periodicidade: Periodicidade; metricas?: Metrica[]; aoAbrir: (celula: Celula) => void;
}) {
  const { linhas, colunas, celula } = montarMatriz(apuracoes, periodicidade);
  const esferas = ORDEM_ESFERAS.filter((e) => linhas.some((l) => l.esfera === e));
  const variasMetricas = metricas.length > 1;

  return (
    <div className="overflow-x-auto rounded-md border">
      <table className="w-full border-collapse text-sm">
        <caption className="sr-only">Valores apurados por tributo e competência</caption>
        <thead className="bg-muted/50">
          <tr>
            <th scope="col" rowSpan={variasMetricas ? 2 : 1} className="sticky left-0 bg-muted/50 px-3 py-2 text-left font-medium">Tributo</th>
            {colunas.map((c) => (
              <th key={c.chave} scope="colgroup" colSpan={metricas.length} className="num whitespace-nowrap px-3 py-2 text-right font-medium">
                {c.rotulo}
              </th>
            ))}
          </tr>
          {variasMetricas && (
            <tr>
              {colunas.map((c) => metricas.map((m) => (
                <th key={`${c.chave}-${m.chave}`} scope="col" className="whitespace-nowrap px-3 py-1 text-right text-xs font-normal text-muted-foreground">
                  {m.rotulo}
                </th>
              )))}
            </tr>
          )}
        </thead>
        <tbody>
          {esferas.map((esfera) => (
            <Fragment key={esfera}>
              <tr className="border-t bg-surface">
                <th scope="rowgroup" colSpan={1 + colunas.length * metricas.length}
                    className="sticky left-0 bg-surface px-3 py-1.5 text-left text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                  {ESFERAS[esfera]}
                </th>
              </tr>
              {linhas.filter((l) => l.esfera === esfera).map((l) => (
                <tr key={l.chave} className="border-t">
                  <th scope="row" className="sticky left-0 whitespace-nowrap bg-background px-3 py-1.5 text-left font-medium">{l.rotulo}</th>
                  {colunas.map((c) => {
                    const apuracoesDaCelula = celula(l.chave, c.chave);
                    return metricas.map((m) => {
                      const valor = apuracoesDaCelula.length ? m.valor(apuracoesDaCelula) : null;
                      return (
                        <td key={`${c.chave}-${m.chave}`} className="px-1 py-0.5 text-right">
                          {valor == null
                            ? <span className="num px-2 text-muted-foreground">—</span>
                            : (
                              <button type="button" onClick={() => aoAbrir({ linha: l, competencia: c.chave, rotuloCompetencia: c.rotulo, apuracoes: apuracoesDaCelula })}
                                      aria-label={`${l.rotulo} em ${c.rotulo}, ${m.rotulo}: ${fmt.moeda(valor)}. Abrir detalhe`}
                                      className={cn('num w-full whitespace-nowrap rounded-sm px-2 py-1 text-right underline-offset-4 hover:bg-accent hover:underline focus-visible:outline-2',
                                        valor === 0 && 'text-muted-foreground')}>
                                {fmt.moeda(valor)}
                              </button>
                            )}
                        </td>
                      );
                    });
                  })}
                </tr>
              ))}
            </Fragment>
          ))}
        </tbody>
      </table>
    </div>
  );
}
