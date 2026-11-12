import { useState } from 'react';
import { LogOut, MonitorSmartphone, ShieldCheck } from 'lucide-react';
import { toast } from 'sonner';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { CabecalhoPagina } from '@/components/layout/cabecalho-pagina';
import { dataHora } from '@/lib/formatadores';
import { mensagemErro } from '@/lib/http';
import type { ConfiguracaoSeguranca, SessaoDispositivo } from '@/lib/api/tipos';
import { useSessao } from '@/features/auth/sessao';
import {
  useAlterarConfiguracaoSeguranca, useConfiguracaoSeguranca, useDesativarSegundoFator, useEncerrarSessao, useNovosCodigos, useSessoes,
} from '../api';
import { AtivacaoSegundoFator, CodigosRecuperacao } from '../components/ativacao-segundo-fator';

/** Segurança da conta (todo usuário): verificação em duas etapas e sessões; para o ADMIN, a exigência no escritório. */
export default function SegurancaPage() {
  const sessao = useSessao();
  return (
    <div className="grid gap-6">
      <CabecalhoPagina titulo="Segurança" subtitulo="Verificação em duas etapas e dispositivos conectados à sua conta" />
      <SegundoFator ativo={!!sessao?.segundoFatorAtivo} />
      <Sessoes />
      {sessao?.papel === 'ADMIN' ? <ExigenciaEscritorio ativoNoAdmin={!!sessao.segundoFatorAtivo} /> : <Encarregado />}
    </div>
  );
}

function SegundoFator({ ativo }: { ativo: boolean }) {
  const [acao, setAcao] = useState<'codigos' | 'desativar' | null>(null);
  const exige = useConfiguracaoSeguranca().data?.exigirSegundoFator;
  return (
    <Cartao aria-labelledby="titulo-2fa">
      <CartaoTitulo id="titulo-2fa" acoes={ativo ? <Badge className="bg-success-bg text-success"><ShieldCheck aria-hidden /> Ativa</Badge>
        : <Badge className="bg-warning-bg text-warning">Desativada</Badge>}>
        Verificação em duas etapas
      </CartaoTitulo>
      {!ativo ? <AtivacaoSegundoFator aoConcluir={() => toast.success('Verificação em duas etapas ativada.')} /> : (
        <div className="grid gap-3">
          <p className="text-sm text-muted-foreground">O login pede a senha e o código do aplicativo autenticador.</p>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => setAcao('codigos')}>Gerar novos códigos de recuperação</Button>
            {!exige && <Button variant="outline" onClick={() => setAcao('desativar')}>Desativar</Button>}
          </div>
          {exige && <p className="text-sm text-muted-foreground">O escritório exige a verificação em duas etapas: ela não pode ser desativada.</p>}
          {acao && <ConfirmarComSenha acao={acao} aoFechar={() => setAcao(null)} />}
        </div>
      )}
    </Cartao>
  );
}

/** Desativar e gerar novos códigos pedem a senha de novo. */
function ConfirmarComSenha({ acao, aoFechar }: { acao: 'codigos' | 'desativar'; aoFechar: () => void }) {
  const [senha, setSenha] = useState('');
  const codigos = useNovosCodigos();
  const desativar = useDesativarSegundoFator();
  const mutacao = acao === 'codigos' ? codigos : desativar;
  if (codigos.data) return <CodigosRecuperacao codigos={codigos.data.codigos} aoConcluir={aoFechar} />;
  const enviar = (e: React.FormEvent) => {
    e.preventDefault();
    if (acao === 'codigos') codigos.mutate(senha);
    else desativar.mutate(senha, { onSuccess: () => { toast.success('Verificação em duas etapas desativada.'); aoFechar(); } });
  };
  return (
    <form onSubmit={enviar} className="grid max-w-sm gap-2 rounded-md border p-3">
      <Label htmlFor="senha-confirmacao">Confirme com a sua senha</Label>
      <Input id="senha-confirmacao" type="password" autoComplete="current-password" value={senha} onChange={(e) => setSenha(e.target.value)} />
      {mutacao.isError && <p role="alert" className="text-sm text-danger">{mensagemErro(mutacao.error, 'Não foi possível confirmar.')}</p>}
      <div className="flex gap-2">
        <Button type="button" variant="outline" onClick={aoFechar}>Cancelar</Button>
        <Button type="submit" variant={acao === 'desativar' ? 'destructive' : 'default'} disabled={!senha || mutacao.isPending}>
          {acao === 'codigos' ? 'Gerar códigos' : 'Desativar'}
        </Button>
      </div>
    </form>
  );
}

/** Navegador e sistema resumidos do User-Agent (só para reconhecer o dispositivo). */
function dispositivo(agente: string | undefined) {
  if (!agente) return 'Dispositivo desconhecido';
  const navegador = /Edg\//.test(agente) ? 'Edge' : /Firefox\//.test(agente) ? 'Firefox' : /Chrome\//.test(agente) ? 'Chrome' : /Safari\//.test(agente) ? 'Safari' : 'Navegador';
  const sistema = /Windows/.test(agente) ? 'Windows' : /Android/.test(agente) ? 'Android' : /iPhone|iPad/.test(agente) ? 'iOS' : /Mac OS/.test(agente) ? 'macOS' : /Linux/.test(agente) ? 'Linux' : '';
  return sistema ? `${navegador} no ${sistema}` : navegador;
}

function Sessoes() {
  const sessoes = useSessoes();
  const encerrar = useEncerrarSessao();
  const outras = (sessoes.data ?? []).filter((s) => !s.atual).length;
  return (
    <Cartao aria-labelledby="titulo-sessoes">
      <CartaoTitulo id="titulo-sessoes" acoes={outras > 0 && (
        <Button variant="outline" size="sm" disabled={encerrar.isPending}
                onClick={() => encerrar.mutate(undefined, { onSuccess: () => toast.success('As outras sessões foram encerradas.') })}>
          <LogOut aria-hidden /> Encerrar as outras
        </Button>)}>
        Sessões ativas
      </CartaoTitulo>
      {sessoes.isPending ? <Skeleton className="h-24" /> : (
        <ul className="divide-y">
          {(sessoes.data ?? []).map((s: SessaoDispositivo) => (
            <li key={s.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
              <div className="flex min-w-0 items-center gap-3">
                <MonitorSmartphone className="size-5 shrink-0 text-muted-foreground" aria-hidden />
                <div className="min-w-0">
                  <p className="font-medium">
                    {dispositivo(s.dispositivo)}{s.atual && <Badge className="ml-2 bg-info-bg text-info">Este dispositivo</Badge>}
                  </p>
                  <p className="text-sm text-muted-foreground">
                    IP {s.ip ?? '—'} · entrou em {dataHora(s.criadaEm)} · último uso {dataHora(s.ultimoUso)}
                  </p>
                </div>
              </div>
              {!s.atual && (
                <Button variant="outline" size="sm" disabled={encerrar.isPending} aria-label={`Encerrar a sessão ${dispositivo(s.dispositivo)} de ${dataHora(s.criadaEm)}`}
                        onClick={() => encerrar.mutate(s.id, { onSuccess: () => toast.success('Sessão encerrada.') })}>
                  Encerrar
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </Cartao>
  );
}

function ExigenciaEscritorio({ ativoNoAdmin }: { ativoNoAdmin: boolean }) {
  const config = useConfiguracaoSeguranca();
  const alterar = useAlterarConfiguracaoSeguranca();
  const exige = !!config.data?.exigirSegundoFator;
  return (
    <Cartao aria-labelledby="titulo-exigencia">
      <CartaoTitulo id="titulo-exigencia">Escritório</CartaoTitulo>
      <div className="grid gap-3">
        <div className="flex items-start gap-3">
          <input id="exigir-segundo-fator" type="checkbox" aria-describedby="exigir-segundo-fator-dica" className="mt-1 size-4 accent-primary" checked={exige} disabled={config.isPending || alterar.isPending || (!exige && !ativoNoAdmin)}
                 onChange={(e) => alterar.mutate({ ...config.data, exigirSegundoFator: e.target.checked }, {
                   onSuccess: (c) => toast.success(c.exigirSegundoFator ? 'Verificação em duas etapas exigida de todos.' : 'Exigência removida.'),
                 })} />
          <div>
            <Label htmlFor="exigir-segundo-fator">Exigir a verificação em duas etapas de todos os usuários</Label>
            <p id="exigir-segundo-fator-dica" className="text-sm text-muted-foreground">
              Quem ainda não ativou é levado à ativação no próximo acesso e não usa o sistema antes disso.
              {!ativoNoAdmin && ' Ative a sua antes de exigi-la do escritório.'}
            </p>
          </div>
        </div>
        {alterar.isError && <p role="alert" className="text-sm text-danger">{mensagemErro(alterar.error, 'Não foi possível salvar.')}</p>}
        {config.data && <FormEncarregado inicial={config.data} />}
      </div>
    </Cartao>
  );
}

/** Contato do encarregado (DPO) do escritório, que é o controlador dos dados dos clientes (LGPD art. 41). */
function FormEncarregado({ inicial }: { inicial: ConfiguracaoSeguranca }) {
  const alterar = useAlterarConfiguracaoSeguranca();
  const [nome, setNome] = useState(inicial.encarregadoNome ?? '');
  const [email, setEmail] = useState(inicial.encarregadoEmail ?? '');
  const salvar = (e: React.FormEvent) => {
    e.preventDefault();
    alterar.mutate({ ...inicial, encarregadoNome: nome, encarregadoEmail: email }, { onSuccess: () => toast.success('Encarregado salvo.') });
  };
  return (
    <form onSubmit={salvar} className="grid gap-3 border-t pt-4 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
      <p className="text-sm text-muted-foreground sm:col-span-3">Encarregado pelo tratamento de dados pessoais (DPO) do escritório: aparece para todos os usuários.</p>
      <div className="grid gap-1.5"><Label htmlFor="dpo-nome">Nome</Label><Input id="dpo-nome" value={nome} maxLength={150} onChange={(e) => setNome(e.target.value)} /></div>
      <div className="grid gap-1.5"><Label htmlFor="dpo-email">E-mail</Label><Input id="dpo-email" type="email" value={email} maxLength={150} onChange={(e) => setEmail(e.target.value)} /></div>
      <Button type="submit" variant="outline" disabled={alterar.isPending}>Salvar</Button>
      {alterar.isError && <p role="alert" className="text-sm text-danger sm:col-span-3">{mensagemErro(alterar.error, 'Não foi possível salvar.')}</p>}
    </form>
  );
}

function Encarregado() {
  const config = useConfiguracaoSeguranca().data;
  return (
    <Cartao aria-labelledby="titulo-encarregado">
      <CartaoTitulo id="titulo-encarregado">Privacidade</CartaoTitulo>
      <p className="text-sm">
        Encarregado pelo tratamento de dados pessoais do escritório:{' '}
        {config?.encarregadoNome || config?.encarregadoEmail
          ? <strong>{[config.encarregadoNome, config.encarregadoEmail].filter(Boolean).join(' · ')}</strong>
          : <span className="text-muted-foreground">não informado (fale com o administrador do escritório)</span>}
      </p>
    </Cartao>
  );
}
