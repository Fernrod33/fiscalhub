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
import { AcoesDocumento } from '../components/acoes-documento';
import { CentralImportacao } from '../components/central-importacao';
import { ClienteDoArquivo } from '../components/cliente-do-arquivo';
import { StatusDocumento } from '../components/status-documento';
import { Cartao, CartaoTitulo } from '@/components/cartao';

const TODOS = 'todos';

const colunas: ColumnDef<Documento>[] = [
  { accessorKey: 'nomeOriginalArquivo', header: 'Arquivo', cell: ({ getValue }) => <TextoTruncado texto={getValue<string>()} /> },
  { accessorKey: 'clienteRazaoSocial', header: 'Cliente', cell: ({ row, getValue }) => (row.original.clienteId
    ? <Link viewTransition to={`/clientes/${row.original.clienteId}/importacoes`} className="text-primary underline-offset-4 hover:underline">{getValue<string>()}</Link>
    : <span className="text-muted-foreground">a confirmar</span>) },
  { accessorKey: 'tipo', header: 'Tipo', cell: ({ getValue }) => TIPOS_DOCUMENTO[getValue<Documento['tipo']>()] },
  { accessorKey: 'dataImportacao', header: 'Importado em', cell: ({ getValue }) => fmt.dataHora(getValue<string>()), meta: { classe: 'whitespace-nowrap' } },
  { accessorKey: 'status', header: 'Status', cell: ({ getValue }) => <StatusDocumento status={getValue<Documento['status']>()} />, meta: { classe: 'whitespace-nowrap' } },
  { id: 'acoes', header: () => <span className="sr-only">Ações</span>, enableSorting: false, cell: ({ row }) => <AcoesDocumento documento={row.original} /> },
];

/** Documentos que esperam uma decisão: confirmar o cliente lido do arquivo ou mapear a planilha. */
function AguardandoVoce() {
  const cliente = useDocumentosDoEscritorio({ status: 'AGUARDANDO_CLIENTE', pagina: 0, tamanho: 20 });
  const mapeamento = useDocumentosDoEscritorio({ status: 'AGUARDANDO_MAPEAMENTO', pagina: 0, tamanho: 20 });
  const pendentes = [...(cliente.data?.content ?? []), ...(mapeamento.data?.content ?? [])];
  if (pendentes.length === 0) return null;
  return (
    <Cartao aria-labelledby="titulo-aguardando">
      <CartaoTitulo id="titulo-aguardando">Aguardando você</CartaoTitulo>
      <ul className="grid gap-3">
        {pendentes.map((d) => (
          <li key={d.id} className="grid gap-2">
            <div className="flex items-center gap-2"><TextoTruncado texto={d.nomeOriginalArquivo} /><StatusDocumento status={d.status} /></div>
            {d.status === 'AGUARDANDO_CLIENTE'
              ? <ClienteDoArquivo documento={d} />
              : <p><Link viewTransition to={`/importacoes/${d.id}/planilha`} className="font-medium text-primary underline-offset-4 hover:underline">
                  Mapear colunas e período</Link></p>}
          </li>
        ))}
      </ul>
    </Cartao>
  );
}

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

/**
 * Central de importação do escritório: envio sem escolher cliente (ele vem do CNPJ do arquivo), pendências e todas as
 * importações, paginadas e filtradas no servidor.
 */
export default function ImportacoesPage() {
  const [filtros, setFiltros] = useState<FiltrosDocumentos>({ pagina: 0, tamanho: 20 });
  const documentos = useDocumentosDoEscritorio(filtros);
  const clientes = useClientes();
  const filtrar = (mudanca: Partial<FiltrosDocumentos>) => setFiltros((f) => ({ ...f, ...mudanca, pagina: 0 }));

  return (
    <Reveal className="grid gap-5">
      <CabecalhoPagina titulo="Importações" subtitulo="Envie os arquivos: o cliente é identificado pelo CNPJ do próprio arquivo" />
      <Cartao aria-labelledby="titulo-central">
        <CartaoTitulo id="titulo-central">Importar arquivos</CartaoTitulo>
        <CentralImportacao />
      </Cartao>
      <AguardandoVoce />
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
                 vazio={<EstadoVazio icone={Upload} titulo="Nenhuma importação" descricao="Envie a ECD ou o balancete acima." />} />
      <Paginacao meta={documentos.data?.page} aoMudar={(pagina) => setFiltros((f) => ({ ...f, pagina }))} rotulo="Páginas das importações" unidade="importações" />
    </Reveal>
  );
}
