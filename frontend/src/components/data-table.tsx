import { useState, type ReactNode } from 'react';
import {
  columnFilteringFeature, createFilteredRowModel, createPaginatedRowModel, createSortedRowModel, filterFn_includesString,
  globalFilteringFeature, rowPaginationFeature, rowSortingFeature, sortFn_alphanumeric, sortFn_basic, sortFn_datetime,
  sortFn_text, tableFeatures, useTable,
  type CellData, type ColumnDef as ColumnDefCore, type RowData, type SortingState, type TableFeatures,
} from '@tanstack/react-table';
import { ArrowDown, ArrowUp, ArrowUpDown, ChevronLeft, ChevronRight, Search } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { cn } from '@/lib/utils';

/** Recursos usados por todas as tabelas (TanStack Table v9: features explícitas, fora do render). */
const recursos = tableFeatures({
  rowSortingFeature,
  columnFilteringFeature,
  globalFilteringFeature,
  rowPaginationFeature,
  filteredRowModel: createFilteredRowModel(),
  sortedRowModel: createSortedRowModel(),
  paginatedRowModel: createPaginatedRowModel(),
  filterFns: { includesString: filterFn_includesString },
  sortFns: { alphanumeric: sortFn_alphanumeric, basic: sortFn_basic, text: sortFn_text, datetime: sortFn_datetime },
});

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type ColumnDef<T extends RowData> = ColumnDefCore<typeof recursos, T, any>;

type Props<T extends RowData> = {
  colunas: ColumnDef<T>[];
  dados: T[] | undefined;
  carregando?: boolean;
  /** Conteúdo mostrado quando não há linhas (sem filtro aplicado). */
  vazio: ReactNode;
  /** Mostra a busca na tabela; o texto é o placeholder. */
  filtro?: string;
  tamanhoPagina?: number;
  rotulo: string;
};

const SEM_DADOS: never[] = [];

/** Tabela única do sistema: ordenação, filtro, paginação no cliente, skeleton e estado vazio. */
export function DataTable<T extends RowData>({ colunas, dados, carregando, vazio, filtro, tamanhoPagina = 20, rotulo }: Props<T>) {
  const [ordenacao, setOrdenacao] = useState<SortingState>([]);
  const [busca, setBusca] = useState('');
  const tabela = useTable({
    features: recursos,
    columns: colunas,
    data: dados ?? SEM_DADOS,
    state: { sorting: ordenacao, globalFilter: busca },
    onSortingChange: setOrdenacao,
    onGlobalFilterChange: setBusca,
    globalFilterFn: 'includesString',
    initialState: { pagination: { pageIndex: 0, pageSize: tamanhoPagina } },
  });

  if (!carregando && (dados?.length ?? 0) === 0) return <>{vazio}</>;

  const linhas = tabela.getRowModel().rows;
  const paginas = tabela.getPageCount();

  return (
    <div className="grid gap-3">
      {filtro && (
        <div className="relative max-w-xs">
          <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input value={busca} onChange={(e) => setBusca(e.target.value)} placeholder={filtro} aria-label={filtro} className="pl-8" />
        </div>
      )}
      <div className="overflow-x-auto rounded-md border bg-surface">
        <Table aria-label={rotulo} aria-busy={carregando || undefined}>
          <TableHeader>
            {tabela.getHeaderGroups().map((grupo) => (
              <TableRow key={grupo.id}>
                {grupo.headers.map((h) => {
                  const ordem = h.column.getIsSorted();
                  return (
                    <TableHead key={h.id} aria-sort={ordem ? (ordem === 'asc' ? 'ascending' : 'descending') : undefined}
                               className={cn('text-xs font-medium tracking-wide text-muted-foreground uppercase', h.column.columnDef.meta?.classe)}>
                      {h.isPlaceholder ? null : h.column.getCanSort() ? (
                        <button type="button" onClick={h.column.getToggleSortingHandler()}
                                className="inline-flex items-center gap-1 rounded-sm uppercase hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none">
                          <tabela.FlexRender header={h} />
                          {ordem === 'asc' ? <ArrowUp className="size-3.5" aria-hidden />
                            : ordem === 'desc' ? <ArrowDown className="size-3.5" aria-hidden />
                              : <ArrowUpDown className="size-3.5 opacity-50" aria-hidden />}
                        </button>
                      ) : <tabela.FlexRender header={h} />}
                    </TableHead>
                  );
                })}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {carregando
              ? Array.from({ length: 5 }, (_, i) => (
                <TableRow key={i}>
                  {colunas.map((_, j) => <TableCell key={j}><Skeleton className="h-4 w-full max-w-40" /></TableCell>)}
                </TableRow>
              ))
              : linhas.length === 0
                ? <TableRow><TableCell colSpan={colunas.length} className="py-8 text-center text-muted-foreground">Nenhum resultado para “{busca}”.</TableCell></TableRow>
                : linhas.map((linha) => (
                  <TableRow key={linha.id}>
                    {linha.getAllCells().map((c) => (
                      <TableCell key={c.id} className={c.column.columnDef.meta?.classe}>
                        <tabela.FlexRender cell={c} />
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
          </TableBody>
        </Table>
      </div>
      {paginas > 1 && (
        <nav className="flex items-center justify-end gap-2 text-muted-foreground" aria-label="Paginação">
          <span className="num">Página {tabela.state.pagination.pageIndex + 1} de {paginas}</span>
          <Button variant="outline" size="icon" onClick={() => tabela.previousPage()} disabled={!tabela.getCanPreviousPage()} aria-label="Página anterior">
            <ChevronLeft aria-hidden />
          </Button>
          <Button variant="outline" size="icon" onClick={() => tabela.nextPage()} disabled={!tabela.getCanNextPage()} aria-label="Próxima página">
            <ChevronRight aria-hidden />
          </Button>
        </nav>
      )}
    </div>
  );
}

declare module '@tanstack/react-table' {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  interface ColumnMeta<in out TFeatures extends TableFeatures, in out TData extends RowData, TValue extends CellData = CellData> {
    /** Classes da coluna (ex.: alinhamento à direita e nowrap para números). */
    classe?: string;
  }
}
