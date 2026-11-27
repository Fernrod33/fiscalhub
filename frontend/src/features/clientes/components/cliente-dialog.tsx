import { toast } from 'sonner';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import type { Cliente } from '@/lib/api/tipos';
import { mensagemErro } from '@/lib/http';
import { useSalvarCliente } from '../api';
import { paraRequisicao } from '../cliente-esquema';
import { ClienteForm } from './cliente-form';

/** Criação (sem cliente) ou edição de cliente num diálogo. */
export function ClienteDialog({ cliente, aberto, aoMudar }: { cliente?: Cliente; aberto: boolean; aoMudar: (a: boolean) => void }) {
  const salvar = useSalvarCliente();
  return (
    <Dialog open={aberto} onOpenChange={(a) => { if (!a) salvar.reset(); aoMudar(a); }}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{cliente ? 'Editar cliente' : 'Novo cliente'}</DialogTitle>
          <DialogDescription>Dados cadastrais. Os dados fiscais entram pela importação de arquivos.</DialogDescription>
        </DialogHeader>
        {aberto && (
          <ClienteForm cliente={cliente} ocupado={salvar.isPending}
                       erro={salvar.isError ? mensagemErro(salvar.error, 'Não foi possível salvar.') : undefined}
                       aoCancelar={() => aoMudar(false)}
                       aoSalvar={(v) => salvar.mutate({ id: cliente?.id, dados: paraRequisicao(v) }, {
                         onSuccess: () => { toast.success(cliente ? 'Cliente atualizado.' : 'Cliente cadastrado.'); aoMudar(false); },
                       })} />
        )}
      </DialogContent>
    </Dialog>
  );
}
