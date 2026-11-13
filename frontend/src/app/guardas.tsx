import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useSessao } from '@/features/auth/sessao';
import { VigiaSessao } from '@/features/auth/components/vigia-sessao';
import { NaoEncontrado } from './erros';

/** Exige sessão (preserva a rota de retorno) e força a troca de senha provisória e o 2º fator exigido pelo escritório. */
export function RotaProtegida() {
  const sessao = useSessao();
  const { pathname, search } = useLocation();
  if (!sessao) return <Navigate to="/login" replace state={{ voltar: pathname + search }} />;
  if (sessao.trocarSenhaNoProximoLogin && pathname !== '/trocar-senha') return <Navigate to="/trocar-senha" replace />;
  // a mesma ordem do filtro do backend: senha provisória (acima), 2º fator exigido, aceite dos termos
  const pendencia = sessao.trocarSenhaNoProximoLogin ? null
    : sessao.segundoFatorPendente ? '/ativar-segundo-fator' : sessao.termosPendentes ? '/aceitar-termos' : null;
  if (pendencia && pathname !== pendencia) return <Navigate to={pendencia} replace />;
  return <><Outlet /><VigiaSessao /></>;
}

/** Só ADMIN (o backend também recusa com 403). */
export function SoAdmin() {
  return useSessao()?.papel === 'ADMIN' ? <Outlet /> : <NaoEncontrado />;
}
