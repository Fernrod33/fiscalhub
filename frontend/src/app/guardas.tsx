import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useSessao } from '@/features/auth/sessao';
import { VigiaSessao } from '@/features/auth/components/vigia-sessao';
import { NaoEncontrado } from './erros';

/** Exige sessão (preserva a rota de retorno) e força o 2º fator exigido pelo escritório e o aceite dos termos. */
export function RotaProtegida() {
  const sessao = useSessao();
  const { pathname, search } = useLocation();
  if (!sessao) return <Navigate to="/login" replace state={{ voltar: pathname + search }} />;
  // a mesma ordem do filtro do backend: 2º fator exigido, aceite dos termos
  const pendencia = sessao.segundoFatorPendente ? '/ativar-segundo-fator' : sessao.termosPendentes ? '/aceitar-termos' : null;
  if (pendencia && pathname !== pendencia) return <Navigate to={pendencia} replace />;
  return <><Outlet /><VigiaSessao /></>;
}

/** Só ADMIN (o backend também recusa com 403). */
export function SoAdmin() {
  return useSessao()?.papel === 'ADMIN' ? <Outlet /> : <NaoEncontrado />;
}
