import { useState } from 'react';
import { Check, Copy, Download, KeyRound } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { mensagemErro } from '@/lib/http';
import { recarregarSessao, useAtivarSegundoFator, useIniciarSegundoFator } from '../api';

/** Os códigos de recuperação aparecem uma vez só: copiar, baixar e confirmar que guardou. */
export function CodigosRecuperacao({ codigos, aoConcluir }: { codigos: string[]; aoConcluir?: () => void }) {
  const texto = `FiscalHub — códigos de recuperação (cada um vale uma vez)\n\n${codigos.join('\n')}\n`;
  const baixar = () => {
    const url = URL.createObjectURL(new Blob([texto], { type: 'text/plain' }));
    const a = Object.assign(document.createElement('a'), { href: url, download: 'fiscalhub-codigos-de-recuperacao.txt' });
    a.click();
    URL.revokeObjectURL(url);
  };
  return (
    <div className="grid gap-3">
      <p className="text-sm">
        Guarde estes códigos num lugar seguro (gerenciador de senhas, por exemplo). Sem o celular, cada um permite entrar
        <strong> uma vez</strong>. Eles não serão mostrados de novo.
      </p>
      <ul aria-label="Códigos de recuperação" className="grid grid-cols-2 gap-1 rounded-md border bg-muted/40 p-3 font-mono text-sm">
        {codigos.map((c) => <li key={c}>{c}</li>)}
      </ul>
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="outline" onClick={() => navigator.clipboard.writeText(texto).then(() => toast.success('Códigos copiados.'))}>
          <Copy aria-hidden /> Copiar
        </Button>
        <Button type="button" variant="outline" onClick={baixar}><Download aria-hidden /> Baixar .txt</Button>
        {aoConcluir && <Button type="button" onClick={aoConcluir}><Check aria-hidden /> Guardei os códigos</Button>}
      </div>
    </div>
  );
}

/** Ativação do TOTP: QR (gerado no servidor) ou chave para digitar, o primeiro código e os códigos de recuperação. */
export function AtivacaoSegundoFator({ aoConcluir }: { aoConcluir: () => void }) {
  const iniciar = useIniciarSegundoFator();
  const ativar = useAtivarSegundoFator();
  const [codigo, setCodigo] = useState('');

  if (ativar.data) return <CodigosRecuperacao codigos={ativar.data.codigos} aoConcluir={() => recarregarSessao().then(aoConcluir)} />;

  if (!iniciar.data) {
    return (
      <div className="grid gap-3">
        <p className="text-sm text-muted-foreground">
          Use um aplicativo autenticador (Google Authenticator, Microsoft Authenticator, 1Password, Bitwarden…). Depois de
          ativar, o login pede a senha e o código de 6 dígitos do aplicativo.
        </p>
        {iniciar.isError && <p role="alert" className="text-sm text-danger">{mensagemErro(iniciar.error, 'Não foi possível começar.')}</p>}
        <div><Button type="button" onClick={() => iniciar.mutate()} disabled={iniciar.isPending}><KeyRound aria-hidden /> Configurar aplicativo</Button></div>
      </div>
    );
  }

  const enviar = (e: React.FormEvent) => {
    e.preventDefault();
    ativar.mutate(codigo.trim());
  };
  return (
    <form onSubmit={enviar} className="grid gap-4 sm:grid-cols-[auto_1fr] sm:items-start">
      <img src={`data:image/svg+xml;charset=utf-8,${encodeURIComponent(iniciar.data.qrSvg)}`} width={176} height={176}
           alt="QR code para o aplicativo autenticador" className="rounded-md border bg-white p-1" />
      <div className="grid gap-3">
        <p className="text-sm">1. Leia o QR code com o aplicativo. Sem câmera? Digite a chave:</p>
        <code className="w-fit break-all rounded bg-muted px-2 py-1 text-sm" aria-label="Chave para digitar no aplicativo">
          {iniciar.data.segredo.match(/.{1,4}/g)?.join(' ')}
        </code>
        <div className="grid gap-1.5">
          <Label htmlFor="codigo-ativacao">2. Digite o código de 6 dígitos que o aplicativo mostra</Label>
          <Input id="codigo-ativacao" inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={codigo}
                 onChange={(e) => setCodigo(e.target.value.replace(/\D/g, ''))} className="w-40 font-mono tracking-widest" />
        </div>
        {ativar.isError && <p role="alert" className="text-sm text-danger">{mensagemErro(ativar.error, 'Código inválido.')}</p>}
        <div><Button type="submit" disabled={codigo.length !== 6 || ativar.isPending}>{ativar.isPending ? 'Ativando…' : 'Ativar'}</Button></div>
      </div>
    </form>
  );
}
