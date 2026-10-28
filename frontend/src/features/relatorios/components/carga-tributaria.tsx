import { Percent } from 'lucide-react';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { EstadoVazio } from '@/components/estado-vazio';
import { Grafico } from '@/components/grafico';
import { Skeleton } from '@/components/ui/skeleton';
import * as fmt from '@/lib/formatadores';
import { useCargaTributaria } from '../api';

/**
 * Carga tributária efetiva por exercício (visão geral do cliente). Os números são os do backend (o mesmo serviço do
 * relatório); exercício sem insumo fica de fora do gráfico e aparece com o que falta importar, sem estimativa.
 */
export function CargaTributaria({ clienteId }: { clienteId: number }) {
  const carga = useCargaTributaria(clienteId);
  const exercicios = carga.data ?? [];
  const comCarga = exercicios.filter((e) => e.carga != null);
  const semCarga = exercicios.filter((e) => e.carga == null);

  return (
    <Cartao aria-labelledby="titulo-carga" className="grid gap-3">
      <CartaoTitulo id="titulo-carga">Carga tributária efetiva</CartaoTitulo>
      {carga.isPending
        ? <Skeleton className="h-56" />
        : comCarga.length === 0
          ? <EstadoVazio icone={Percent} titulo="Carga não disponível"
                         descricao="É preciso a receita bruta (ECD ou ECF) e os 12 meses das escriturações fiscais de um exercício. Nada é estimado." />
          : <Grafico titulo="Carga tributária efetiva por exercício (tributos ÷ receita bruta)" tipo="linha" formato="percentual"
                     categoria="exercicio" series={[{ chave: 'carga', nome: 'Carga efetiva' }]}
                     dados={exercicios.map((e) => ({ exercicio: String(e.exercicio), carga: e.carga ?? null }))} />}
      {semCarga.length > 0 && (
        <ul className="grid gap-1 text-sm" aria-label="Exercícios sem carga calculada">
          {semCarga.map((e) => (
            <li key={e.exercicio}>
              <span className="num font-medium">{e.exercicio}</span>{' '}
              <span className="text-muted-foreground">não disponível — {e.faltando[0] ?? 'falta insumo'}{e.faltando.length > 1 && ` (+${e.faltando.length - 1})`}</span>
            </li>
          ))}
        </ul>
      )}
      {comCarga.length > 0 && (
        <p className="num text-sm text-muted-foreground">
          Último: {comCarga.at(-1)!.exercicio} · {fmt.percentual(comCarga.at(-1)!.carga)} de {fmt.moeda(comCarga.at(-1)!.receitaBruta)}
        </p>
      )}
    </Cartao>
  );
}
