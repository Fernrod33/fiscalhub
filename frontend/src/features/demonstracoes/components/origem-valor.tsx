import { useState } from 'react';
import { Link } from 'react-router-dom';
import { FileText } from 'lucide-react';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Skeleton } from '@/components/ui/skeleton';
import * as fmt from '@/lib/formatadores';
import { cn } from '@/lib/utils';
import { useOrigem } from '../api';

/**
 * Um valor da demonstração que abre a origem: arquivo, registro e linha (publicada) ou as contas da empresa que
 * compõem a linha, cada saldo com registro e linha do arquivo (referencial).
 */
export function OrigemValor({ clienteId, linhaId, valor, descricao, exercicio, destaque }: {
  clienteId: number; linhaId: number; valor: number | null | undefined; descricao: string; exercicio: number; destaque?: boolean;
}) {
  const [aberto, setAberto] = useState(false);
  const origem = useOrigem(clienteId, linhaId, aberto);
  const o = origem.data;

  return (
    <Popover open={aberto} onOpenChange={setAberto}>
      <PopoverTrigger asChild>
        <button type="button" aria-label={`Origem de ${descricao} em ${exercicio}: ${fmt.saldo(valor)}`}
                className={cn('num w-full whitespace-nowrap rounded-sm px-1 text-right underline-offset-4 hover:bg-accent hover:underline focus-visible:outline-2',
                  destaque && 'font-semibold')}>
          {fmt.saldo(valor)}
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-96 max-w-[calc(100vw-2rem)]" align="end">
        {origem.isPending || !o
          ? <div className="grid gap-2"><Skeleton className="h-4 w-48" /><Skeleton className="h-16" /></div>
          : (
            <div className="grid gap-3 text-sm">
              <div>
                <p className="font-semibold">{o.descricao}</p>
                <p className="num text-xs text-muted-foreground">{o.codigo} · {exercicio} · {o.origem === 'REFERENCIAL' ? 'referencial' : 'publicada'}</p>
              </div>
              <Link to={`/clientes/${clienteId}/importacoes`} viewTransition className="flex items-center gap-1.5 text-primary underline-offset-4 hover:underline">
                <FileText className="size-4 shrink-0" aria-hidden /><span className="truncate">{o.arquivo}</span>
              </Link>
              {o.origem === 'PUBLICADA'
                ? <p className="num">Registro {o.registro} · linha {fmt.inteiro(o.linha)}</p>
                : o.contas.length === 0
                  ? <p className="text-muted-foreground">Nenhuma conta da empresa mapeada para esta linha.</p>
                  : (
                    <ul className="grid max-h-64 gap-2 overflow-y-auto" aria-label="Contas que compõem o valor">
                      {o.contas.map((c) => (
                        <li key={c.contaId} className="rounded-md border p-2">
                          <div className="flex items-baseline justify-between gap-2">
                            <span className="min-w-0 truncate"><span className="num text-muted-foreground">{c.codigo}</span> {c.nome}</span>
                            <span className="num whitespace-nowrap">{fmt.saldo(c.valor)}</span>
                          </div>
                          <ul className="num mt-1 text-xs text-muted-foreground">
                            {c.saldos.map((s) => (
                              <li key={`${s.registro}-${s.linha}`}>
                                {s.registro} · linha {fmt.inteiro(s.linha)}{s.centroCusto ? ` · centro ${s.centroCusto}` : ''} · {fmt.saldo(s.valor)}
                              </li>
                            ))}
                          </ul>
                        </li>
                      ))}
                    </ul>
                  )}
            </div>
          )}
      </PopoverContent>
    </Popover>
  );
}
