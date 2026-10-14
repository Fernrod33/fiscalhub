import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useNavigate } from 'react-router-dom';
import { AlertCircle } from 'lucide-react';
import { toast } from 'sonner';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Form } from '@/components/ui/form';
import { CampoTexto } from '@/components/campo';
import { cnpjValido, somenteDigitos } from '@/lib/cnpj';
import { mensagemErro } from '@/lib/http';
import { LayoutAuth } from '../components/layout-auth';
import { useSignup } from '../api';

const esquema = z.object({
  nomeEscritorio: z.string().trim().min(1, 'Informe o nome do escritório.').max(150),
  documentoEscritorio: z.string().trim()
    .refine((v) => !v || somenteDigitos(v).length === 11 || cnpjValido(v), 'Informe um CNPJ válido ou um CPF com 11 dígitos.'),
  nome: z.string().trim().min(1, 'Informe seu nome.').max(100),
  email: z.string().email('Informe um e-mail válido.'),
  senha: z.string().min(6, 'Mínimo de 6 caracteres.').max(40),
});

export default function CadastroPage() {
  const signup = useSignup();
  const navigate = useNavigate();
  const form = useForm<z.infer<typeof esquema>>({
    resolver: zodResolver(esquema),
    defaultValues: { nomeEscritorio: '', documentoEscritorio: '', nome: '', email: '', senha: '' },
  });

  const enviar = form.handleSubmit((dados) => signup.mutate(
    { ...dados, documentoEscritorio: dados.documentoEscritorio || undefined },
    { onSuccess: () => { toast.success('Escritório cadastrado. Entre com seu e-mail e senha.'); navigate('/login'); } },
  ));

  return (
    <LayoutAuth titulo="Cadastrar escritório" subtitulo="Você será o administrador do escritório."
                rodape={<>Já tem conta? <Link className="font-medium text-primary underline-offset-4 hover:underline" to="/login">Entrar</Link></>}>
      <Form {...form}>
        <form onSubmit={enviar} className="grid gap-4" noValidate>
          {signup.isError && (
            <Alert variant="destructive">
              <AlertCircle aria-hidden />
              <AlertDescription>{mensagemErro(signup.error, 'Não foi possível cadastrar. Verifique a conexão.')}</AlertDescription>
            </Alert>
          )}
          <CampoTexto control={form.control} name="nomeEscritorio" rotulo="Nome do escritório" autoComplete="organization" />
          <CampoTexto control={form.control} name="documentoEscritorio" rotulo="CNPJ ou CPF do escritório (opcional)" />
          <CampoTexto control={form.control} name="nome" rotulo="Seu nome" autoComplete="name" />
          <CampoTexto control={form.control} name="email" rotulo="E-mail" type="email" autoComplete="email" />
          <CampoTexto control={form.control} name="senha" rotulo="Senha" type="password" autoComplete="new-password" />
          <Button type="submit" disabled={signup.isPending}>{signup.isPending ? 'Cadastrando…' : 'Cadastrar'}</Button>
        </form>
      </Form>
    </LayoutAuth>
  );
}
