import { Link } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { AnimatedNumber, Stagger, StaggerItem } from '@/components/motion';
import * as fmt from '@/lib/formatadores';
import { usePontos } from '../api';
import { SeloSeveridade } from './severidade';
import { SEVERIDADES } from '../rotulos';

/** Visão geral do cliente: achados abertos por severidade e os três mais graves. */
export function ResumoPontos({ clienteId }: { clienteId: number }) {
  const pontos = usePontos(clienteId);
  const abertos = (pontos.data ?? []).filter((p) => p.estado === 'NOVO' || p.estado === 'EM_ANALISE');
  return (
    <Cartao aria-labelledby="titulo-resumo-pontos" className="grid gap-3">
      <CartaoTitulo id="titulo-resumo-pontos" acoes={
        <Button variant="outline" size="sm" asChild><Link to="../pontos-de-atencao" viewTransition><AlertTriangle aria-hidden /> Ver todos</Link></Button>}>
        Pontos de atenção
      </CartaoTitulo>
      {pontos.isPending ? <Skeleton className="h-24" /> : (
        <>
          <dl className="grid grid-cols-4 gap-2">
            {(Object.keys(SEVERIDADES) as (keyof typeof SEVERIDADES)[]).map((s) => (
              <div key={s}>
                <dt className="text-sm text-muted-foreground">{SEVERIDADES[s].rotulo}</dt>
                <dd className="font-serif text-2xl font-semibold"><AnimatedNumber valor={abertos.filter((p) => p.severidade === s).length} /></dd>
              </div>
            ))}
          </dl>
          {abertos.length > 0 && (
            <Stagger as="ul" className="grid gap-1 text-sm">
              {abertos.slice(0, 3).map((p) => (
                <StaggerItem as="li" key={p.id} className="flex flex-wrap items-center gap-2">
                  <SeloSeveridade severidade={p.severidade} /> <span>{p.titulo}</span>
                  {p.valor != null && <span className="num ml-auto">{fmt.moeda(p.valor)}</span>}
                </StaggerItem>
              ))}
            </Stagger>
          )}
        </>
      )}
    </Cartao>
  );
}
