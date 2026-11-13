import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import type { DadosSessao, Reautenticacao } from '@/lib/api/tipos';
import { configurarAtividade, configurarReautenticacao, ErroApi, http, mensagemErro } from '@/lib/http';
import { expirarPorInatividade, sair, useSessao } from '../sessao';

const AVISO_MS = 2 * 60_000;
// o servidor só grava o último uso de minuto em minuto: o relógio local começa a contar até 1 min antes
const FOLGA_MS = 60_000;
const CHAVE = 'fiscalhub.atividade'; // compartilhada entre abas: trabalhar numa mantém as outras

function registrarAtividade() {
  try { localStorage.setItem(CHAVE, String(Date.now())); } catch { /* sem storage: vale a memória */ }
  ultima = Date.now();
}
let ultima = Date.now();
const ultimaAtividade = () => {
  try { return Math.max(ultima, Number(localStorage.getItem(CHAVE)) || 0); } catch { return ultima; }
};

/** Sessão aberta: aviso de inatividade 2 min antes de expirar e o diálogo de reautenticação das ações destrutivas. */
export function VigiaSessao() {
  return (
    <>
      <AvisoInatividade />
      <DialogoReautenticacao />
    </>
  );
}

function AvisoInatividade() {
  const sessao = useSessao();
  // com pendência (senha provisória, 2º fator, termos) a API só libera o próprio cadastro: o aviso começa depois
  const livre = !!sessao && !sessao.trocarSenhaNoProximoLogin && !sessao.segundoFatorPendente && !sessao.termosPendentes;
  const { data } = useQuery({
    queryKey: ['sessao', 'dados'], queryFn: () => http.get<DadosSessao>('/api/usuarios/me/sessao'), staleTime: Infinity, enabled: livre,
  });
  const [restante, setRestante] = useState<number | null>(null);

  useEffect(() => {
    registrarAtividade();
    configurarAtividade(registrarAtividade);
    return () => configurarAtividade(() => {});
  }, []);

  useEffect(() => {
    if (!data) return;
    const limite = data.inatividadeSegundos * 1000 - FOLGA_MS;
    const conferir = () => {
      const r = ultimaAtividade() + limite - Date.now();
      if (r > 0) return setRestante(r <= AVISO_MS ? r : null);
      clearInterval(id);
      void expirarPorInatividade();
    };
    const id = setInterval(conferir, 1000);
    return () => clearInterval(id);
  }, [data]);

  const segundos = Math.ceil((restante ?? 0) / 1000);
  return (
    <Dialog open={restante !== null}>
      <DialogContent showCloseButton={false} onEscapeKeyDown={(e) => e.preventDefault()} onInteractOutside={(e) => e.preventDefault()}>
        <DialogHeader>
          <DialogTitle>A sessão vai expirar</DialogTitle>
          <DialogDescription>
            Sem uso há quase {Math.round((data?.inatividadeSegundos ?? 0) / 60)} minutos. A sessão será encerrada em{' '}
            <span role="timer">{Math.floor(segundos / 60)}:{String(segundos % 60).padStart(2, '0')}</span>.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => void sair()}>Sair agora</Button>
          {/* qualquer requisição renova a sessão no servidor e registra a atividade aqui */}
          <Button onClick={() => http.get('/api/usuarios/me').then(() => setRestante(null), () => {})}>Continuar conectado</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

type Pedido = { resolver: (r: Reautenticacao) => void; rejeitar: (e: Error) => void };

function DialogoReautenticacao() {
  const sessao = useSessao();
  const [pedido, setPedido] = useState<Pedido | null>(null);
  const [senha, setSenha] = useState('');
  const [codigo, setCodigo] = useState('');
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    configurarReautenticacao(() => new Promise((resolver, rejeitar) => setPedido({ resolver, rejeitar })));
    return () => configurarReautenticacao(null);
  }, []);

  const fechar = () => { setPedido(null); setSenha(''); setCodigo(''); setErro(null); };
  const cancelar = () => {
    pedido?.rejeitar(new ErroApi(403, { detail: 'Ação cancelada: a senha não foi confirmada.' }));
    fechar();
  };
  const confirmar = async (e: React.FormEvent) => {
    e.preventDefault();
    setEnviando(true);
    try {
      const r = await http.post<Reautenticacao>('/api/usuarios/me/reautenticacao', { senha, codigo: codigo || undefined });
      pedido?.resolver(r);
      fechar();
    } catch (err) {
      setErro(mensagemErro(err, 'Não foi possível confirmar.'));
    } finally {
      setEnviando(false);
    }
  };

  const comCodigo = sessao?.segundoFatorAtivo;
  return (
    <Dialog open={pedido !== null} onOpenChange={(a) => !a && cancelar()}>
      <DialogContent>
        <form onSubmit={confirmar} className="grid gap-4" noValidate>
          <DialogHeader>
            <DialogTitle>Confirme que é você</DialogTitle>
            <DialogDescription>
              Esta ação não se desfaz facilmente. Confirme a senha{comCodigo ? ' e o código do aplicativo autenticador' : ''}; a
              confirmação vale por 5 minutos nesta sessão.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-1.5">
            <Label htmlFor="reautenticacao-senha">Senha</Label>
            <Input id="reautenticacao-senha" type="password" autoComplete="current-password" value={senha}
                   onChange={(e) => setSenha(e.target.value)} />
          </div>
          {comCodigo && (
            <div className="grid gap-1.5">
              <Label htmlFor="reautenticacao-codigo">Código</Label>
              <Input id="reautenticacao-codigo" autoComplete="one-time-code" inputMode="numeric" value={codigo}
                     onChange={(e) => setCodigo(e.target.value)} />
            </div>
          )}
          {erro && <p role="alert" className="text-sm text-danger">{erro}</p>}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={cancelar}>Cancelar</Button>
            <Button type="submit" disabled={!senha || (comCodigo && !codigo) || enviando}>{enviando ? 'Conferindo…' : 'Confirmar'}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
