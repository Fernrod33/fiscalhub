import { Fragment } from 'react';
import { Selo } from '@/components/selo';
import type { ItemConciliacao } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { ESFERAS, STATUS_CONCILIACAO } from '@/lib/rotulos';
import { cn } from '@/lib/utils';
import { METRICAS, montarMatriz, ORDEM_ESFERAS, piorStatus, type Celula, type Metrica, type Periodicidade } from '../matriz';

/**
 * Matriz tributo × competência: tributos agrupados por esfera nas linhas, competências nas colunas e, em cada uma,
 * apurado, declarado, pago e diferença, com o status da conciliação. Qualquer valor da célula abre a gaveta.
 */
export function MatrizTributos({ itens, periodicidade, metricas = METRICAS, aoAbrir }: {
  itens: ItemConciliacao[]; periodicidade: Periodicidade; metricas?: Metrica[]; aoAbrir: (celula: Celula) => void;
}) {
  const { linhas, colunas, celula } = montarMatriz(itens, periodicidade);
  const esferas = ORDEM_ESFERAS.filter((e) => linhas.some((l) => l.esfera === e));

  return (
    <div className="overflow-x-auto rounded-md border">
      <table className="w-full border-collapse text-sm">
        <caption className="sr-only">Apurado, declarado e pago por tributo e competência</caption>
        <thead className="bg-muted/50">
          <tr>
            <th scope="col" rowSpan={2} className="sticky left-0 z-10 bg-muted/50 px-3 py-2 text-left font-medium">Tributo</th>
            {colunas.map((c) => (
              <th key={c.chave} scope="colgroup" colSpan={metricas.length} className="num whitespace-nowrap border-l px-3 py-2 text-center font-medium">
                {c.rotulo}
              </th>
            ))}
          </tr>
          <tr>
            {colunas.map((c) => metricas.map((m, i) => (
              <th key={`${c.chave}-${m.chave}`} scope="col"
                  className={cn('whitespace-nowrap px-3 py-1 text-right text-xs font-normal text-muted-foreground', i === 0 && 'border-l')}>
                {m.rotulo}
              </th>
            )))}
          </tr>
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
                  <th scope="row" className="sticky left-0 z-10 whitespace-nowrap bg-background px-3 py-1.5 text-left font-medium">{l.rotulo}</th>
                  {colunas.map((c) => {
                    const itensDaCelula = celula(l.chave, c.chave);
                    const status = itensDaCelula.length ? piorStatus(itensDaCelula) : undefined;
                    return metricas.map((m, i) => {
                      const valor = itensDaCelula.length ? m.valor(itensDaCelula) : null;
                      const diferenca = m.chave === 'diferenca';
                      return (
                        <td key={`${c.chave}-${m.chave}`} className={cn('px-1 py-0.5 text-right align-top', i === 0 && 'border-l')}>
                          {!itensDaCelula.length
                            ? <span className="num px-2 text-muted-foreground">—</span>
                            : (
                              <button type="button" onClick={() => aoAbrir({ linha: l, competencia: c.chave, rotuloCompetencia: c.rotulo, itens: itensDaCelula })}
                                      aria-label={`${l.rotulo} em ${c.rotulo}, ${m.rotulo}: ${fmt.moeda(valor)}${status ? `, ${STATUS_CONCILIACAO[status].rotulo}` : ''}. Abrir detalhe`}
                                      className={cn('num grid w-full justify-items-end gap-1 whitespace-nowrap rounded-sm px-2 py-1 text-right underline-offset-4 hover:bg-accent hover:underline focus-visible:outline-2',
                                        (valor == null || valor === 0) && 'text-muted-foreground')}>
                                {fmt.moeda(valor)}
                                {diferenca && status && <Selo tom={STATUS_CONCILIACAO[status].tom}>{STATUS_CONCILIACAO[status].rotulo}</Selo>}
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
