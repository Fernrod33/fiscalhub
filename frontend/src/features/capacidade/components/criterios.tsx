import { useId, useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { Collapse } from '@/components/motion';
import { Selo, type Tom } from '@/components/selo';
import { ValorOrigem } from '@/components/valor-origem';
import type { CriterioCapacidade, ResultadoCapacidade } from '@/lib/api/tipos';
import { cn } from '@/lib/utils';
import { ListaOrigens } from '@/features/situacao-fiscal/components/origem-documento';
import { valorCriterio } from '../formatar';

const TOM_ROTULO: Record<ResultadoCapacidade['rotulo'], { tom: Tom; curto: string }> = {
  OFICIAL: { tom: 'success', curto: 'Oficial' },
  ESTIMATIVA: { tom: 'warning', curto: 'Estimativa' },
  GERENCIAL: { tom: 'info', curto: 'Gerencial' },
};

/** Selo do rótulo (oficial, estimativa, gerencial) com o texto completo do backend ao lado. */
export function SeloRotulo({ resultado }: { resultado: Pick<ResultadoCapacidade, 'rotulo' | 'rotuloTexto'> }) {
  const r = TOM_ROTULO[resultado.rotulo];
  return (
    <span className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
      <Selo tom={r.tom}>{r.curto}</Selo> {resultado.rotuloTexto}
    </span>
  );
}

/** Critérios recolhíveis: valor, pontos ou índice, faixa, fundamento (norma e artigo) e a origem do insumo. */
export function TabelaCriterios({ clienteId, titulo, criterios, abertoInicial = false }: {
  clienteId: number; titulo: string; criterios: CriterioCapacidade[]; abertoInicial?: boolean;
}) {
  const [aberto, setAberto] = useState(abertoInicial);
  const id = useId();
  return (
    <div className="grid gap-2">
      <button type="button" aria-expanded={aberto} aria-controls={id} onClick={() => setAberto((a) => !a)}
              className="flex w-fit items-center gap-1 text-sm font-medium text-primary underline-offset-4 hover:underline">
        <ChevronDown aria-hidden className={cn('size-4 transition-transform', aberto && 'rotate-180')} />
        {aberto ? 'Ocultar' : 'Ver'} {titulo.toLowerCase()} ({criterios.length})
      </button>
      <Collapse aberto={aberto} id={id}>
        <div className="overflow-x-auto">
          <table className="w-full text-sm" aria-label={titulo}>
            <thead>
              <tr className="border-b text-left text-xs text-muted-foreground">
                <th scope="col" className="py-1 pr-2 font-medium">Critério</th>
                <th scope="col" className="py-1 pr-2 text-right font-medium">Valor</th>
                <th scope="col" className="py-1 pr-2 text-right font-medium">Pontos / índice</th>
                <th scope="col" className="py-1 pr-2 font-medium">Faixa ou cálculo</th>
                <th scope="col" className="py-1 pr-2 font-medium">Fundamento</th>
              </tr>
            </thead>
            <tbody>
              {criterios.map((c) => (
                <tr key={c.nome} className="border-b align-top last:border-0">
                  <th scope="row" className="py-1.5 pr-2 text-left font-normal">{c.nome}</th>
                  <td className="py-1.5 pr-2 text-right">
                    {c.naoDisponivel
                      ? <span className="text-warning">{c.naoDisponivel}</span>
                      : c.origens.length
                        ? (
                          <ValorOrigem rotulo={`Origem de ${c.nome}: ${valorCriterio(c)}`} texto={valorCriterio(c)}>
                            {() => <ListaOrigens clienteId={clienteId} origens={c.origens} />}
                          </ValorOrigem>
                        )
                        : <span className="num">{valorCriterio(c)}</span>}
                  </td>
                  <td className="num py-1.5 pr-2 text-right">{c.pontos ?? '—'}</td>
                  <td className="py-1.5 pr-2 text-muted-foreground">{c.faixa ?? '—'}</td>
                  <td className="py-1.5 pr-2 text-xs text-muted-foreground">{c.fundamento}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Collapse>
    </div>
  );
}
