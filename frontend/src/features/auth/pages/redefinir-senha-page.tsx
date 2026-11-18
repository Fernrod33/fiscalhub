import { Link, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { LayoutAuth } from '../components/layout-auth';
import { FormularioSenhaNova, LinkIncompleto } from '../components/formulario-senha-nova';
import { useTokenDoLink } from '../token-do-link';
import { useRedefinirSenha } from '../api';

/** Link da recuperação (30 min, uso único). Não dispensa o 2º fator: com ele ativo, o código vai junto. */
export default function RedefinirSenhaPage() {
  const token = useTokenDoLink();
  const redefinir = useRedefinirSenha();
  const navigate = useNavigate();
  return (
    <LayoutAuth titulo="Redefinir a senha" subtitulo="Escolha uma senha nova. As sessões abertas serão encerradas."
                rodape={<Link className="font-medium text-primary underline-offset-4 hover:underline" to="/login">Voltar ao login</Link>}>
      {token
        ? <FormularioSenhaNova comCodigo rotuloBotao="Salvar senha" pendente={redefinir.isPending} erro={redefinir.error}
                               aoEnviar={({ senha, codigo }) => redefinir.mutate({ token, novaSenha: senha, codigo }, {
                                 onSuccess: () => { toast.success('Senha redefinida. Entre com a senha nova.'); navigate('/login', { replace: true }); },
                               })} />
        : <LinkIncompleto pedirNovo={'Peça um novo em "Esqueci minha senha".'} />}
    </LayoutAuth>
  );
}
