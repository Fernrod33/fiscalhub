import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link } from 'react-router-dom';
import { AlertCircle, MailCheck } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Form } from '@/components/ui/form';
import { CampoTexto } from '@/components/campo';
import { mensagemErro } from '@/lib/http';
import { LayoutAuth } from '../components/layout-auth';
import { useRecuperarSenha } from '../api';

const esquema = z.object({ email: z.string().email('Informe um e-mail válido.') });

/** Pede o link de redefinição. A resposta é a mesma exista ou não a conta (não revela quem está cadastrado). */
export default function EsqueciSenhaPage() {
  const recuperar = useRecuperarSenha();
  const form = useForm<z.infer<typeof esquema>>({ resolver: zodResolver(esquema), defaultValues: { email: '' } });
  const enviar = form.handleSubmit((dados) => recuperar.mutate(dados));
  return (
    <LayoutAuth titulo="Esqueci minha senha" subtitulo="Enviaremos um link para redefinir a senha."
                rodape={<Link className="font-medium text-primary underline-offset-4 hover:underline" to="/login">Voltar ao login</Link>}>
      {recuperar.isSuccess ? (
        <Alert role="status">
          <MailCheck aria-hidden />
          <AlertDescription>{recuperar.data.message} Confira também a caixa de spam.</AlertDescription>
        </Alert>
      ) : (
        <Form {...form}>
          <form onSubmit={enviar} className="grid gap-4" noValidate>
            {recuperar.isError && (
              <Alert variant="destructive">
                <AlertCircle aria-hidden />
                <AlertDescription>{mensagemErro(recuperar.error, 'Não foi possível pedir o link. Verifique a conexão.')}</AlertDescription>
              </Alert>
            )}
            <CampoTexto control={form.control} name="email" rotulo="E-mail" type="email" autoComplete="email" />
            <Button type="submit" disabled={recuperar.isPending}>{recuperar.isPending ? 'Enviando…' : 'Enviar link'}</Button>
          </form>
        </Form>
      )}
    </LayoutAuth>
  );
}
