import { useNavigate } from 'react-router-dom';
import { LayoutAuth } from '@/features/auth/components/layout-auth';
import { sair } from '@/features/auth/sessao';
import { AtivacaoSegundoFator } from '../components/ativacao-segundo-fator';

/** O escritório exige o 2º fator e o usuário ainda não ativou: só esta tela até ativar (o backend recusa o resto). */
export default function AtivarSegundoFatorPage() {
  const navigate = useNavigate();
  return (
    <LayoutAuth titulo="Ative a verificação em duas etapas" subtitulo="O escritório exige o código do aplicativo autenticador no login."
                rodape={<button type="button" className="font-medium text-primary underline-offset-4 hover:underline" onClick={sair}>Sair</button>}>
      <AtivacaoSegundoFator aoConcluir={() => navigate('/', { replace: true })} />
    </LayoutAuth>
  );
}
