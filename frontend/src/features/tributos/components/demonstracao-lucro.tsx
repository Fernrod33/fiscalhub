import { useState } from 'react';
import { ChevronRight } from 'lucide-react';
import { Collapse } from '@/components/motion';
import { Skeleton } from '@/components/ui/skeleton';
import type { Apuracao, LinhaApuracao } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { cn } from '@/lib/utils';
import { useDetalheApuracao } from '../api';
import { ValorComOrigem } from './origem-arquivo';

/** Degraus da demonstração, na ordem: cada um soma as linhas do grupo (LUCRO e CALCULO mostram as linhas). */
const DEGRAUS: { grupo: string; rotulo: string; sinal: string }[] = [
  { grupo: 'LUCRO', rotulo: 'Lucro antes do tributo e totais do e-Lalur/e-Lacs', sinal: '' },
  { grupo: 'ADICAO', rotulo: 'Adições', sinal: '(+)' },
  { grupo: 'EXCLUSAO', rotulo: 'Exclusões', sinal: '(−)' },
  { grupo: 'COMPENSACAO', rotulo: 'Compensação de prejuízos / base negativa', sinal: '(−)' },
  { grupo: 'BASE', rotulo: 'Demonstração da base presumida', sinal: '' },
  { grupo: 'CALCULO', rotulo: 'Cálculo do tributo', sinal: '' },
];

/**
 * Demonstração em cascata de uma apuração de IRPJ ou CSLL (anual ou trimestral): do lucro contábil, pelas adições,
 * exclusões e compensações (Lucro Real) ou pela base presumida (Lucro Presumido), até a base e o cálculo do tributo.
 * Cada linha abre a origem (registro e linha da ECF).
 */
export function DemonstracaoLucro({ clienteId, apuracao }: { clienteId: number; apuracao: Apuracao }) {
  const detalhe = useDetalheApuracao(clienteId, apuracao.id);
  const [abertos, setAbertos] = useState<string[]>(['CALCULO']);
  if (detalhe.isPending || !detalhe.data) return <Skeleton className="h-40" />;
  const linhas = detalhe.data.linhas;
  const alternar = (grupo: string) => setAbertos(abertos.includes(grupo) ? abertos.filter((g) => g !== grupo) : [...abertos, grupo]);

  return (
    <div className="grid gap-1 text-sm">
      {DEGRAUS.map(({ grupo, rotulo, sinal }) => {
        const doGrupo = linhas.filter((l) => l.grupo === grupo);
        if (doGrupo.length === 0) return null;
        const aberto = abertos.includes(grupo);
        const id = `degrau-${apuracao.id}-${grupo}`;
        const total = ['ADICAO', 'EXCLUSAO', 'COMPENSACAO'].includes(grupo) ? doGrupo.reduce((s, l) => s + l.valor, 0) : null;
        return (
          <div key={grupo} className="rounded-md border">
            <button type="button" onClick={() => alternar(grupo)} aria-expanded={aberto} aria-controls={id}
                    className="flex w-full items-center gap-2 px-3 py-2 text-left hover:bg-accent">
              <ChevronRight className={cn('size-4 shrink-0 transition-transform duration-(--dur-fast)', aberto && 'rotate-90')} aria-hidden />
              <span className="flex-1 font-medium">{sinal && <span className="num text-muted-foreground">{sinal} </span>}{rotulo}</span>
              {total != null && <span className="num font-semibold">{fmt.moeda(total)}</span>}
            </button>
            <Collapse aberto={aberto} id={id}>
              <Linhas clienteId={clienteId} linhas={doGrupo} arquivo={apuracao.arquivo} />
            </Collapse>
          </div>
        );
      })}
      <div className="flex items-center justify-between rounded-md bg-accent px-3 py-2 font-semibold">
        <span>= A recolher</span>
        <span className="num">{fmt.moeda(apuracao.valorARecolher)}</span>
      </div>
    </div>
  );
}

function Linhas({ clienteId, linhas, arquivo }: { clienteId: number; linhas: LinhaApuracao[]; arquivo: string }) {
  return (
    <table className="w-full">
      <tbody>
        {linhas.map((l) => (
          <tr key={`${l.registro}-${l.linha}`} className="border-t">
            <th scope="row" className="py-1 pl-9 pr-2 text-left font-normal">
              {l.codigo && <span className="num text-muted-foreground">{l.codigo} </span>}{l.descricao}
            </th>
            <td className="w-40 py-0.5 pr-2">
              <ValorComOrigem clienteId={clienteId} rotulo={l.descricao} valor={l.valor} arquivo={arquivo} registro={l.registro} linha={l.linha} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
