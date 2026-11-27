import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import {
  Dialog, DialogClose, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import type { Cliente } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { mensagemErro } from '@/lib/http';
import { useSolicitarExclusao } from '../api';

/**
 * Exclusão em dupla confirmação: 1) o que acontece (tudo some depois do prazo de arrependimento, inclusive os
 * arquivos); 2) digitar o CNPJ. O backend confere o CNPJ de novo.
 */
export function ExclusaoClienteDialog({ cliente, aoFechar }: { cliente: Cliente | null; aoFechar: () => void }) {
  const [etapa, setEtapa] = useState<1 | 2>(1);
  const [cnpj, setCnpj] = useState('');
  const solicitar = useSolicitarExclusao();
  const fechar = () => { setEtapa(1); setCnpj(''); solicitar.reset(); aoFechar(); };
  const confere = cliente && cnpj.replace(/\D/g, '') === cliente.cnpj.replace(/\D/g, '');

  return (
    <Dialog open={!!cliente} onOpenChange={(a) => !a && fechar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Excluir {cliente?.razaoSocial}</DialogTitle>
          <DialogDescription>
            {etapa === 1
              ? 'A exclusão remove todos os dados do cliente: arquivos importados (todas as versões), escriturações, débitos, simulações, relatórios emitidos e pontos de atenção. Fica agendada por 7 dias, quando ainda dá para cancelar; depois não há como desfazer. Se precisar dos dados, exporte-os antes.'
              : 'Para confirmar, digite o CNPJ do cliente.'}
          </DialogDescription>
        </DialogHeader>
        {etapa === 2 && (
          <div className="grid gap-1.5">
            <Label htmlFor="confirmacao-cnpj">CNPJ ({cliente && fmt.cnpj(cliente.cnpj)})</Label>
            <Input id="confirmacao-cnpj" value={cnpj} onChange={(e) => setCnpj(e.target.value)} autoComplete="off" inputMode="numeric" />
            {solicitar.isError && <p role="alert" className="text-sm text-danger">{mensagemErro(solicitar.error, 'Não foi possível agendar.')}</p>}
          </div>
        )}
        <DialogFooter>
          <DialogClose asChild><Button variant="outline">Cancelar</Button></DialogClose>
          {etapa === 1
            ? <Button variant="destructive" onClick={() => setEtapa(2)}>Continuar</Button>
            : <Button variant="destructive" disabled={!confere || solicitar.isPending}
                      onClick={() => cliente && solicitar.mutate({ id: cliente.id, confirmacao: cnpj }, {
                        onSuccess: (c) => { toast.success(`Exclusão agendada para ${fmt.dataHora(c.exclusaoPrevistaPara)}.`); fechar(); },
                      })}>
                Agendar exclusão
              </Button>}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
