import { Link } from 'react-router-dom';
import { BookOpen } from 'lucide-react';
import { Skeleton } from '@/components/ui/skeleton';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { EstadoVazio } from '@/components/estado-vazio';
import { Selo } from '@/components/selo';
import { TextoTruncado } from '@/components/texto-truncado';
import * as fmt from '@/lib/formatadores';
import { ORIGENS_ESCRITURACAO, SITUACOES_ESCRITURACAO } from '@/lib/rotulos';
import { useEscrituracoes } from '../api';

/** Exercícios disponíveis do cliente: cada escrituração importada (ECD ou balancete) e se está vigente ou substituída. */
export function EscrituracoesCliente({ clienteId }: { clienteId: number }) {
  const escrituracoes = useEscrituracoes(clienteId);
  return (
    <Cartao aria-labelledby="titulo-exercicios">
      <CartaoTitulo id="titulo-exercicios">Exercícios</CartaoTitulo>
      {escrituracoes.isPending
        ? <div className="grid gap-2">{Array.from({ length: 2 }, (_, i) => <Skeleton key={i} className="h-10" />)}</div>
        : !escrituracoes.data?.length
          ? <EstadoVazio icone={BookOpen} titulo="Nenhum exercício importado" descricao="Importe a ECD ou um balancete para ver as demonstrações." />
          : (
            <ul className="divide-y">
              {escrituracoes.data.map((e) => (
                <li key={e.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 py-2">
                  <Link to={`/clientes/${clienteId}/demonstracoes`} viewTransition className="num font-semibold text-primary underline-offset-4 hover:underline">
                    {e.dataFim.slice(0, 4)}
                  </Link>
                  <span className="text-muted-foreground">{ORIGENS_ESCRITURACAO[e.origem] ?? e.origem}{e.versaoLeiaute ? ` · leiaute ${e.versaoLeiaute}` : ''}</span>
                  <Selo tom={SITUACOES_ESCRITURACAO[e.situacao]?.tom ?? 'info'}>{SITUACOES_ESCRITURACAO[e.situacao]?.rotulo ?? e.situacao}</Selo>
                  <span className="num whitespace-nowrap text-muted-foreground">{fmt.data(e.dataInicio)} a {fmt.data(e.dataFim)}</span>
                  <span className="min-w-0 flex-1 text-xs text-muted-foreground"><TextoTruncado texto={e.arquivo} /></span>
                </li>
              ))}
            </ul>
          )}
    </Cartao>
  );
}
