import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { AlertCircle } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Form, FormControl, FormField, FormItem, FormLabel } from '@/components/ui/form';
import { CampoTexto } from '@/components/campo';
import type { Cliente } from '@/lib/api/tipos';
import { esquemaCliente, valoresIniciais, type ValoresCliente } from '../cliente-esquema';

/** Formulário único de cliente: criação e edição. Não chama a API (recebe aoSalvar). */
export function ClienteForm({ cliente, aoSalvar, aoCancelar, ocupado, erro }: {
  cliente?: Cliente;
  aoSalvar: (valores: ValoresCliente) => void;
  aoCancelar?: () => void;
  ocupado?: boolean;
  erro?: string;
}) {
  const form = useForm<ValoresCliente>({ resolver: zodResolver(esquemaCliente), defaultValues: valoresIniciais(cliente) });

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(aoSalvar)} className="grid gap-4" noValidate>
        {erro && <Alert variant="destructive"><AlertCircle aria-hidden /><AlertDescription>{erro}</AlertDescription></Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <CampoTexto control={form.control} name="razaoSocial" rotulo="Razão social" />
          <CampoTexto control={form.control} name="nomeFantasia" rotulo="Nome fantasia" />
          <CampoTexto control={form.control} name="cnpj" rotulo="CNPJ" placeholder="00.000.000/0000-00" />
          <CampoTexto control={form.control} name="telefone" rotulo="Telefone" type="tel" autoComplete="tel" />
          <CampoTexto control={form.control} name="inscricaoEstadual" rotulo="Inscrição estadual" />
          <CampoTexto control={form.control} name="inscricaoMunicipal" rotulo="Inscrição municipal" />
          <div className="sm:col-span-2"><CampoTexto control={form.control} name="endereco" rotulo="Endereço" autoComplete="street-address" /></div>
          <CampoTexto control={form.control} name="cidade" rotulo="Cidade" autoComplete="address-level2" />
          <div className="grid grid-cols-2 gap-4">
            <CampoTexto control={form.control} name="estado" rotulo="UF" autoComplete="address-level1" />
            <CampoTexto control={form.control} name="cep" rotulo="CEP" autoComplete="postal-code" />
          </div>
        </div>
        <FormField control={form.control} name="ativa" render={({ field }) => (
          <FormItem className="flex items-center gap-2">
            <FormControl>
              <input type="checkbox" checked={field.value} onChange={(e) => field.onChange(e.target.checked)}
                     className="size-4 accent-primary" />
            </FormControl>
            <FormLabel className="font-normal">Cliente ativo</FormLabel>
          </FormItem>
        )} />
        <div className="flex justify-end gap-2">
          {aoCancelar && <Button type="button" variant="outline" onClick={aoCancelar}>Cancelar</Button>}
          <Button type="submit" disabled={ocupado}>{ocupado ? 'Salvando…' : cliente ? 'Salvar alterações' : 'Cadastrar cliente'}</Button>
        </div>
      </form>
    </Form>
  );
}
