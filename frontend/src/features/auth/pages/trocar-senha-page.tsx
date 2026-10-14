import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate } from 'react-router-dom';
import { AlertCircle } from 'lucide-react';
import { toast } from 'sonner';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Form } from '@/components/ui/form';
import { CampoTexto } from '@/components/campo';
import { mensagemErro } from '@/lib/http';
import { LayoutAuth } from '../components/layout-auth';
import { useTrocarSenha } from '../api';
import { sair, useSessao } from '../sessao';

const esquemaTrocaSenha = z.object({
  senhaAtual: z.string().min(1, 'Informe a senha atual.'),
  novaSenha: z.string().min(6, 'Mínimo de 6 caracteres.').max(40),
  confirmacao: z.string(),
}).refine((d) => d.novaSenha === d.confirmacao, { message: 'As senhas não conferem.', path: ['confirmacao'] })
  .refine((d) => d.novaSenha !== d.senhaAtual, { message: 'A nova senha deve ser diferente da atual.', path: ['novaSenha'] });

/** Usada tanto na troca obrigatória (senha provisória) quanto na troca voluntária pelo menu do usuário. */
export default function TrocarSenhaPage() {
  const obrigatoria = useSessao()?.trocarSenhaNoProximoLogin;
  const trocar = useTrocarSenha();
  const navigate = useNavigate();
  const form = useForm<z.infer<typeof esquemaTrocaSenha>>({
    resolver: zodResolver(esquemaTrocaSenha),
    defaultValues: { senhaAtual: '', novaSenha: '', confirmacao: '' },
  });

  const enviar = form.handleSubmit(({ senhaAtual, novaSenha }) => trocar.mutate({ senhaAtual, novaSenha }, {
    onSuccess: () => { toast.success('Senha alterada.'); navigate('/', { replace: true }); },
  }));

  return (
    <LayoutAuth titulo={obrigatoria ? 'Defina sua senha' : 'Trocar senha'}
                subtitulo={obrigatoria ? 'Sua senha é provisória. Troque-a para continuar.' : undefined}
                rodape={obrigatoria ? <button type="button" className="font-medium text-primary underline-offset-4 hover:underline" onClick={sair}>Sair</button> : undefined}>
      <Form {...form}>
        <form onSubmit={enviar} className="grid gap-4" noValidate>
          {trocar.isError && (
            <Alert variant="destructive">
              <AlertCircle aria-hidden />
              <AlertDescription>{mensagemErro(trocar.error, 'Não foi possível trocar a senha.')}</AlertDescription>
            </Alert>
          )}
          <CampoTexto control={form.control} name="senhaAtual" rotulo={obrigatoria ? 'Senha provisória' : 'Senha atual'} type="password" autoComplete="current-password" />
          <CampoTexto control={form.control} name="novaSenha" rotulo="Nova senha" type="password" autoComplete="new-password" />
          <CampoTexto control={form.control} name="confirmacao" rotulo="Confirme a nova senha" type="password" autoComplete="new-password" />
          <div className="flex gap-2">
            {!obrigatoria && <Button type="button" variant="outline" onClick={() => navigate(-1)}>Cancelar</Button>}
            <Button type="submit" className="flex-1" disabled={trocar.isPending}>{trocar.isPending ? 'Salvando…' : 'Salvar senha'}</Button>
          </div>
        </form>
      </Form>
    </LayoutAuth>
  );
}
