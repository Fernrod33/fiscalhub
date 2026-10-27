import { useState } from 'react';
import { AlertTriangle, ChevronRight } from 'lucide-react';
import { Collapse } from '@/components/motion';
import { Selo } from '@/components/selo';
import { Skeleton } from '@/components/ui/skeleton';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import type { Apuracao, ItemConciliacao } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { CAMPOS_APURACAO, ORGAO_CREDOR, REGIMES, SITUACAO_DEBITO, STATUS_CONCILIACAO, TRIBUTOS } from '@/lib/rotulos';
import { cn } from '@/lib/utils';
import { ListaOrigens, ValorDocumento } from '@/features/situacao-fiscal/components/origem-documento';
import { useDetalheApuracao } from '../api';
import type { Celula } from '../matriz';
import { ValorComOrigem } from './origem-arquivo';

/**
 * Gaveta de uma célula da matriz: para cada período, o status da conciliação, apurado, declarado e pago com as origens
 * (arquivo, página ou registro, linha), a diferença, os acréscimos devidos pelo atraso e o débito correspondente na
 * situação fiscal; embaixo, o cálculo das apurações do período.
 */
export function DetalheCelula({ clienteId, celula, apuracoes, aoFechar }: {
  clienteId: number; celula: Celula | null; apuracoes: Apuracao[]; aoFechar: () => void;
}) {
  return (
    <Sheet open={celula != null} onOpenChange={(aberto) => !aberto && aoFechar()}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-xl">
        {celula && (
          <>
            <SheetHeader>
              <SheetTitle>{celula.linha.rotulo} · {celula.rotuloCompetencia}</SheetTitle>
              <SheetDescription>Apurado × declarado × pago, com a origem de cada valor</SheetDescription>
            </SheetHeader>
            <div className="grid gap-4 px-4 pb-6">
              {celula.itens.map((item) => (
                <ItemDetalhado key={item.id} clienteId={clienteId} item={item}
                               apuracoes={apuracoes.filter((a) => a.tributo === item.tributo && (a.uf ?? null) === (item.uf ?? null)
                                 && a.dataFim.slice(0, 7) === item.periodo)} />
              ))}
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}

function ItemDetalhado({ clienteId, item, apuracoes }: { clienteId: number; item: ItemConciliacao; apuracoes: Apuracao[] }) {
  const status = STATUS_CONCILIACAO[item.status];
  const rotulo = `${TRIBUTOS[item.tributo] ?? item.tributo} ${item.periodo.slice(5, 7)}/${item.periodo.slice(0, 4)}`;
  const a = item.acrescimos;
  return (
    <section aria-label={`Conciliação de ${rotulo}`} className="grid gap-3 rounded-md border p-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="font-semibold">{rotulo}</h3>
        <Selo tom={status.tom}>{status.rotulo}</Selo>
      </div>
      <p className="num text-xs text-muted-foreground">
        Vencimento {fmt.data(item.vencimento)}
        {item.pago?.dataPagamento && <> · pago em {fmt.data(item.pago.dataPagamento)}</>}
        {item.codigosReceita.length > 0 && <> · código {item.codigosReceita.join(', ')}</>}
      </p>
      <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-1 text-sm">
        {([['Apurado', item.apurado], ['Declarado', item.declarado], ['Pago', item.pago]] as const).map(([nome, v]) => (
          <div key={nome} className="contents">
            <dt className="text-muted-foreground">{nome}</dt>
            <dd><ValorDocumento clienteId={clienteId} rotulo={`${nome} — ${rotulo}`} valor={v?.valor} origens={v?.origens} /></dd>
          </div>
        ))}
        <dt className="font-semibold">Diferença</dt>
        <dd className={cn('num px-1 text-right font-semibold', status.tom === 'danger' && 'text-danger')}>{fmt.moeda(item.diferenca)}</dd>
      </dl>

      {a && (
        <div className="grid gap-1 rounded-md bg-muted/50 p-2 text-sm" aria-label="Acréscimos devidos">
          <p className="font-semibold">Acréscimos devidos pelo atraso</p>
          <p className="num text-xs text-muted-foreground">
            {fmt.inteiro(a.diasAtraso)} dias de {fmt.data(a.vencimento)} a {fmt.data(a.pagamento)} sobre {fmt.moeda(a.principal)}
          </p>
          <dl className="grid grid-cols-[1fr_auto] gap-x-4">
            <dt>Multa de mora ({fmt.percentual(a.percentualMulta)}; 0,33% ao dia, até 20%)</dt>
            <dd className="num text-right">{fmt.moeda(a.multa)}</dd>
            <dt>Juros ({fmt.percentual(a.percentualJuros)}; SELIC{a.selicDe ? ` ${a.selicDe} a ${a.selicAte}` : ''} + 1%)</dt>
            <dd className="num text-right">{fmt.moeda(a.juros)}</dd>
            <dt className="font-semibold">Total dos acréscimos</dt>
            <dd className="num text-right font-semibold">{fmt.moeda(a.multa + a.juros)}</dd>
          </dl>
        </div>
      )}
      {item.erroAcrescimos && (
        <p className="flex items-start gap-1.5 text-sm text-warning" role="note">
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />{item.erroAcrescimos}
        </p>
      )}

      {item.debitos.length > 0 && (
        <div className="grid gap-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Débito na situação fiscal</p>
          {item.debitos.map((d) => (
            <div key={d.id} className="grid gap-2 rounded-md border p-2 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span>{ORGAO_CREDOR[d.orgao]} · <span className="num">{fmt.moeda(d.valorTotal)}</span></span>
                <span className="flex gap-1">
                  <Selo tom={SITUACAO_DEBITO[d.situacao].tom}>{SITUACAO_DEBITO[d.situacao].rotulo}</Selo>
                  {d.impedeCertidao && <Selo tom="danger">Impede certidão</Selo>}
                </span>
              </div>
              <ListaOrigens clienteId={clienteId} origens={d.origens} />
            </div>
          ))}
        </div>
      )}

      {apuracoes.map((ap) => <ApuracaoDetalhada key={ap.id} clienteId={clienteId} apuracao={ap} />)}
    </section>
  );
}

function ApuracaoDetalhada({ clienteId, apuracao: a }: { clienteId: number; apuracao: Apuracao }) {
  const [calculoAberto, setCalculoAberto] = useState(false);
  const detalhe = useDetalheApuracao(clienteId, calculoAberto ? a.id : undefined);
  const idCalculo = `calculo-${a.id}`;
  return (
    <section aria-label={`${a.descricao} de ${fmt.data(a.dataInicio)} a ${fmt.data(a.dataFim)}`} className="grid gap-3 rounded-md border p-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="font-semibold">{a.descricao}</h3>
        {a.regime && <Selo tom="info">{REGIMES[a.regime] ?? a.regime}</Selo>}
      </div>
      <p className="num text-xs text-muted-foreground">{fmt.data(a.dataInicio)} a {fmt.data(a.dataFim)} · {a.arquivo}</p>
      <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-1 text-sm">
        {CAMPOS_APURACAO.filter(([campo]) => a[campo] != null).map(([campo, rotulo]) => (
          <div key={campo} className="contents">
            <dt className={cn('text-muted-foreground', campo === 'valorARecolher' && 'font-semibold text-foreground')}>{rotulo}</dt>
            <dd>
              <ValorComOrigem clienteId={clienteId} rotulo={`${rotulo} — ${a.descricao}`} valor={a[campo]} arquivo={a.arquivo}
                              registro={a.origem[campo]?.registro} linha={a.origem[campo]?.linha} destaque={campo === 'valorARecolher'} />
            </dd>
          </div>
        ))}
      </dl>
      <button type="button" onClick={() => setCalculoAberto(!calculoAberto)} aria-expanded={calculoAberto} aria-controls={idCalculo}
              className="flex items-center gap-1 text-sm font-medium text-primary">
        <ChevronRight className={cn('size-4 transition-transform duration-(--dur-fast)', calculoAberto && 'rotate-90')} aria-hidden />
        Linhas do cálculo e obrigações
      </button>
      <Collapse aberto={calculoAberto} id={idCalculo}>
        {detalhe.isPending || !detalhe.data
          ? <Skeleton className="h-24" />
          : (
            <div className="grid gap-3">
              <table className="w-full text-sm">
                <caption className="sr-only">Linhas do cálculo</caption>
                <thead><tr className="text-xs text-muted-foreground">
                  <th scope="col" className="py-1 text-left font-normal">Linha</th>
                  <th scope="col" className="py-1 text-right font-normal">Valor</th>
                </tr></thead>
                <tbody>
                  {detalhe.data.linhas.map((l, i) => (
                    <tr key={i} className="border-t">
                      <th scope="row" className="py-1 pr-2 text-left font-normal">
                        {l.codigo && <span className="num text-muted-foreground">{l.codigo} </span>}{l.descricao}
                      </th>
                      <td className="py-0.5">
                        <ValorComOrigem clienteId={clienteId} rotulo={l.descricao} valor={l.valor} arquivo={a.arquivo}
                                        registro={l.registro} linha={l.linha} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {detalhe.data.obrigacoes.length > 0 && (
                <table className="w-full text-sm">
                  <caption className="text-left text-xs font-semibold uppercase tracking-wide text-muted-foreground">Obrigações a recolher</caption>
                  <thead><tr className="text-xs text-muted-foreground">
                    <th scope="col" className="py-1 text-left font-normal">Receita</th>
                    <th scope="col" className="py-1 text-left font-normal">Vencimento</th>
                    <th scope="col" className="py-1 text-right font-normal">Valor</th>
                  </tr></thead>
                  <tbody>
                    {detalhe.data.obrigacoes.map((o) => (
                      <tr key={`${o.registro}-${o.linha}`} className="border-t">
                        <td className="num py-1">{o.codigoReceita ?? '—'}</td>
                        <td className="num py-1">{fmt.data(o.vencimento)}</td>
                        <td className="py-0.5">
                          <ValorComOrigem clienteId={clienteId} rotulo={`Obrigação ${o.codigoReceita ?? ''}`} valor={o.valor}
                                          arquivo={a.arquivo} registro={o.registro} linha={o.linha} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}
      </Collapse>
    </section>
  );
}
