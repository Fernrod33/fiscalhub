import { useId, useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { Collapse } from '@/components/motion';
import { Grafico } from '@/components/grafico';
import { Selo } from '@/components/selo';
import { ValorOrigem } from '@/components/valor-origem';
import type { ResultadoModalidade } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { cn } from '@/lib/utils';
import { ListaOrigens } from '@/features/situacao-fiscal/components/origem-documento';

const NUM = 'num py-1.5 pr-2 text-right';

/** Expandir/recolher com o botão acessível (aria-expanded/controls) e a animação do Collapse. */
function Recolhivel({ titulo, children, abertoInicial = false }: { titulo: string; children: React.ReactNode; abertoInicial?: boolean }) {
  const [aberto, setAberto] = useState(abertoInicial);
  const id = useId();
  return (
    <div className="grid gap-2">
      <button type="button" aria-expanded={aberto} aria-controls={id} onClick={() => setAberto((a) => !a)}
              className="flex w-fit items-center gap-1 text-sm font-medium text-primary underline-offset-4 hover:underline">
        <ChevronDown aria-hidden className={cn('size-4 transition-transform', aberto && 'rotate-180')} />
        {aberto ? 'Ocultar' : 'Ver'} {titulo.toLowerCase()}
      </button>
      <Collapse aberto={aberto} id={id}>{children}</Collapse>
    </div>
  );
}

/** Uma modalidade num cenário: débitos com a origem, cronograma (nominal × projetado), gráfico, memória, elegibilidade e fontes. */
export function DetalheModalidade({ clienteId, m }: { clienteId: number; m: ResultadoModalidade }) {
  const saldos = m.cronograma.map((p) => ({ prestacao: String(p.numero), nominal: p.saldoNominal, projetado: p.saldoProjetado }));
  return (
    <div className="grid gap-4">
      {m.bloqueio && <p role="alert" className="rounded-md border border-warning p-3 text-sm">{m.bloqueio}</p>}
      {m.avisos.length > 0 && (
        <ul className="grid list-disc gap-1 pl-5 text-sm text-muted-foreground" aria-label="Avisos da modalidade">
          {m.avisos.map((a) => <li key={a}>{a}</li>)}
        </ul>
      )}

      {m.debitos.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-sm" aria-label={`Débitos consolidados — ${m.nome}`}>
            <thead>
              <tr className="border-b text-left text-xs text-muted-foreground">
                <th scope="col" className="py-1 pr-2 font-medium">Débito</th>
                <th scope="col" className="py-1 pr-2 font-medium">Vencimento</th>
                <th scope="col" className="py-1 pr-2 text-right font-medium">Principal</th>
                <th scope="col" className="py-1 pr-2 text-right font-medium">Multa</th>
                <th scope="col" className="py-1 pr-2 text-right font-medium">Juros</th>
                <th scope="col" className="py-1 pr-2 text-right font-medium">Encargo</th>
                <th scope="col" className="py-1 pr-2 text-right font-medium">Desconto</th>
                <th scope="col" className="py-1 pr-2 text-right font-medium">Total</th>
              </tr>
            </thead>
            <tbody>
              {m.debitos.map((d) => (
                <tr key={d.debitoId} className="border-b align-top last:border-0">
                  <th scope="row" className="py-1.5 pr-2 text-left font-normal">
                    {d.descricao}<span className="block text-xs text-muted-foreground">{d.posicao}</span>
                  </th>
                  <td className="py-1.5 pr-2">{fmt.data(d.vencimento)}</td>
                  <td className={NUM}>
                    {d.origem
                      ? <ValorOrigem rotulo={`Origem de ${d.descricao}: ${fmt.moeda(d.principal)}`} texto={fmt.moeda(d.principal)}>
                          {() => <ListaOrigens clienteId={clienteId} origens={[d.origem!]} />}
                        </ValorOrigem>
                      : fmt.moeda(d.principal)}
                  </td>
                  <td className={NUM}>{fmt.moeda(d.multa)}{d.percentualMulta != null && <span className="block text-xs text-muted-foreground">{fmt.percentual(d.percentualMulta)}</span>}</td>
                  <td className={NUM}>{fmt.moeda(d.juros)}{d.percentualJuros != null && <span className="block text-xs text-muted-foreground">{fmt.percentual(d.percentualJuros)}</span>}</td>
                  <td className={NUM}>{fmt.moeda(d.encargo)}</td>
                  <td className={NUM}>{d.desconto ? fmt.moeda(-d.desconto) : '—'}</td>
                  <td className={cn(NUM, 'font-semibold')}>{fmt.moeda(d.total)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {m.cronograma.length > 1 && (
        <Grafico titulo={`Saldo nominal × projetado — ${m.nome}`} tipo="linha" dados={saldos} categoria="prestacao" formato="moeda"
                 series={[{ chave: 'nominal', nome: 'Saldo nominal' }, { chave: 'projetado', nome: 'Saldo projetado' }]} />
      )}

      <Recolhivel titulo={`Cronograma (${m.cronograma.length} prestações)`}>
        <div className="max-h-96 overflow-auto">
          <table className="w-full text-sm" aria-label={`Cronograma — ${m.nome}`}>
            <thead className="sticky top-0 bg-surface">
              <tr className="border-b text-left text-xs text-muted-foreground">
                <th scope="col" className="py-1 pr-2 font-medium">Nº</th>
                <th scope="col" className="py-1 pr-2 font-medium">Vencimento</th>
                <th scope="col" className="py-1 pr-2 text-right font-medium">Nominal</th>
                <th scope="col" className="py-1 pr-2 text-right font-medium">Correção</th>
                <th scope="col" className="py-1 pr-2 text-right font-medium">Projetado</th>
                <th scope="col" className="py-1 pr-2 text-right font-medium">Saldo projetado</th>
              </tr>
            </thead>
            <tbody>
              {m.cronograma.map((p) => (
                <tr key={p.numero} className="border-b last:border-0">
                  <th scope="row" className="py-1 pr-2 text-left font-normal">{p.numero}{p.tipo === 'ENTRADA' && ' (entrada)'}</th>
                  <td className="py-1 pr-2">{fmt.data(p.vencimento)}</td>
                  <td className={NUM}>{fmt.moeda(p.nominal)}</td>
                  <td className={NUM}>{fmt.percentual(p.percentualCorrecao)}</td>
                  <td className={NUM}>{fmt.moeda(p.valor)}{p.projetada && <span className="ml-1 text-xs text-warning">projeção</span>}</td>
                  <td className={NUM}>{fmt.moeda(p.saldoProjetado)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Recolhivel>

      <Recolhivel titulo="Memória de cálculo">
        <ol className="grid gap-2 text-sm" aria-label={`Memória de cálculo — ${m.nome}`}>
          {m.memoria.map((e, i) => (
            <li key={i} className="grid gap-0.5 border-b pb-2 last:border-0">
              <span className="flex flex-wrap justify-between gap-2">
                <span>{e.descricao}</span>
                <span className="num font-medium">{e.valor == null ? '' : e.unidade === 'R$' ? fmt.moeda(e.valor) : `${fmt.decimal(e.valor)} ${e.unidade ?? ''}`}</span>
              </span>
              <span className="text-xs text-muted-foreground">{e.formula}{e.fundamento && ` — ${e.fundamento}`}</span>
            </li>
          ))}
        </ol>
      </Recolhivel>

      <Recolhivel titulo="Elegibilidade por débito">
        <ul className="grid gap-1 text-sm" aria-label={`Elegibilidade — ${m.nome}`}>
          {m.elegibilidade.map((e) => (
            <li key={e.debitoId} className="flex flex-wrap items-start gap-2">
              <Selo tom={e.elegivel ? 'success' : 'danger'}>{e.elegivel ? 'elegível' : 'não elegível'}</Selo>
              <span>{e.descricao}{e.motivos.length > 0 && <span className="block text-xs text-muted-foreground">{e.motivos.join(' ')}</span>}</span>
            </li>
          ))}
        </ul>
      </Recolhivel>

      <Recolhivel titulo="Regra e fontes">
        <div className="grid gap-2 text-sm">
          <p>{m.fundamento}</p>
          {m.rescisao && <p><span className="font-medium">Rescisão:</span> {m.rescisao}</p>}
          {m.garantia && <p><span className="font-medium">Garantia:</span> {m.garantia}</p>}
          <p className="text-xs text-muted-foreground">Versão da regra: {m.versaoRegra}</p>
          <ul className="grid gap-1">
            {m.fontes.map((f) => (
              <li key={f.chave} className="flex flex-wrap items-center gap-2">
                <Selo tom={f.secundaria ? 'warning' : 'info'}>{f.secundaria ? 'fonte secundária' : 'oficial'}</Selo>
                <a href={f.url} target="_blank" rel="noreferrer noopener" className="text-primary underline-offset-4 hover:underline">{f.titulo}</a>
                <span className="text-xs text-muted-foreground">baixado em {fmt.data(f.data)}{f.sha256 && ` · SHA-256 ${f.sha256.slice(0, 12)}…`}</span>
              </li>
            ))}
          </ul>
        </div>
      </Recolhivel>
    </div>
  );
}
