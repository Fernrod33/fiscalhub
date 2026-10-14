import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Check, ChevronsUpDown, KeyRound, LogOut, Monitor, Moon, Search, Sun } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuLabel, DropdownMenuRadioGroup, DropdownMenuRadioItem,
  DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { useTema, type Tema } from '@/app/tema';
import * as fmt from '@/lib/formatadores';
import { PAPEIS } from '@/lib/rotulos';
import { sair, useSessao } from '@/features/auth/sessao';
import { useClientes } from '@/features/clientes/api';
import { Trilha } from './trilha';

/** Cabeçalho: seletor de cliente, breadcrumbs, busca (Ctrl+K), tema e menu do usuário. */
export function Cabecalho({ abrirPaleta }: { abrirPaleta: () => void }) {
  return (
    <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b bg-surface/95 px-5 backdrop-blur">
      <SeletorCliente />
      <div className="min-w-0 flex-1"><Trilha /></div>
      <Button variant="outline" size="sm" onClick={abrirPaleta} className="text-muted-foreground">
        <Search aria-hidden /> Buscar <kbd className="rounded-sm border px-1 font-sans text-[11px]">Ctrl K</kbd>
      </Button>
      <AlternarTema />
      <MenuUsuario />
    </header>
  );
}

function SeletorCliente() {
  const [aberto, setAberto] = useState(false);
  const clientes = useClientes();
  const navigate = useNavigate();
  const clienteId = Number(useParams().clienteId);
  const atual = clientes.data?.find((c) => c.id === clienteId);
  return (
    <Popover open={aberto} onOpenChange={setAberto}>
      <PopoverTrigger asChild>
        <Button variant="outline" size="sm" role="combobox" aria-expanded={aberto} className="w-56 justify-between"
                aria-label={atual ? `Cliente: ${atual.razaoSocial}. Trocar cliente` : 'Selecionar cliente'}>
          <span className="truncate">{atual?.razaoSocial ?? 'Selecionar cliente'}</span>
          <ChevronsUpDown className="opacity-50" aria-hidden />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-80 p-0" align="start">
        <Command>
          <CommandInput placeholder="Buscar por nome ou CNPJ…" />
          <CommandList>
            <CommandEmpty>Nenhum cliente encontrado.</CommandEmpty>
            <CommandGroup>
              {clientes.data?.map((c) => (
                <CommandItem key={c.id} value={`${c.razaoSocial} ${c.cnpj}`}
                             onSelect={() => { setAberto(false); navigate(`/clientes/${c.id}`, { viewTransition: true }); }}>
                  <Check className={c.id === atual?.id ? 'opacity-100' : 'opacity-0'} aria-hidden />
                  <span className="truncate">{c.razaoSocial}</span>
                  <span className="num ml-auto text-xs text-muted-foreground">{fmt.cnpj(c.cnpj)}</span>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}

function AlternarTema() {
  const { tema, escuro, setTema } = useTema();
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" aria-label="Tema">{escuro ? <Moon aria-hidden /> : <Sun aria-hidden />}</Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuLabel>Tema</DropdownMenuLabel>
        <DropdownMenuRadioGroup value={tema} onValueChange={(t) => setTema(t as Tema)}>
          <DropdownMenuRadioItem value="claro"><Sun aria-hidden /> Claro</DropdownMenuRadioItem>
          <DropdownMenuRadioItem value="escuro"><Moon aria-hidden /> Escuro</DropdownMenuRadioItem>
          <DropdownMenuRadioItem value="sistema"><Monitor aria-hidden /> Sistema</DropdownMenuRadioItem>
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function MenuUsuario() {
  const sessao = useSessao();
  const navigate = useNavigate();
  if (!sessao) return null;
  const iniciais = sessao.nome.split(' ').filter(Boolean).slice(0, 2).map((p) => p[0]).join('').toUpperCase();
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" aria-label={`Menu do usuário ${sessao.nome}`} className="rounded-full bg-primary text-xs font-semibold text-primary-foreground hover:bg-primary/90">
          {iniciais}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel className="grid font-normal">
          <span className="font-medium">{sessao.nome}</span>
          <span className="truncate text-xs text-muted-foreground">{sessao.email} · {PAPEIS[sessao.papel]}</span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => navigate('/trocar-senha')}><KeyRound aria-hidden /> Trocar senha</DropdownMenuItem>
        <DropdownMenuItem onSelect={sair}><LogOut aria-hidden /> Sair</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
