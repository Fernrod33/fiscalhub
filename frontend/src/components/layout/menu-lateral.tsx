import { NavLink } from 'react-router-dom';
import { Landmark, PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';
import { CONFIGURACOES, NAVEGACAO, type ItemNavegacao } from './navegacao';

/** Menu lateral escuro (nos dois temas), recolhível; o estado fica no navegador (AppShell). */
export function MenuLateral({ recolhido, alternar, admin }: { recolhido: boolean; alternar: () => void; admin: boolean }) {
  const configuracoes = CONFIGURACOES.filter((i) => !i.soAdmin || admin);
  return (
    <aside className={cn('sticky top-0 flex h-svh flex-col bg-sidebar text-sidebar-foreground transition-[width] duration-(--dur-base) ease-(--ease-standard)',
      recolhido ? 'w-14' : 'w-56')}>
      <div className={cn('flex h-14 items-center gap-2 px-4', recolhido && 'justify-center px-0')}>
        <Landmark className="size-5 shrink-0" aria-hidden />
        {!recolhido && <span className="font-semibold tracking-tight text-white">FiscalHub</span>}
      </div>
      <nav aria-label="Menu principal" className="flex flex-1 flex-col gap-4 px-2">
        <Grupo itens={NAVEGACAO} recolhido={recolhido} />
        {configuracoes.length > 0 && (
          <div>
            {!recolhido && <p className="px-2 pb-1 text-xs font-medium tracking-wide uppercase opacity-70">Configurações</p>}
            <Grupo itens={configuracoes} recolhido={recolhido} />
          </div>
        )}
      </nav>
      <button type="button" onClick={alternar} aria-expanded={!recolhido} aria-label={recolhido ? 'Expandir menu' : 'Recolher menu'}
              className="m-2 flex items-center gap-2 rounded-md px-2 py-2 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground focus-visible:ring-2 focus-visible:ring-sidebar-ring focus-visible:outline-none">
        {recolhido ? <PanelLeftOpen className="size-4" aria-hidden /> : <><PanelLeftClose className="size-4" aria-hidden /> Recolher</>}
      </button>
    </aside>
  );
}

function Grupo({ itens, recolhido }: { itens: ItemNavegacao[]; recolhido: boolean }) {
  return (
    <ul className="grid gap-0.5">
      {itens.map(({ rotulo, caminho, icone: Icone }) => {
        const link = (
          <NavLink to={caminho} end={caminho === '/'} viewTransition aria-label={recolhido ? rotulo : undefined}
                   className={({ isActive }) => cn(
                     'flex items-center gap-2.5 rounded-md px-2 py-1.5 transition-colors duration-(--dur-fast) focus-visible:ring-2 focus-visible:ring-sidebar-ring focus-visible:outline-none',
                     recolhido && 'justify-center',
                     isActive ? 'bg-sidebar-accent font-medium text-sidebar-accent-foreground' : 'hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground')}>
            <Icone className="size-4 shrink-0" aria-hidden />
            {!recolhido && rotulo}
          </NavLink>
        );
        return (
          <li key={caminho}>
            {recolhido ? (
              <Tooltip>
                <TooltipTrigger asChild>{link}</TooltipTrigger>
                <TooltipContent side="right">{rotulo}</TooltipContent>
              </Tooltip>
            ) : link}
          </li>
        );
      })}
    </ul>
  );
}
