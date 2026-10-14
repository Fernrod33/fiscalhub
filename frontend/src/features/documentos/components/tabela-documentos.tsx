import { FileText, Upload } from 'lucide-react';
import { DataTable, type ColumnDef } from '@/components/data-table';
import { EstadoVazio } from '@/components/estado-vazio';
import { TextoTruncado } from '@/components/texto-truncado';
import type { Documento } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { TIPOS_DOCUMENTO } from '@/lib/rotulos';
import { AcoesDocumento } from './acoes-documento';
import { StatusDocumento } from './status-documento';

/** Documentos importados de um cliente: tipo detectado, período lido do arquivo, status ao vivo e ações. */
export function TabelaDocumentos({ documentos, carregando, destacado }: {
  documentos: Documento[] | undefined;
  carregando: boolean;
  /** Id do documento a destacar (ex.: o existente, depois de um envio duplicado). */
  destacado?: number;
}) {
  const colunas: ColumnDef<Documento>[] = [
    { accessorKey: 'nomeOriginalArquivo', header: 'Arquivo', cell: ({ row }) => (
      <span className="flex items-center gap-2">
        <FileText className="size-4 shrink-0 text-muted-foreground" aria-hidden />
        <TextoTruncado texto={row.original.nomeOriginalArquivo} />
        {row.original.id === destacado && <span className="rounded-sm bg-info-bg px-1.5 text-xs font-semibold text-info">já importado</span>}
      </span>) },
    { accessorKey: 'tipo', header: 'Tipo', cell: ({ getValue }) => TIPOS_DOCUMENTO[getValue<Documento['tipo']>()] },
    { id: 'periodo', header: 'Período', accessorFn: (d) => d.periodoInicio,
      cell: ({ row }) => (row.original.periodoInicio ? `${fmt.data(row.original.periodoInicio)} a ${fmt.data(row.original.periodoFim)}` : '—'),
      meta: { classe: 'whitespace-nowrap' } },
    { accessorKey: 'tamanhoBytes', header: 'Tamanho', cell: ({ getValue }) => fmt.tamanhoArquivo(getValue<number>()), meta: { classe: 'text-right whitespace-nowrap' } },
    { accessorKey: 'dataImportacao', header: 'Importado em', cell: ({ getValue }) => fmt.dataHora(getValue<string>()), meta: { classe: 'whitespace-nowrap' } },
    { accessorKey: 'status', header: 'Status', cell: ({ getValue }) => <StatusDocumento status={getValue<Documento['status']>()} />, meta: { classe: 'whitespace-nowrap' } },
    { id: 'acoes', header: () => <span className="sr-only">Ações</span>, enableSorting: false, cell: ({ row }) => <AcoesDocumento documento={row.original} /> },
  ];

  return (
    <DataTable rotulo="Documentos importados" colunas={colunas} dados={documentos} carregando={carregando} filtro="Filtrar documentos"
               vazio={<EstadoVazio icone={Upload} titulo="Nenhum arquivo importado" descricao="Envie a ECD ou o balancete do cliente acima." />} />
  );
}
