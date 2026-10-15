import { useState } from 'react';
import { ChevronRight } from 'lucide-react';
import { Collapse } from '@/components/motion';
import { Selo } from '@/components/selo';
import { Skeleton } from '@/components/ui/skeleton';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import type { Apuracao } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { CAMPOS_APURACAO, REGIMES } from '@/lib/rotulos';
import { cn } from '@/lib/utils';
import { useDetalheApuracao } from '../api';
import type { Celula } from '../matriz';
import { ValorComOrigem } from './origem-arquivo';

/** Gaveta com as apurações de uma célula da matriz: valores com origem, linhas do cálculo e obrigações a recolher. */
export function DetalheCelula({ clienteId, celula, aoFechar }: { clienteId: number; celula: Celula | null; aoFechar: () => void }) {
  return (
    <Sheet open={celula != null} onOpenChange={(aberto) => !aberto && aoFechar()}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-xl">
        {celula && (
          <>
            <SheetHeader>
              <SheetTitle>{celula.linha.rotulo} · {celula.rotuloCompetencia}</SheetTitle>
              <SheetDescription>
                {celula.apuracoes.length === 1 ? '1 apuração' : `${celula.apuracoes.length} apurações`} nesta competência
              </SheetDescription>
            </SheetHeader>
            <div className="grid gap-4 px-4 pb-6">
              {celula.apuracoes.map((a) => <ApuracaoDetalhada key={a.id} clienteId={clienteId} apuracao={a} />)}
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}

function ApuracaoDetalhada({ clienteId, apuracao: a }: { clienteId: number; apuracao: Apuracao }) {
  const [calculoAberto, setCalculoAberto] = useState(false);
  const detalhe = useDetalheApuracao(clienteId, calculoAberto ? a.id : undefined);
  const idCalculo = `calculo-${a.id}`;
  return (
    <section aria-label={`${a.descricao} de ${fmt.data(a.dataInicio)} a ${fmt.data(a.dataFim)}`} className="grid gap-3 rounded-md border p-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="font-semibold">{a.descricao}</h3>
        {a.regime && <Selo tom="info">{REGIMES[a.regime] ?? a.regime}</Selo>}
      </div>
      <p className="num text-xs text-muted-foreground">{fmt.data(a.dataInicio)} a {fmt.data(a.dataFim)} · {a.arquivo}</p>
      <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-1 text-sm">
        {CAMPOS_APURACAO.filter(([campo]) => a[campo] != null).map(([campo, rotulo]) => (
          <div key={campo} className="contents">
            <dt className={cn('text-muted-foreground', campo === 'valorARecolher' && 'font-semibold text-foreground')}>{rotulo}</dt>
            <dd>
              <ValorComOrigem clienteId={clienteId} rotulo={`${rotulo} — ${a.descricao}`} valor={a[campo]} arquivo={a.arquivo}
                              registro={a.origem[campo]?.registro} linha={a.origem[campo]?.linha} destaque={campo === 'valorARecolher'} />
            </dd>
          </div>
        ))}
      </dl>
      <button type="button" onClick={() => setCalculoAberto(!calculoAberto)} aria-expanded={calculoAberto} aria-controls={idCalculo}
              className="flex items-center gap-1 text-sm font-medium text-primary">
        <ChevronRight className={cn('size-4 transition-transform duration-(--dur-fast)', calculoAberto && 'rotate-90')} aria-hidden />
        Linhas do cálculo e obrigações
      </button>
      <Collapse aberto={calculoAberto} id={idCalculo}>
        {detalhe.isPending || !detalhe.data
          ? <Skeleton className="h-24" />
          : (
            <div className="grid gap-3">
              <table className="w-full text-sm">
                <caption className="sr-only">Linhas do cálculo</caption>
                <thead><tr className="text-xs text-muted-foreground">
                  <th scope="col" className="py-1 text-left font-normal">Linha</th>
                  <th scope="col" className="py-1 text-right font-normal">Valor</th>
                </tr></thead>
                <tbody>
                  {detalhe.data.linhas.map((l, i) => (
                    <tr key={i} className="border-t">
                      <th scope="row" className="py-1 pr-2 text-left font-normal">
                        {l.codigo && <span className="num text-muted-foreground">{l.codigo} </span>}{l.descricao}
                      </th>
                      <td className="py-0.5">
                        <ValorComOrigem clienteId={clienteId} rotulo={l.descricao} valor={l.valor} arquivo={a.arquivo}
                                        registro={l.registro} linha={l.linha} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {detalhe.data.obrigacoes.length > 0 && (
                <table className="w-full text-sm">
                  <caption className="text-left text-xs font-semibold uppercase tracking-wide text-muted-foreground">Obrigações a recolher</caption>
                  <thead><tr className="text-xs text-muted-foreground">
                    <th scope="col" className="py-1 text-left font-normal">Receita</th>
                    <th scope="col" className="py-1 text-left font-normal">Vencimento</th>
                    <th scope="col" className="py-1 text-right font-normal">Valor</th>
                  </tr></thead>
                  <tbody>
                    {detalhe.data.obrigacoes.map((o) => (
                      <tr key={`${o.registro}-${o.linha}`} className="border-t">
                        <td className="num py-1">{o.codigoReceita ?? '—'}</td>
                        <td className="num py-1">{fmt.data(o.vencimento)}</td>
                        <td className="py-0.5">
                          <ValorComOrigem clienteId={clienteId} rotulo={`Obrigação ${o.codigoReceita ?? ''}`} valor={o.valor}
                                          arquivo={a.arquivo} registro={o.registro} linha={o.linha} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}
      </Collapse>
    </section>
  );
}
