import { useState } from 'react';
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
import type { Usuario } from '@/lib/api/tipos';
import { LayoutAuth } from '../components/layout-auth';
import { useLogin, useSegundoFator } from '../api';
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
  const [desafio, setDesafio] = useState<string | null>(null);
  const form = useForm<z.infer<typeof esquema>>({ resolver: zodResolver(esquema), defaultValues: { email: '', senha: '' } });

  const destino = (u: Usuario) => (u.trocarSenhaNoProximoLogin ? '/trocar-senha' : u.segundoFatorPendente ? '/ativar-segundo-fator'
    : u.termosPendentes ? '/aceitar-termos' : voltar);
  if (sessao && !login.isPending) return <Navigate to={destino(sessao)} replace />;
  if (desafio) return <PassoCodigo desafio={desafio} aoEntrar={(u) => navigate(destino(u), { replace: true })} aoVoltar={() => setDesafio(null)} />;

  const enviar = form.handleSubmit((dados) => login.mutate(dados, {
    onSuccess: (r) => (r.usuario ? navigate(destino(r.usuario), { replace: true }) : setDesafio(r.desafio ?? null)),
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

const esquemaCodigo = z.object({ codigo: z.string().trim().min(6, 'Digite o código de 6 dígitos ou um código de recuperação.').max(20) });

/** Segunda etapa: o código do aplicativo autenticador ou um código de recuperação (uso único). */
function PassoCodigo({ desafio, aoEntrar, aoVoltar }: { desafio: string; aoEntrar: (u: Usuario) => void; aoVoltar: () => void }) {
  const segundoFator = useSegundoFator();
  const form = useForm<z.infer<typeof esquemaCodigo>>({ resolver: zodResolver(esquemaCodigo), defaultValues: { codigo: '' } });
  const enviar = form.handleSubmit(({ codigo }) => segundoFator.mutate({ desafio, codigo }, {
    onSuccess: (r) => { if (r.usuario) aoEntrar(r.usuario); },
  }));
  return (
    <LayoutAuth titulo="Verificação em duas etapas" subtitulo="Digite o código do aplicativo autenticador."
                rodape={<button type="button" className="font-medium text-primary underline-offset-4 hover:underline" onClick={aoVoltar}>Voltar</button>}>
      <Form {...form}>
        <form onSubmit={enviar} className="grid gap-4" noValidate>
          {segundoFator.isError && (
            <Alert variant="destructive">
              <AlertCircle aria-hidden />
              <AlertDescription>{mensagemErro(segundoFator.error, 'Não foi possível conferir o código.')}</AlertDescription>
            </Alert>
          )}
          <CampoTexto control={form.control} name="codigo" rotulo="Código" autoComplete="one-time-code"
                      descricao="Sem o celular? Use um dos códigos de recuperação (cada um vale uma vez)." />
          <Button type="submit" disabled={segundoFator.isPending}>{segundoFator.isPending ? 'Conferindo…' : 'Entrar'}</Button>
        </form>
      </Form>
    </LayoutAuth>
  );
}
