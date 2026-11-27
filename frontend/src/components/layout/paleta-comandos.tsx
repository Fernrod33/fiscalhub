import { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Building2, Plus, Upload } from 'lucide-react';
import {
  CommandDialog, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList, CommandSeparator,
} from '@/components/ui/command';
import * as fmt from '@/lib/formatadores';
import { usePermissoes } from '@/features/auth/permissoes';
import { useClientes } from '@/features/clientes/api';
import { CONFIGURACOES, NAVEGACAO } from './navegacao';

/** Paleta de comandos (Ctrl+K / ⌘K): buscar cliente, navegar e ações rápidas. */
export function PaletaComandos({ aberta, aoMudar }: { aberta: boolean; aoMudar: (a: boolean) => void }) {
  const navigate = useNavigate();
  const clienteAtual = useParams().clienteId;
  const { alterar, admin } = usePermissoes();
  const clientes = useClientes();

  useEffect(() => {
    const atalho = (e: KeyboardEvent) => {
      if (e.key.toLowerCase() === 'k' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        aoMudar(!aberta);
      }
    };
    document.addEventListener('keydown', atalho);
    return () => document.removeEventListener('keydown', atalho);
  }, [aberta, aoMudar]);

  const ir = (caminho: string) => { aoMudar(false); navigate(caminho, { viewTransition: true }); };

  return (
    <CommandDialog open={aberta} onOpenChange={aoMudar} title="Paleta de comandos" description="Busque um cliente, uma tela ou uma ação.">
      <CommandInput placeholder="Buscar cliente, tela ou ação…" />
      <CommandList>
        <CommandEmpty>Nada encontrado.</CommandEmpty>
        <CommandGroup heading="Ações">
          {alterar && <CommandItem onSelect={() => ir('/clientes?novo=1')}><Plus aria-hidden /> Novo cliente</CommandItem>}
          <CommandItem onSelect={() => ir(clienteAtual ? `/clientes/${clienteAtual}/importacoes` : '/clientes')}>
            <Upload aria-hidden /> Importar arquivo{clienteAtual ? '' : ' (escolha o cliente)'}
          </CommandItem>
        </CommandGroup>
        <CommandSeparator />
        <CommandGroup heading="Navegar">
          {[...NAVEGACAO, ...CONFIGURACOES.filter((i) => !i.soAdmin || admin)].map(({ rotulo, caminho, icone: Icone }) => (
            <CommandItem key={caminho} onSelect={() => ir(caminho)}><Icone aria-hidden /> {rotulo}</CommandItem>
          ))}
        </CommandGroup>
        <CommandSeparator />
        <CommandGroup heading="Clientes">
          {clientes.data?.map((c) => (
            <CommandItem key={c.id} value={`${c.razaoSocial} ${c.cnpj}`} onSelect={() => ir(`/clientes/${c.id}`)}>
              <Building2 aria-hidden /> <span className="truncate">{c.razaoSocial}</span>
              <span className="num ml-auto text-xs text-muted-foreground">{fmt.cnpj(c.cnpj)}</span>
            </CommandItem>
          ))}
        </CommandGroup>
      </CommandList>
    </CommandDialog>
  );
}
