import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { LayoutAuth } from '../components/layout-auth';
import { FormularioSenhaNova, LinkIncompleto } from '../components/formulario-senha-nova';
import { useTokenDoLink } from '../token-do-link';
import { useAceitarConvite } from '../api';

/** Link do convite (72 h, uso único): o convidado define a própria senha e entra pelo login. */
export default function ConvitePage() {
  const token = useTokenDoLink();
  const aceitar = useAceitarConvite();
  const navigate = useNavigate();
  return (
    <LayoutAuth titulo="Defina sua senha" subtitulo="Você foi convidado para o escritório no FiscalHub.">
      {token
        ? <FormularioSenhaNova rotuloBotao="Definir senha" pendente={aceitar.isPending} erro={aceitar.error}
                               aoEnviar={({ senha }) => aceitar.mutate({ token, senha }, {
                                 onSuccess: () => { toast.success('Senha definida. Entre com o seu e-mail e a senha nova.'); navigate('/login', { replace: true }); },
                               })} />
        : <LinkIncompleto pedirNovo="Peça ao administrador do escritório para reenviar o convite." />}
    </LayoutAuth>
  );
}
