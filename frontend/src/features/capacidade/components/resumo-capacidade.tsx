import { Link } from 'react-router-dom';
import { Gauge } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { Selo } from '@/components/selo';
import * as fmt from '@/lib/formatadores';
import { useResumoCapacidade } from '../api';

/** Visão geral do cliente: classe CAPAG oficial (e a estimada), classe do PR e a parcela sustentável. */
export function ResumoCapacidade({ clienteId }: { clienteId: number }) {
  const resumo = useResumoCapacidade(clienteId);
  const r = resumo.data;
  return (
    <Cartao aria-labelledby="titulo-resumo-capacidade" className="grid gap-3">
      <CartaoTitulo id="titulo-resumo-capacidade" acoes={
        <Button variant="outline" size="sm" asChild><Link to="../capacidade" viewTransition><Gauge aria-hidden /> Detalhes</Link></Button>}>
        Capacidade de pagamento
      </CartaoTitulo>
      {resumo.isPending || !r
        ? <Skeleton className="h-24" />
        : (
          <dl className="grid grid-cols-3 gap-3">
            <div>
              <dt className="text-sm text-muted-foreground">CAPAG oficial</dt>
              <dd className="font-serif text-2xl font-semibold">{r.capagOficial ?? '—'}</dd>
              {r.capagEstimada && <dd className="text-xs text-muted-foreground">estimada: {r.capagEstimada}</dd>}
              {r.revisaoVantajosa && <dd><Selo tom="warning">revisão vantajosa</Selo></dd>}
            </div>
            <div>
              <dt className="text-sm text-muted-foreground">Classe PR</dt>
              <dd className="font-serif text-2xl font-semibold">{r.classePr ?? '—'}</dd>
              <dd className="text-xs text-muted-foreground">oficial (RTI): {r.classePrOficial ?? '—'}</dd>
            </div>
            <div>
              <dt className="text-sm text-muted-foreground">Parcela sustentável</dt>
              <dd className="num text-lg font-semibold">{r.parcelaSustentavel == null ? 'não disponível' : fmt.moeda(r.parcelaSustentavel)}</dd>
              <dd className="text-xs text-muted-foreground">análise do consultor</dd>
            </div>
          </dl>
        )}
    </Cartao>
  );
}
