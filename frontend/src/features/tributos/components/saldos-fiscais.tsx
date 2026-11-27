import { AnimatedNumber, Stagger, StaggerItem } from '@/components/motion';
import { DataTable, type ColumnDef } from '@/components/data-table';
import type { SaldoFiscal } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { SALDOS_FISCAIS } from '@/lib/rotulos';
import { ValorComOrigem } from './origem-arquivo';

/** Saldo mais recente de cada tipo: soma das contas/UFs no último período em que o tipo aparece. */
function ultimos(saldos: SaldoFiscal[]) {
  const porTipo = new Map<string, { fim: string; valor: number }>();
  for (const s of saldos) {
    const atual = porTipo.get(s.tipo);
    if (!atual || s.dataFim > atual.fim) porTipo.set(s.tipo, { fim: s.dataFim, valor: s.saldoFinal });
    else if (s.dataFim === atual.fim) atual.valor += s.saldoFinal;
  }
  return Object.keys(SALDOS_FISCAIS).filter((t) => porTipo.has(t)).map((t) => ({ tipo: t, ...porTipo.get(t)! }));
}

/** Cards do saldo acumulado (prejuízo fiscal, base negativa, créditos, saldos credores) e a evolução por período. */
export function SaldosFiscais({ clienteId, saldos }: { clienteId: number; saldos: SaldoFiscal[] }) {
  const colunas: ColumnDef<SaldoFiscal>[] = [
    { accessorKey: 'tipo', header: 'Saldo', cell: ({ getValue }) => SALDOS_FISCAIS[getValue<string>()] ?? getValue<string>() },
    { accessorKey: 'dataFim', header: 'Período', meta: { classe: 'num whitespace-nowrap' },
      cell: ({ row }) => `${fmt.data(row.original.dataInicio)} a ${fmt.data(row.original.dataFim)}` },
    { id: 'conta', header: 'Conta / UF', cell: ({ row }) => [row.original.codigo, row.original.uf].filter(Boolean).join(' · ') || '—' },
    { accessorKey: 'saldoInicial', header: 'Inicial', meta: { classe: 'text-right' }, cell: ({ getValue }) => <span className="num">{fmt.moeda(getValue<number>())}</span> },
    { accessorKey: 'saldoFinal', header: 'Final', meta: { classe: 'text-right' },
      cell: ({ row }) => (
        <ValorComOrigem clienteId={clienteId} rotulo={`${SALDOS_FISCAIS[row.original.tipo] ?? row.original.tipo} em ${fmt.data(row.original.dataFim)}`}
                        valor={row.original.saldoFinal} arquivo={row.original.arquivo} registro={row.original.registro} linha={row.original.linha} />
      ) },
  ];
  return (
    <div className="grid gap-4">
      <Stagger as="ul" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {ultimos(saldos).map((s) => (
          <StaggerItem as="li" key={s.tipo} className="rounded-md border p-3">
            <p className="text-sm text-muted-foreground">{SALDOS_FISCAIS[s.tipo]}</p>
            <AnimatedNumber valor={s.valor} formatar={fmt.moeda} className="num text-xl font-semibold" />
            <p className="num text-xs text-muted-foreground">em {fmt.data(s.fim)}</p>
          </StaggerItem>
        ))}
      </Stagger>
      <DataTable rotulo="Evolução dos saldos fiscais" colunas={colunas} dados={saldos} vazio={null} tamanhoPagina={12} />
    </div>
  );
}
