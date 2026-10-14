import { useState } from 'react';
import { Download, FileText, Trash2, Upload } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Confirmar } from '@/components/confirmar';
import { DataTable, type ColumnDef } from '@/components/data-table';
import { EstadoVazio } from '@/components/estado-vazio';
import { TextoTruncado } from '@/components/texto-truncado';
import type { Documento } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { mensagemErro } from '@/lib/http';
import { TIPOS_DOCUMENTO } from '@/lib/rotulos';
import { usePermissoes } from '@/features/auth/permissoes';
import { baixarDocumento, useExcluirDocumento } from '../api';
import { StatusDocumento } from './status-documento';

/** Documentos importados de um cliente: status ao vivo, download e exclusão. */
export function TabelaDocumentos({ documentos, carregando, destacado }: {
  documentos: Documento[] | undefined;
  carregando: boolean;
  /** Id do documento a destacar (ex.: o existente, depois de um envio duplicado). */
  destacado?: number;
}) {
  const { alterar } = usePermissoes();
  const excluir = useExcluirDocumento();
  const [excluindo, setExcluindo] = useState<Documento | null>(null);

  const baixar = (d: Documento) => baixarDocumento(d.id)
    .catch((e) => toast.error(mensagemErro(e, 'Não foi possível baixar o arquivo.')));

  const colunas: ColumnDef<Documento>[] = [
    { accessorKey: 'nomeOriginalArquivo', header: 'Arquivo', cell: ({ row }) => (
      <span className="flex items-center gap-2">
        <FileText className="size-4 shrink-0 text-muted-foreground" aria-hidden />
        <TextoTruncado texto={row.original.nomeOriginalArquivo} />
        {row.original.id === destacado && <span className="rounded-sm bg-info-bg px-1.5 text-xs font-semibold text-info">já importado</span>}
      </span>) },
    { accessorKey: 'tipo', header: 'Tipo', cell: ({ getValue }) => TIPOS_DOCUMENTO[getValue<Documento['tipo']>()] },
    { id: 'periodo', header: 'Período', accessorFn: (d) => d.periodoInicio,
      cell: ({ row }) => `${fmt.data(row.original.periodoInicio)} a ${fmt.data(row.original.periodoFim)}`, meta: { classe: 'whitespace-nowrap' } },
    { accessorKey: 'tamanhoBytes', header: 'Tamanho', cell: ({ getValue }) => fmt.tamanhoArquivo(getValue<number>()), meta: { classe: 'text-right whitespace-nowrap' } },
    { accessorKey: 'dataImportacao', header: 'Importado em', cell: ({ getValue }) => fmt.dataHora(getValue<string>()), meta: { classe: 'whitespace-nowrap' } },
    { accessorKey: 'status', header: 'Status', cell: ({ getValue }) => <StatusDocumento status={getValue<Documento['status']>()} />, meta: { classe: 'whitespace-nowrap' } },
    { id: 'acoes', header: () => <span className="sr-only">Ações</span>, enableSorting: false, cell: ({ row }) => (
      <div className="flex justify-end gap-1">
        <Button variant="ghost" size="icon" aria-label={`Baixar ${row.original.nomeOriginalArquivo}`} onClick={() => baixar(row.original)}><Download aria-hidden /></Button>
        {alterar && <Button variant="ghost" size="icon" aria-label={`Excluir ${row.original.nomeOriginalArquivo}`} onClick={() => setExcluindo(row.original)}><Trash2 aria-hidden /></Button>}
      </div>) },
  ];

  return (
    <>
      <DataTable rotulo="Documentos importados" colunas={colunas} dados={documentos} carregando={carregando} filtro="Filtrar documentos"
                 vazio={<EstadoVazio icone={Upload} titulo="Nenhum arquivo importado" descricao="Envie o SPED, PDF, CSV ou XLSX do cliente acima." />} />
      <Confirmar aberto={!!excluindo} aoMudar={(a) => !a && setExcluindo(null)} titulo="Excluir documento"
                 descricao={<>Excluir <strong>{excluindo?.nomeOriginalArquivo}</strong> e o arquivo armazenado?</>}
                 rotuloAcao="Excluir" ocupado={excluir.isPending}
                 aoConfirmar={() => excluindo && excluir.mutate(excluindo.id, {
                   onSuccess: () => { toast.success('Documento excluído.'); setExcluindo(null); },
                   onError: (e) => { toast.error(mensagemErro(e, 'Não foi possível excluir.')); setExcluindo(null); },
                 })} />
    </>
  );
}
