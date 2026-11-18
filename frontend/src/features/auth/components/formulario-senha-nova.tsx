import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link } from 'react-router-dom';
import { AlertCircle } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Form } from '@/components/ui/form';
import { CampoTexto } from '@/components/campo';
import { mensagemErro } from '@/lib/http';
import { DICA_SENHA, SENHA_MAXIMA, SENHA_MINIMA } from '../api';

const esquema = z.object({
  senha: z.string().min(SENHA_MINIMA, `Mínimo de ${SENHA_MINIMA} caracteres.`).max(SENHA_MAXIMA, `Máximo de ${SENHA_MAXIMA} caracteres.`),
  confirmacao: z.string(),
  codigo: z.string().trim().max(20),
}).refine((d) => d.senha === d.confirmacao, { message: 'As senhas não conferem.', path: ['confirmacao'] });

export type SenhaNova = { senha: string; codigo?: string };

/** Senha nova e confirmação (convite e redefinição); com comCodigo, o código do 2º fator, se estiver ativo. */
export function FormularioSenhaNova({ comCodigo = false, rotuloBotao, pendente, erro, aoEnviar }: {
  comCodigo?: boolean;
  rotuloBotao: string;
  pendente: boolean;
  erro: unknown;
  aoEnviar: (dados: SenhaNova) => void;
}) {
  const form = useForm<z.infer<typeof esquema>>({ resolver: zodResolver(esquema), defaultValues: { senha: '', confirmacao: '', codigo: '' } });
  const enviar = form.handleSubmit(({ senha, codigo }) => aoEnviar({ senha, codigo: codigo || undefined }));
  return (
    <Form {...form}>
      <form onSubmit={enviar} className="grid gap-4" noValidate>
        {!!erro && (
          <Alert variant="destructive">
            <AlertCircle aria-hidden />
            <AlertDescription>{mensagemErro(erro, 'Não foi possível salvar a senha.')}</AlertDescription>
          </Alert>
        )}
        <CampoTexto control={form.control} name="senha" rotulo="Nova senha" type="password" autoComplete="new-password" descricao={DICA_SENHA} />
        <CampoTexto control={form.control} name="confirmacao" rotulo="Confirme a nova senha" type="password" autoComplete="new-password" />
        {comCodigo && (
          <CampoTexto control={form.control} name="codigo" rotulo="Código do aplicativo autenticador" autoComplete="one-time-code"
                      descricao="Só se a verificação em duas etapas estiver ativa. Vale também um código de recuperação." />
        )}
        <Button type="submit" disabled={pendente}>{pendente ? 'Salvando…' : rotuloBotao}</Button>
      </form>
    </Form>
  );
}

/** Link sem token (copiado pela metade, ou aberto de novo depois de usado). */
export function LinkIncompleto({ pedirNovo }: { pedirNovo: string }) {
  return (
    <Alert variant="destructive">
      <AlertCircle aria-hidden />
      <AlertDescription>Link incompleto ou já usado. {pedirNovo}{' '}
        <Link className="font-medium underline underline-offset-4" to="/login">Voltar ao login</Link></AlertDescription>
    </Alert>
  );
}
