import { useState } from 'react';
import { Check, Search } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Selo } from '@/components/selo';
import { Skeleton } from '@/components/ui/skeleton';
import type { ContaDepara } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { mensagemErro } from '@/lib/http';
import { PLANOS_REFERENCIAIS } from '@/lib/rotulos';
import { useAssociar, useBuscaReferencial, useDepara } from '../api';

type Associacao = { codigo: string; descricao?: string } | null;

/** Escolha de uma conta referencial com busca no plano oficial (código ou descrição). */
function BuscaReferencial({ plano, exercicio, conta, aoEscolher }: {
  plano: string; exercicio: number; conta: string; aoEscolher: (codigo: string, descricao: string) => void;
}) {
  const [aberto, setAberto] = useState(false);
  const [busca, setBusca] = useState('');
  const resultado = useBuscaReferencial(plano, exercicio, busca);
  return (
    <Popover open={aberto} onOpenChange={setAberto}>
      <PopoverTrigger asChild>
        <Button variant="outline" size="sm" aria-label={`Escolher conta referencial para ${conta}`}><Search aria-hidden /> Escolher</Button>
      </PopoverTrigger>
      <PopoverContent className="w-[28rem] p-0" align="end">
        <Command shouldFilter={false}>
          <CommandInput placeholder="Código ou descrição da conta referencial" value={busca} onValueChange={setBusca} />
          <CommandList>
            <CommandEmpty>{resultado.isFetching ? 'Buscando…' : 'Nenhuma conta analítica encontrada.'}</CommandEmpty>
            <CommandGroup>
              {(resultado.data ?? []).map((r) => (
                <CommandItem key={r.codigo} value={r.codigo} onSelect={() => { aoEscolher(r.codigo, r.descricao); setAberto(false); }}>
                  <span className="num w-36 shrink-0 text-muted-foreground">{r.codigo}</span>
                  <span className="truncate">{r.descricao}</span>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}

/**
 * Passo 3: de-para das contas do balancete para o plano referencial da RFB. As associações já salvas e as da ECD do
 * cliente vêm aplicadas; contas sem associação ficam fora do referencial (nunca há chute).
 */
export function PassoDepara({ documentoId, exercicio, aoSalvar }: { documentoId: number; exercicio: number; aoSalvar: () => void }) {
  const depara = useDepara(documentoId, true);
  const associar = useAssociar(documentoId);
  const [mudancas, setMudancas] = useState<Record<string, Associacao>>({});

  if (depara.isPending) return <div className="grid gap-2">{Array.from({ length: 5 }, (_, i) => <Skeleton key={i} className="h-10" />)}</div>;
  if (!depara.data) return <p className="text-danger">{mensagemErro(depara.error, 'Não foi possível carregar as contas.')}</p>;

  const plano = depara.data.planoReferencial;
  const atual = (c: ContaDepara): Associacao => (c.codigo in mudancas ? mudancas[c.codigo]
    : c.codigoReferencial ? { codigo: c.codigoReferencial, descricao: c.descricaoReferencial ?? undefined } : null);
  const pendentes = depara.data.contas.filter((c) => !atual(c)).length;

  const salvar = () => associar.mutate(
    Object.entries(mudancas).map(([codigoConta, a]) => ({ codigoConta, codigoReferencial: a?.codigo ?? null })),
    { onSuccess: () => { toast.success('De-para salvo. O balancete vai ser processado com ele.'); setMudancas({}); aoSalvar(); },
      onError: (e) => toast.error(mensagemErro(e, 'Não foi possível salvar o de-para.')) });

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-muted-foreground">Plano referencial: {PLANOS_REFERENCIAIS[plano] ?? plano} · tabelas {depara.data.tabela}</p>
        <p role="status" aria-live="polite" className="num font-medium">
          {pendentes === 0 ? 'Todas as contas associadas' : `${fmt.inteiro(pendentes)} conta(s) pendente(s)`}
        </p>
      </div>
      <div className="overflow-auto rounded-md border">
        <table className="w-full">
          <caption className="sr-only">De-para das contas do balancete</caption>
          <thead className="bg-muted text-xs uppercase text-muted-foreground">
            <tr>
              <th scope="col" className="px-3 py-2 text-left">Conta</th>
              <th scope="col" className="px-3 py-2 text-right">Saldo final</th>
              <th scope="col" className="px-3 py-2 text-left">Conta referencial</th>
              <th scope="col" className="px-3 py-2"><span className="sr-only">Ações</span></th>
            </tr>
          </thead>
          <tbody>
            {depara.data.contas.map((c) => {
              const a = atual(c);
              return (
                <tr key={c.codigo} className="border-t">
                  <td className="px-3 py-2"><span className="num text-muted-foreground">{c.codigo}</span> {c.nome}</td>
                  <td className="num whitespace-nowrap px-3 py-2 text-right">{fmt.saldo(c.saldoFinal)}</td>
                  <td className="px-3 py-2">
                    {a ? <span><span className="num">{a.codigo}</span> {a.descricao}</span> : <Selo tom="warning">Pendente</Selo>}
                  </td>
                  <td className="whitespace-nowrap px-3 py-2 text-right">
                    <div className="flex justify-end gap-1">
                      {!a && c.sugestaoEcd && (
                        <Button variant="ghost" size="sm" onClick={() => setMudancas((m) => ({ ...m, [c.codigo]: { codigo: c.sugestaoEcd! } }))}>
                          <Check aria-hidden /> Usar o da ECD ({c.sugestaoEcd})
                        </Button>
                      )}
                      <BuscaReferencial plano={plano} exercicio={exercicio} conta={`${c.codigo} ${c.nome}`}
                                        aoEscolher={(codigo, descricao) => setMudancas((m) => ({ ...m, [c.codigo]: { codigo, descricao } }))} />
                      {a && <Button variant="ghost" size="sm" onClick={() => setMudancas((m) => ({ ...m, [c.codigo]: null }))}>Desassociar</Button>}
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <div className="flex justify-end">
        <Button disabled={Object.keys(mudancas).length === 0 || associar.isPending} onClick={salvar}>Salvar de-para</Button>
      </div>
    </div>
  );
}
