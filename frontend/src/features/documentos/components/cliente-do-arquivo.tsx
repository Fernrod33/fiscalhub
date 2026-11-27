import { useState } from 'react';
import { Building2 } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import type { Documento } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { mensagemErro } from '@/lib/http';
import { usePermissoes } from '@/features/auth/permissoes';
import { useClientes } from '@/features/clientes/api';
import { useConfirmarCliente } from '../api';

/**
 * "Cliente a partir do arquivo": a empresa lida do arquivo não é cliente do escritório. Um clique cadastra e importa,
 * ou o usuário escolhe um cliente existente. Nada é digitado.
 */
export function ClienteDoArquivo({ documento }: { documento: Pick<Documento, 'id' | 'cnpjArquivo' | 'nomeEmpresaArquivo'> }) {
  const { alterar } = usePermissoes();
  const clientes = useClientes();
  const confirmar = useConfirmarCliente();
  const [escolhido, setEscolhido] = useState<string>();

  const confirmarCom = (clienteId?: number) => confirmar.mutate({ documentoId: documento.id, clienteId }, {
    onSuccess: () => toast.success('Cliente confirmado. O arquivo vai ser processado de novo.'),
    onError: (e) => toast.error(mensagemErro(e, 'Não foi possível confirmar o cliente.')),
  });

  if (!alterar) {
    return <p className="text-muted-foreground">Aguardando um consultor ou administrador confirmar o cliente deste arquivo.</p>;
  }
  return (
    <div className="grid gap-3 rounded-md border border-info/30 bg-info-bg/40 p-3">
      <div className="flex items-start gap-3">
        <Building2 className="mt-0.5 size-5 shrink-0 text-info" aria-hidden />
        {documento.cnpjArquivo
          ? <p>O arquivo é de <strong>{documento.nomeEmpresaArquivo ?? 'empresa sem nome'}</strong>, CNPJ <span className="num">{fmt.cnpj(documento.cnpjArquivo)}</span>. Nenhum cliente do escritório tem esse CNPJ.</p>
          : <p>O arquivo não identifica a empresa. Escolha o cliente.</p>}
      </div>
      <div className="flex flex-wrap items-center gap-2">
        {documento.cnpjArquivo && (
          <Button disabled={confirmar.isPending} onClick={() => confirmarCom()}>Cadastrar cliente e importar</Button>
        )}
        <Select value={escolhido} onValueChange={setEscolhido}>
          <SelectTrigger className="w-64" aria-label="Cliente existente"><SelectValue placeholder={documento.cnpjArquivo ? "Ou associar a um cliente…" : "Escolher cliente…"} /></SelectTrigger>
          <SelectContent>
            {(clientes.data ?? []).map((c) => <SelectItem key={c.id} value={String(c.id)}>{c.razaoSocial}</SelectItem>)}
          </SelectContent>
        </Select>
        <Button variant="outline" disabled={!escolhido || confirmar.isPending} onClick={() => confirmarCom(Number(escolhido))}>Associar</Button>
      </div>
    </div>
  );
}
