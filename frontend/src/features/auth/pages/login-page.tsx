import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { AlertCircle } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Form } from '@/components/ui/form';
import { CampoTexto } from '@/components/campo';
import { mensagemErro } from '@/lib/http';
import { LayoutAuth } from '../components/layout-auth';
import { useLogin } from '../api';
import { useSessao } from '../sessao';

const esquema = z.object({
  email: z.string().email('Informe um e-mail válido.'),
  senha: z.string().min(1, 'Informe a senha.'),
});

export default function LoginPage() {
  const sessao = useSessao();
  const login = useLogin();
  const navigate = useNavigate();
  const voltar: string = useLocation().state?.voltar ?? '/';
  const form = useForm<z.infer<typeof esquema>>({ resolver: zodResolver(esquema), defaultValues: { email: '', senha: '' } });

  if (sessao && !login.isPending) return <Navigate to={sessao.trocarSenhaNoProximoLogin ? '/trocar-senha' : voltar} replace />;

  const enviar = form.handleSubmit((dados) => login.mutate(dados, {
    onSuccess: (s) => navigate(s.trocarSenhaNoProximoLogin ? '/trocar-senha' : voltar, { replace: true }),
  }));

  return (
    <LayoutAuth titulo="Entrar" subtitulo="Acesse o escritório"
                rodape={<>Ainda não tem conta? <Link className="font-medium text-primary underline-offset-4 hover:underline" to="/cadastro">Cadastre o escritório</Link></>}>
      <Form {...form}>
        <form onSubmit={enviar} className="grid gap-4" noValidate>
          {login.isError && (
            <Alert variant="destructive">
              <AlertCircle aria-hidden />
              <AlertDescription>{mensagemErro(login.error, 'Não foi possível entrar. Verifique a conexão.')}</AlertDescription>
            </Alert>
          )}
          <CampoTexto control={form.control} name="email" rotulo="E-mail" type="email" autoComplete="email" />
          <CampoTexto control={form.control} name="senha" rotulo="Senha" type="password" autoComplete="current-password" />
          <Button type="submit" disabled={login.isPending}>{login.isPending ? 'Entrando…' : 'Entrar'}</Button>
        </form>
      </Form>
    </LayoutAuth>
  );
}
