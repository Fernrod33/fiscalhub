import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { AnimatedNumber } from '@/components/motion';
import { Selo } from '@/components/selo';
import type { ResultadoCapacidade } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { mensagemErro } from '@/lib/http';
import { useSimulacaoPr } from '../api';

type Acao = 'manter' | 'quitar' | 'parcelar';
const ACOES: [Acao, string][] = [['manter', 'Manter'], ['quitar', 'Quitar'], ['parcelar', 'Parcelar']];

/**
 * Simulador de ações no PR: o consultor escolhe CDAs a quitar ou parcelar e o backend recalcula endividamento, pontos e
 * classe (nada é gravado). Mostra antes × depois.
 */
export function SimuladorPr({ clienteId, atual }: { clienteId: number; atual: ResultadoCapacidade }) {
  const [acoes, setAcoes] = useState<Record<string, Acao>>({});
  const simular = useSimulacaoPr(clienteId);
  const depois = simular.data;
  const escolhidas = (a: Acao) => Object.entries(acoes).filter(([, v]) => v === a).map(([k]) => k);

  const aoSimular = () => simular.mutate({ quitar: escolhidas('quitar'), parcelar: escolhidas('parcelar') }, {
    onError: (e) => toast.error(mensagemErro(e, 'Não foi possível simular.')),
  });

  return (
    <section aria-labelledby="titulo-simulador" className="grid gap-3 rounded-md border border-dashed p-3">
      <h3 id="titulo-simulador" className="font-semibold">Simular ações nas CDAs</h3>
      <p className="text-sm text-muted-foreground">Simulação: não altera os dados importados nem a classificação oficial.</p>
      <ul className="grid gap-2">
        {atual.itens.map((i) => (
          <li key={i.id} className="flex flex-wrap items-center justify-between gap-2 text-sm">
            <span className="num">{i.id} · {fmt.moeda(Number(i.valor))} · classe {i.classe ?? '—'}</span>
            <fieldset className="flex gap-1">
              <legend className="sr-only">Ação na CDA {i.id}</legend>
              {ACOES.map(([a, rotulo]) => (
                <label key={a} className="flex cursor-pointer items-center gap-1 rounded border px-2 py-0.5 has-checked:border-primary has-checked:bg-accent">
                  <input type="radio" name={`acao-${i.id}`} value={a} checked={(acoes[i.id] ?? 'manter') === a}
                         onChange={() => setAcoes((x) => ({ ...x, [i.id]: a }))} className="accent-(--primary)" />
                  {rotulo}
                </label>
              ))}
            </fieldset>
          </li>
        ))}
      </ul>
      <div>
        <Button type="button" variant="outline" onClick={aoSimular} disabled={simular.isPending}>
          {simular.isPending ? 'Simulando…' : 'Simular'}
        </Button>
      </div>
      {depois && (
        <div className="grid gap-2 sm:grid-cols-2" aria-live="polite" aria-label="Resultado da simulação">
          {([['Antes', atual], ['Depois (simulação)', depois]] as const).map(([rotulo, r]) => (
            <div key={rotulo} className="rounded-md bg-muted/50 p-3">
              <p className="text-sm text-muted-foreground">{rotulo}</p>
              <p className="flex items-baseline gap-2">
                <span className="text-sm">Soma</span>
                {r.valor == null ? <span>—</span> : <AnimatedNumber valor={r.valor} className="text-xl font-semibold" />}
                <span className="text-sm">· classe</span>
                <Selo tom={r.classe === 'A' || r.classe === 'B' ? 'success' : 'warning'}>{r.classe ?? 'não calculada'}</Selo>
              </p>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
