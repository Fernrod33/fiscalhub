import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Building2, Pencil, Plus, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Confirmar } from '@/components/confirmar';
import { DataTable, type ColumnDef } from '@/components/data-table';
import { EstadoVazio } from '@/components/estado-vazio';
import { Reveal } from '@/components/motion';
import { CabecalhoPagina } from '@/components/layout/cabecalho-pagina';
import type { Cliente } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { mensagemErro } from '@/lib/http';
import { usePermissoes } from '@/features/auth/permissoes';
import { useClientes, useExcluirCliente } from '../api';
import { ClienteDialog } from '../components/cliente-dialog';

export default function ClientesPage() {
  const clientes = useClientes();
  const excluir = useExcluirCliente();
  const { alterar } = usePermissoes();
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
    { accessorKey: 'ativa', header: 'Situação', cell: ({ getValue }) => getValue<boolean>()
      ? <Badge variant="outline">Ativo</Badge> : <Badge variant="secondary">Inativo</Badge>, meta: { classe: 'whitespace-nowrap' } },
    ...(alterar ? [{
      id: 'acoes', header: () => <span className="sr-only">Ações</span>, enableSorting: false,
      cell: ({ row }) => (
        <div className="flex justify-end gap-1">
          <Button variant="ghost" size="icon" aria-label={`Editar ${row.original.razaoSocial}`} onClick={() => setEditando(row.original)}><Pencil aria-hidden /></Button>
          <Button variant="ghost" size="icon" aria-label={`Excluir ${row.original.razaoSocial}`} onClick={() => setExcluindo(row.original)}><Trash2 aria-hidden /></Button>
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
      <Confirmar aberto={!!excluindo} aoMudar={(a) => !a && setExcluindo(null)} titulo="Excluir cliente"
                 descricao={<>Excluir <strong>{excluindo?.razaoSocial}</strong>? Esta ação não pode ser desfeita.</>}
                 rotuloAcao="Excluir" ocupado={excluir.isPending}
                 aoConfirmar={() => excluindo && excluir.mutate(excluindo.id, {
                   onSuccess: () => { toast.success('Cliente excluído.'); setExcluindo(null); },
                   onError: (e) => toast.error(mensagemErro(e, 'Não foi possível excluir.')),
                 })} />
    </Reveal>
  );
}
