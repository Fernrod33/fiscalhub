import { useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { ValorOrigem } from '@/components/valor-origem';
import * as fmt from '@/lib/formatadores';
import { mensagemErro } from '@/lib/http';
import { useDfc, useDfcExercicios } from '@/features/capacidade/api';

const MESES = Array.from({ length: 12 }, (_, i) => i + 1);

/**
 * DFC pelo método direto (lançamentos I200/I250 da ECD, categoria pela contrapartida no plano referencial): mês a mês,
 * total e os lançamentos de cada linha. A conferência com os saldos de caixa (I155) aparece como alerta se não bater.
 */
export function DfcDireta({ clienteId }: { clienteId: number }) {
  const anos = useDfcExercicios(clienteId).data ?? [];
  const [ano, setAno] = useState<number>();
  const exercicio = ano ?? anos.at(-1);
  const dfc = useDfc(clienteId, exercicio);
  if (anos.length === 0) return <p className="text-sm text-muted-foreground">A DFC direta vem dos lançamentos da ECD: importe a ECD na aba Importações.</p>;
  return (
    <div className="grid gap-3">
      <label className="flex items-center gap-2 text-sm">
        <span className="text-muted-foreground">Exercício</span>
        <select value={exercicio} onChange={(e) => setAno(Number(e.target.value))} className="h-8 rounded-md border bg-background px-2">
          {anos.map((a) => <option key={a} value={a}>{a}</option>)}
        </select>
      </label>
      {dfc.isPending ? <Skeleton className="h-64" /> : !dfc.data ? <p className="text-danger">{mensagemErro(dfc.error, 'DFC indisponível.')}</p> : (
        <>
          {dfc.data.alerta && (
            <Alert><AlertTriangle aria-hidden className="text-warning" /><AlertTitle>Conferência do caixa</AlertTitle>
              <AlertDescription>{dfc.data.alerta}</AlertDescription></Alert>
          )}
          <div className="overflow-x-auto rounded-md border">
            <table className="w-full text-sm" aria-label={`DFC pelo método direto de ${dfc.data.exercicio}`}>
              <thead className="bg-muted/50 text-xs text-muted-foreground">
                <tr>
                  <th scope="col" className="px-2 py-1 text-left font-medium">Linha</th>
                  {MESES.map((m) => <th key={m} scope="col" className="px-2 py-1 text-right font-medium">{String(m).padStart(2, '0')}</th>)}
                  <th scope="col" className="px-2 py-1 text-right font-medium">Total</th>
                </tr>
              </thead>
              <tbody>
                {dfc.data.linhas.map((l) => (
                  <tr key={l.categoria} className="border-t">
                    <th scope="row" className="px-2 py-1 text-left font-normal">{l.rotulo}<span className="block text-xs text-muted-foreground">{l.atividade.toLowerCase()}</span></th>
                    {MESES.map((m) => <td key={m} className="num whitespace-nowrap px-2 py-1 text-right">{l.meses[m] == null ? '—' : fmt.decimal(l.meses[m])}</td>)}
                    <td className="px-2 py-1 text-right">
                      <ValorOrigem rotulo={`Lançamentos de ${l.rotulo}: ${fmt.moeda(l.total)}`} texto={fmt.moeda(l.total)}>
                        {() => (
                          <div className="grid max-h-72 gap-1 overflow-y-auto text-xs">
                            <p className="font-semibold">{dfc.data.arquivo}</p>
                            {l.origens.map((o, i) => (
                              <p key={`${o.numero}-${i}`} className="num">Lançamento {o.numero} · {fmt.data(o.data)} · linha {o.linha} · {fmt.moeda(o.valor)}</p>
                            ))}
                          </div>
                        )}
                      </ValorOrigem>
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot className="border-t-2 font-semibold">
                <tr>
                  <th scope="row" className="px-2 py-1 text-left">Variação do caixa</th>
                  <td colSpan={12} className="num px-2 py-1 text-right text-xs font-normal text-muted-foreground">
                    saldos de caixa (I155): {fmt.moeda(dfc.data.variacaoSaldos)}
                  </td>
                  <td className="num px-2 py-1 text-right">{fmt.moeda(dfc.data.variacaoDfc)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
