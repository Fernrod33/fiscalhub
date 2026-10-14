import { Link } from 'react-router-dom';
import { Upload } from 'lucide-react';
import { DataTable, type ColumnDef } from '@/components/data-table';
import { EstadoVazio } from '@/components/estado-vazio';
import { Reveal } from '@/components/motion';
import { CabecalhoPagina } from '@/components/layout/cabecalho-pagina';
import { TextoTruncado } from '@/components/texto-truncado';
import type { Documento } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { TIPOS_DOCUMENTO } from '@/lib/rotulos';
import { useDocumentosDoEscritorio } from '../api';
import { StatusDocumento } from '../components/status-documento';

/** Todas as importações do escritório; para enviar, abra o cliente. */
export default function ImportacoesPage() {
  const { carregando, documentos, clientes } = useDocumentosDoEscritorio();
  const nomeCliente = new Map(clientes.map((c) => [c.id, c.razaoSocial]));

  const colunas: ColumnDef<Documento>[] = [
    { accessorKey: 'nomeOriginalArquivo', header: 'Arquivo', cell: ({ getValue }) => <TextoTruncado texto={getValue<string>()} /> },
    { id: 'cliente', header: 'Cliente', accessorFn: (d) => nomeCliente.get(d.clienteId) ?? '—', cell: ({ row, getValue }) => (
      <Link viewTransition to={`/clientes/${row.original.clienteId}/importacoes`} className="text-primary underline-offset-4 hover:underline">{getValue<string>()}</Link>) },
    { accessorKey: 'tipo', header: 'Tipo', cell: ({ getValue }) => TIPOS_DOCUMENTO[getValue<Documento['tipo']>()] },
    { accessorKey: 'dataImportacao', header: 'Importado em', cell: ({ getValue }) => fmt.dataHora(getValue<string>()), meta: { classe: 'whitespace-nowrap' } },
    { accessorKey: 'status', header: 'Status', cell: ({ getValue }) => <StatusDocumento status={getValue<Documento['status']>()} />, meta: { classe: 'whitespace-nowrap' } },
  ];

  return (
    <Reveal className="grid gap-5">
      <CabecalhoPagina titulo="Importações" subtitulo="Arquivos enviados para todos os clientes" />
      <DataTable rotulo="Importações do escritório" colunas={colunas} dados={documentos} carregando={carregando} filtro="Filtrar por arquivo ou cliente"
                 vazio={<EstadoVazio icone={Upload} titulo="Nenhuma importação"
                                     descricao={<>Abra um cliente em <Link to="/clientes" className="text-primary underline">Clientes</Link> e use a aba Importações.</>} />} />
    </Reveal>
  );
}
