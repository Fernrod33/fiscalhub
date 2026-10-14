import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Upload } from 'lucide-react';
import { DataTable, type ColumnDef } from '@/components/data-table';
import { EstadoVazio } from '@/components/estado-vazio';
import { Reveal } from '@/components/motion';
import { Paginacao } from '@/components/paginacao';
import { CabecalhoPagina } from '@/components/layout/cabecalho-pagina';
import { TextoTruncado } from '@/components/texto-truncado';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import type { Documento } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { STATUS_DOCUMENTO, TIPOS_DOCUMENTO } from '@/lib/rotulos';
import { useClientes } from '@/features/clientes/api';
import { useDocumentosDoEscritorio, type FiltrosDocumentos } from '../api';
import { StatusDocumento } from '../components/status-documento';

const TODOS = 'todos';

const colunas: ColumnDef<Documento>[] = [
  { accessorKey: 'nomeOriginalArquivo', header: 'Arquivo', cell: ({ getValue }) => <TextoTruncado texto={getValue<string>()} /> },
  { accessorKey: 'clienteRazaoSocial', header: 'Cliente', cell: ({ row, getValue }) => (
    <Link viewTransition to={`/clientes/${row.original.clienteId}/importacoes`} className="text-primary underline-offset-4 hover:underline">{getValue<string>()}</Link>) },
  { accessorKey: 'tipo', header: 'Tipo', cell: ({ getValue }) => TIPOS_DOCUMENTO[getValue<Documento['tipo']>()] },
  { accessorKey: 'dataImportacao', header: 'Importado em', cell: ({ getValue }) => fmt.dataHora(getValue<string>()), meta: { classe: 'whitespace-nowrap' } },
  { accessorKey: 'status', header: 'Status', cell: ({ getValue }) => <StatusDocumento status={getValue<Documento['status']>()} />, meta: { classe: 'whitespace-nowrap' } },
];

/** Filtro em select: "todos" vira undefined (sem filtro). */
function Filtro<T extends string>({ rotulo, valor, opcoes, aoMudar }: {
  rotulo: string; valor?: T; opcoes: [T, string][]; aoMudar: (v?: T) => void;
}) {
  return (
    <Select value={valor ?? TODOS} onValueChange={(v) => aoMudar(v === TODOS ? undefined : (v as T))}>
      <SelectTrigger aria-label={rotulo} className="w-full sm:w-56"><SelectValue /></SelectTrigger>
      <SelectContent>
        <SelectItem value={TODOS}>{rotulo}: todos</SelectItem>
        {opcoes.map(([v, r]) => <SelectItem key={v} value={v}>{r}</SelectItem>)}
      </SelectContent>
    </Select>
  );
}

/** Todas as importações do escritório, paginadas e filtradas no servidor; para enviar, abra o cliente. */
export default function ImportacoesPage() {
  const [filtros, setFiltros] = useState<FiltrosDocumentos>({ pagina: 0, tamanho: 20 });
  const documentos = useDocumentosDoEscritorio(filtros);
  const clientes = useClientes();
  const filtrar = (mudanca: Partial<FiltrosDocumentos>) => setFiltros((f) => ({ ...f, ...mudanca, pagina: 0 }));

  return (
    <Reveal className="grid gap-5">
      <CabecalhoPagina titulo="Importações" subtitulo="Arquivos enviados para todos os clientes" />
      <div className="flex flex-wrap gap-2" role="group" aria-label="Filtros">
        <Filtro rotulo="Cliente" valor={filtros.clienteId?.toString()} aoMudar={(v) => filtrar({ clienteId: v ? Number(v) : undefined })}
                opcoes={(clientes.data ?? []).map((c) => [String(c.id), c.razaoSocial])} />
        <Filtro rotulo="Status" valor={filtros.status} aoMudar={(status) => filtrar({ status })}
                opcoes={Object.entries(STATUS_DOCUMENTO).map(([v, s]) => [v as Documento['status'], s.rotulo])} />
        <Filtro rotulo="Tipo" valor={filtros.tipo} aoMudar={(tipo) => filtrar({ tipo })}
                opcoes={Object.entries(TIPOS_DOCUMENTO) as [Documento['tipo'], string][]} />
      </div>
      <DataTable rotulo="Importações do escritório" colunas={colunas} dados={documentos.data?.content} carregando={documentos.isPending}
                 tamanhoPagina={filtros.tamanho}
                 vazio={<EstadoVazio icone={Upload} titulo="Nenhuma importação"
                                     descricao={<>Abra um cliente em <Link to="/clientes" className="text-primary underline">Clientes</Link> e use a aba Importações.</>} />} />
      <Paginacao meta={documentos.data?.page} aoMudar={(pagina) => setFiltros((f) => ({ ...f, pagina }))} rotulo="Páginas das importações" unidade="importações" />
    </Reveal>
  );
}
