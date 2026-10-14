import { NavLink, Outlet } from 'react-router-dom';
import { Skeleton } from '@/components/ui/skeleton';
import { CabecalhoPagina } from '@/components/layout/cabecalho-pagina';
import * as fmt from '@/lib/formatadores';
import { cn } from '@/lib/utils';
import { useCliente } from '../api';
import { ABAS_CLIENTE, useClienteId } from '../rota';

/** Cabeçalho do cliente + abas (cada aba é uma rota). */
export default function ClienteLayout() {
  const cliente = useCliente(useClienteId());
  return (
    <div className="grid gap-4">
      {cliente.data
        ? <CabecalhoPagina titulo={cliente.data.razaoSocial} subtitulo={<span className="num">CNPJ {fmt.cnpj(cliente.data.cnpj)}</span>} />
        : <div className="grid gap-2"><Skeleton className="h-8 w-72" /><Skeleton className="h-4 w-40" /></div>}
      <nav aria-label="Seções do cliente" className="-mx-1 overflow-x-auto border-b">
        <ul className="flex min-w-max gap-1 px-1">
          {ABAS_CLIENTE.map(([caminho, rotulo]) => (
            <li key={caminho}>
              <NavLink to={caminho} viewTransition
                       className={({ isActive }) => cn(
                         'inline-block border-b-2 px-3 py-2 font-medium whitespace-nowrap transition-colors duration-(--dur-fast) focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none',
                         isActive ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground')}>
                {rotulo}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <Outlet />
    </div>
  );
}
