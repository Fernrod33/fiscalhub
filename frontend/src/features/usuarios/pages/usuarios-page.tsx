import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { UserMinus, UserPlus, Users } from 'lucide-react';
import { toast } from 'sonner';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { CampoTexto } from '@/components/campo';
import { Confirmar } from '@/components/confirmar';
import { DataTable, type ColumnDef } from '@/components/data-table';
import { EstadoVazio } from '@/components/estado-vazio';
import { Reveal } from '@/components/motion';
import { CabecalhoPagina } from '@/components/layout/cabecalho-pagina';
import type { NovoUsuario, Papel, Usuario } from '@/lib/api/tipos';
import { http, mensagemErro } from '@/lib/http';
import { PAPEIS } from '@/lib/rotulos';
import { useSessao } from '@/features/auth/sessao';

const CHAVE = ['usuarios'];

function useMutacaoUsuarios<T>(fn: (v: T) => Promise<unknown>, sucesso: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => { toast.success(sucesso); return qc.invalidateQueries({ queryKey: CHAVE }); },
    onError: (e) => toast.error(mensagemErro(e, 'Não foi possível concluir.')),
  });
}

const PAPEIS_LISTA = Object.keys(PAPEIS) as Papel[];
const esquema = z.object({
  nome: z.string().trim().min(1, 'Informe o nome.').max(100),
  email: z.string().email('Informe um e-mail válido.'),
  papel: z.enum(PAPEIS_LISTA as [Papel, ...Papel[]]),
  senhaProvisoria: z.string().min(6, 'Mínimo de 6 caracteres.').max(40),
});

/** Usuários do escritório (só ADMIN): criar com senha provisória, mudar papel, desativar. */
export default function UsuariosPage() {
  const eu = useSessao()?.id;
  const usuarios = useQuery({ queryKey: CHAVE, queryFn: () => http.get<Usuario[]>('/api/usuarios') });
  const criar = useMutacaoUsuarios((u: NovoUsuario) => http.post('/api/usuarios', u), 'Usuário criado. Ele trocará a senha no primeiro acesso.');
  const mudarPapel = useMutacaoUsuarios(({ id, papel }: { id: number; papel: Papel }) => http.patch(`/api/usuarios/${id}/papel`, { papel }), 'Papel alterado.');
  const desativar = useMutacaoUsuarios((id: number) => http.patch(`/api/usuarios/${id}/desativar`), 'Usuário desativado.');
  const [novo, setNovo] = useState(false);
  const [desativando, setDesativando] = useState<Usuario | null>(null);
  const form = useForm<z.infer<typeof esquema>>({ resolver: zodResolver(esquema), defaultValues: { nome: '', email: '', papel: 'CONSULTOR', senhaProvisoria: '' } });

  const colunas: ColumnDef<Usuario>[] = [
    { accessorKey: 'nome', header: 'Nome' },
    { accessorKey: 'email', header: 'E-mail' },
    { accessorKey: 'papel', header: 'Papel', cell: ({ row }) => row.original.id === eu || !row.original.ativo
      ? PAPEIS[row.original.papel]
      : (
        <Select value={row.original.papel} onValueChange={(papel) => mudarPapel.mutate({ id: row.original.id, papel: papel as Papel })}>
          <SelectTrigger size="sm" aria-label={`Papel de ${row.original.nome}`}><SelectValue /></SelectTrigger>
          <SelectContent>{PAPEIS_LISTA.map((p) => <SelectItem key={p} value={p}>{PAPEIS[p]}</SelectItem>)}</SelectContent>
        </Select>
      ) },
    { accessorKey: 'ativo', header: 'Situação', cell: ({ row }) => (
      <span className="flex gap-1">
        {row.original.ativo ? <Badge variant="outline">Ativo</Badge> : <Badge variant="secondary">Desativado</Badge>}
        {row.original.trocarSenhaNoProximoLogin && row.original.ativo && <Badge className="bg-warning-bg text-warning">Senha provisória</Badge>}
      </span>) },
    { id: 'acoes', header: () => <span className="sr-only">Ações</span>, enableSorting: false, cell: ({ row }) => row.original.ativo && row.original.id !== eu && (
      <div className="flex justify-end">
        <Button variant="ghost" size="sm" onClick={() => setDesativando(row.original)}><UserMinus aria-hidden /> Desativar</Button>
      </div>) },
  ];

  const enviar = form.handleSubmit((dados) => criar.mutate(dados, { onSuccess: () => { setNovo(false); form.reset(); } }));

  return (
    <Reveal className="grid gap-5">
      <CabecalhoPagina titulo="Usuários" subtitulo="Pessoas do escritório e seus papéis"
                       acoes={<Button onClick={() => setNovo(true)}><UserPlus aria-hidden /> Novo usuário</Button>} />
      <DataTable rotulo="Usuários do escritório" colunas={colunas} dados={usuarios.data} carregando={usuarios.isPending} filtro="Buscar usuário"
                 vazio={<EstadoVazio icone={Users} titulo="Nenhum usuário" />} />

      <Dialog open={novo} onOpenChange={setNovo}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Novo usuário</DialogTitle>
            <DialogDescription>Ele entra com a senha provisória e define a própria senha no primeiro acesso.</DialogDescription>
          </DialogHeader>
          <Form {...form}>
            <form onSubmit={enviar} className="grid gap-4" noValidate>
              <CampoTexto control={form.control} name="nome" rotulo="Nome" autoComplete="off" />
              <CampoTexto control={form.control} name="email" rotulo="E-mail" type="email" autoComplete="off" />
              <FormField control={form.control} name="papel" render={({ field }) => (
                <FormItem>
                  <FormLabel>Papel</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent>{PAPEIS_LISTA.map((p) => <SelectItem key={p} value={p}>{PAPEIS[p]}</SelectItem>)}</SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )} />
              <CampoTexto control={form.control} name="senhaProvisoria" rotulo="Senha provisória" type="password" autoComplete="new-password" />
              <div className="flex justify-end gap-2">
                <Button type="button" variant="outline" onClick={() => setNovo(false)}>Cancelar</Button>
                <Button type="submit" disabled={criar.isPending}>{criar.isPending ? 'Criando…' : 'Criar usuário'}</Button>
              </div>
            </form>
          </Form>
        </DialogContent>
      </Dialog>

      <Confirmar aberto={!!desativando} aoMudar={(a) => !a && setDesativando(null)} titulo="Desativar usuário"
                 descricao={<><strong>{desativando?.nome}</strong> perde o acesso na hora. O histórico é mantido.</>}
                 rotuloAcao="Desativar" ocupado={desativar.isPending}
                 aoConfirmar={() => desativando && desativar.mutate(desativando.id, { onSettled: () => setDesativando(null) })} />
    </Reveal>
  );
}
