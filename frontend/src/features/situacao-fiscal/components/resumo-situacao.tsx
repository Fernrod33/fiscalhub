import { Link } from 'react-router-dom';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { AnimatedNumber, Stagger, StaggerItem } from '@/components/motion';
import { Selo } from '@/components/selo';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import * as fmt from '@/lib/formatadores';
import { ORGAO_CREDOR } from '@/lib/rotulos';
import { cn } from '@/lib/utils';
import { useConciliacao, useDebitos } from '../api';
import { totaisPorOrgao } from '../situacao';

/** Visão geral: total dos débitos por órgão, divergências da conciliação e situação para certidão. */
export function ResumoSituacao({ clienteId }: { clienteId: number }) {
  const debitos = useDebitos(clienteId);
  const conciliacao = useConciliacao(clienteId);
  const lista = debitos.data ?? [];
  const impeditivos = lista.filter((d) => d.impedeCertidao).length;
  const divergencias = (conciliacao.data?.itens ?? []).filter((i) => i.status !== 'CONCILIADO').length;

  return (
    <Cartao aria-labelledby="titulo-resumo-fiscal">
      <CartaoTitulo id="titulo-resumo-fiscal" acoes={
        <Button variant="outline" size="sm" asChild><Link to="../situacao-fiscal" viewTransition>Ver situação fiscal</Link></Button>}>
        Situação fiscal
      </CartaoTitulo>
      {debitos.isPending || conciliacao.isPending
        ? <div className="grid grid-cols-2 gap-2">{Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-14" />)}</div>
        : (
          <Stagger as="ul" className="grid grid-cols-2 gap-2">
            <StaggerItem as="li" className="col-span-2 flex items-center justify-between gap-2 rounded-md border p-3">
              <span className="text-muted-foreground">Certidão</span>
              {impeditivos > 0
                ? <Selo tom="danger">{impeditivos === 1 ? '1 débito impede' : `${impeditivos} débitos impedem`}</Selo>
                : <Selo tom="success">Sem impeditivos importados</Selo>}
            </StaggerItem>
            {totaisPorOrgao(lista).map(({ orgao, total }) => (
              <StaggerItem as="li" key={orgao} className="rounded-md border p-3">
                <p className="text-muted-foreground">Débitos {ORGAO_CREDOR[orgao]}</p>
                <AnimatedNumber valor={total} formatar={fmt.moeda} className="text-xl font-semibold" />
              </StaggerItem>
            ))}
            <StaggerItem as="li" className="rounded-md border p-3">
              <p className="text-muted-foreground">Divergências na conciliação</p>
              {/* cor só quando indica estado: há divergência */}
              <AnimatedNumber valor={divergencias} className={cn('text-xl font-semibold', divergencias > 0 && 'text-danger')} />
            </StaggerItem>
          </Stagger>
        )}
    </Cartao>
  );
}
