import { Navigate } from 'react-router-dom';
import { LayoutAuth } from '@/features/auth/components/layout-auth';
import { sair, useSessao } from '@/features/auth/sessao';
import { AtivacaoSegundoFator } from '../components/ativacao-segundo-fator';

/** ADMIN (sempre) ou escritório que exige o 2º fator, e o usuário ainda não ativou: só esta tela até ativar (o backend recusa o resto). */
export default function AtivarSegundoFatorPage() {
  // ativado, a sessão recarregada tira a pendência: o guard leva à próxima (termos) ou chega-se aqui e vai ao painel.
  // Navegar também no aoConcluir criava duas navegações concorrentes para rotas lazy e a tela ficava em branco.
  if (!useSessao()?.segundoFatorPendente) return <Navigate to="/" replace />;
  return (
    <LayoutAuth titulo="Ative a verificação em duas etapas" subtitulo="Obrigatória para administradores e quando o escritório exige: o login passa a pedir o código do aplicativo autenticador."
                rodape={<button type="button" className="font-medium text-primary underline-offset-4 hover:underline" onClick={sair}>Sair</button>}>
      <AtivacaoSegundoFator aoConcluir={() => {}} />
    </LayoutAuth>
  );
}
