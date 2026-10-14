import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useSessao } from '@/features/auth/sessao';
import { NaoEncontrado } from './erros';

/** Exige sessão (preserva a rota de retorno) e força a troca de senha provisória. */
export function RotaProtegida() {
  const sessao = useSessao();
  const { pathname, search } = useLocation();
  if (!sessao) return <Navigate to="/login" replace state={{ voltar: pathname + search }} />;
  if (sessao.trocarSenhaNoProximoLogin && pathname !== '/trocar-senha') return <Navigate to="/trocar-senha" replace />;
  return <Outlet />;
}

/** Só ADMIN (o backend também recusa com 403). */
export function SoAdmin() {
  return useSessao()?.papel === 'ADMIN' ? <Outlet /> : <NaoEncontrado />;
}
