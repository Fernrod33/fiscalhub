import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Building2, Pencil, Plus, Trash2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { DataTable, type ColumnDef } from '@/components/data-table';
import { EstadoVazio } from '@/components/estado-vazio';
import { Reveal } from '@/components/motion';
import { CabecalhoPagina } from '@/components/layout/cabecalho-pagina';
import type { Cliente } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { usePermissoes } from '@/features/auth/permissoes';
import { useClientes } from '../api';
import { ClienteDialog } from '../components/cliente-dialog';
import { ExclusaoClienteDialog } from '../components/exclusao-cliente-dialog';

export default function ClientesPage() {
  const clientes = useClientes();
  const { alterar, admin } = usePermissoes();
  const [busca, setBusca] = useSearchParams();
  const [editando, setEditando] = useState<Cliente | null>(null);
  const [excluindo, setExcluindo] = useState<Cliente | null>(null);
  const novo = busca.get('novo') === '1';
  const fecharNovo = () => setBusca((p) => { p.delete('novo'); return p; }, { replace: true });

  const colunas: ColumnDef<Cliente>[] = [
    { accessorKey: 'razaoSocial', header: 'Razão social', cell: ({ row }) => (
      <Link viewTransition to={`/clientes/${row.original.id}`} className="font-medium text-primary underline-offset-4 hover:underline">
        {row.original.razaoSocial}
      </Link>) },
    { accessorKey: 'cnpj', header: 'CNPJ', cell: ({ getValue }) => fmt.cnpj(getValue<string>()), meta: { classe: 'whitespace-nowrap' } },
    { id: 'local', header: 'Cidade/UF', accessorFn: (c) => [c.cidade, c.estado].filter(Boolean).join('/') || '—' },
    { accessorKey: 'ativa', header: 'Situação', cell: ({ row }) => row.original.exclusaoPrevistaPara
      ? <Badge className="bg-danger-bg text-danger">Exclusão em {fmt.data(row.original.exclusaoPrevistaPara)}</Badge>
      : row.original.ativa ? <Badge variant="outline">Ativo</Badge> : <Badge variant="secondary">Inativo</Badge>, meta: { classe: 'whitespace-nowrap' } },
    ...(alterar ? [{
      id: 'acoes', header: () => <span className="sr-only">Ações</span>, enableSorting: false,
      cell: ({ row }) => (
        <div className="flex justify-end gap-1">
          <Button variant="ghost" size="icon" aria-label={`Editar ${row.original.razaoSocial}`} onClick={() => setEditando(row.original)}><Pencil aria-hidden /></Button>
          {admin && !row.original.exclusaoPrevistaPara && (
            <Button variant="ghost" size="icon" aria-label={`Excluir ${row.original.razaoSocial}`} onClick={() => setExcluindo(row.original)}><Trash2 aria-hidden /></Button>)}
        </div>),
    } satisfies ColumnDef<Cliente>] : []),
  ];

  return (
    <Reveal className="grid gap-5">
      <CabecalhoPagina titulo="Clientes" subtitulo="Empresas atendidas pelo escritório"
                       acoes={alterar && <Button onClick={() => setBusca({ novo: '1' })}><Plus aria-hidden /> Novo cliente</Button>} />
      <DataTable rotulo="Clientes" colunas={colunas} dados={clientes.data} carregando={clientes.isPending} filtro="Buscar por nome ou CNPJ"
                 vazio={<EstadoVazio icone={Building2} titulo="Nenhum cliente cadastrado"
                                     descricao="Cadastre a primeira empresa atendida para importar os arquivos dela."
                                     acao={alterar && <Button onClick={() => setBusca({ novo: '1' })}><Plus aria-hidden /> Novo cliente</Button>} />} />

      <ClienteDialog aberto={novo} aoMudar={(a) => !a && fecharNovo()} />
      <ClienteDialog cliente={editando ?? undefined} aberto={!!editando} aoMudar={(a) => !a && setEditando(null)} />
      <ExclusaoClienteDialog cliente={excluindo} aoFechar={() => setExcluindo(null)} />
    </Reveal>
  );
}
