import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { lerPreferencia, salvarPreferencia } from '@/lib/preferencias';
import { usePermissoes } from '@/features/auth/permissoes';
import { Cabecalho } from './cabecalho';
import { MenuLateral } from './menu-lateral';
import { PaletaComandos } from './paleta-comandos';

/** Casca da aplicação: menu lateral, cabeçalho e a área de conteúdo (a única que anima entre páginas). */
export default function AppShell() {
  const { admin } = usePermissoes();
  // em telas estreitas (tablet) o menu começa recolhido, se o usuário ainda não escolheu
  const [recolhido, setRecolhido] = useState(() => lerPreferencia('menuRecolhido', window.innerWidth < 1024));
  const [paleta, setPaleta] = useState(false);
  const alternar = () => setRecolhido((r) => { salvarPreferencia('menuRecolhido', !r); return !r; });

  return (
    <div className="flex min-h-svh">
      <MenuLateral recolhido={recolhido} alternar={alternar} admin={admin} />
      <div className="flex min-w-0 flex-1 flex-col">
        <Cabecalho abrirPaleta={() => setPaleta(true)} />
        <main className="area-conteudo mx-auto w-full max-w-7xl flex-1 p-5">
          <Outlet />
        </main>
      </div>
      <PaletaComandos aberta={paleta} aoMudar={setPaleta} />
    </div>
  );
}
