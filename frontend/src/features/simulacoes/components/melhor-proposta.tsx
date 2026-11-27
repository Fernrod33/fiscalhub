import { Link } from 'react-router-dom';
import { Calculator } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import * as fmt from '@/lib/formatadores';
import { useMelhorSimulacao } from '../api';

/** Visão geral do cliente: a melhor proposta da simulação salva mais recente. */
export function MelhorProposta({ clienteId }: { clienteId: number }) {
  const melhor = useMelhorSimulacao(clienteId);
  const m = melhor.data;
  return (
    <Cartao aria-labelledby="titulo-melhor-proposta" className="grid gap-3">
      <CartaoTitulo id="titulo-melhor-proposta" acoes={
        <Button variant="outline" size="sm" asChild><Link to="../simulacoes" viewTransition><Calculator aria-hidden /> Simulações</Link></Button>}>
        Melhor proposta salva
      </CartaoTitulo>
      {melhor.isPending ? <Skeleton className="h-16" />
        : !m ? <p className="text-sm text-muted-foreground">Nenhuma simulação salva.</p>
          : (
            <dl className="grid gap-1 text-sm">
              <dt className="text-muted-foreground">Recomendação (data-base {fmt.data(m.dataBase)}, v{m.versao})</dt>
              <dd>{m.recomendacao}</dd>
              <dt className="text-muted-foreground">Custo projetado</dt>
              <dd className="num text-lg font-semibold">{fmt.moeda(m.custoRecomendado)}</dd>
            </dl>
          )}
    </Cartao>
  );
}
