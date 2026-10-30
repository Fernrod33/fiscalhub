import { useId, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronDown, RefreshCw, ShieldCheck } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { EstadoVazio } from '@/components/estado-vazio';
import { Collapse, Reveal } from '@/components/motion';
import { Selo } from '@/components/selo';
import { ValorOrigem } from '@/components/valor-origem';
import type { PontoAtencao } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { mensagemErro } from '@/lib/http';
import { cn } from '@/lib/utils';
import { usePermissoes } from '@/features/auth/permissoes';
import { useClienteId } from '@/features/clientes/rota';
import { ListaOrigens } from '@/features/situacao-fiscal/components/origem-documento';
import { useMudarEstado, useNaoAvaliadas, usePonto, usePontos, useReavaliar } from '../api';
import { SeloSeveridade } from '../components/severidade';
import { CATEGORIAS, ESTADOS, SEVERIDADES } from '../rotulos';

/** Mudança de estado com comentário (obrigatório para descartar) e o histórico. */
function Estado({ clienteId, p }: { clienteId: number; p: PontoAtencao }) {
  const { alterar } = usePermissoes();
  const detalhe = usePonto(clienteId, p.id);
  const mudar = useMudarEstado(clienteId);
  const [estado, setEstado] = useState(p.estado);
  const [comentario, setComentario] = useState('');
  const exige = estado === 'DESCARTADO' && !comentario.trim();
  return (
    <div className="grid gap-2">
      {alterar && (
        <form className="flex flex-wrap items-end gap-2" onSubmit={(e) => {
          e.preventDefault();
          mudar.mutate({ id: p.id, estado, comentario: comentario || undefined }, {
            onSuccess: () => { toast.success(`Ponto marcado como ${ESTADOS[estado].toLowerCase()}.`); setComentario(''); },
            onError: (err) => toast.error(mensagemErro(err, 'Não foi possível mudar o estado.')) });
        }}>
          <label className="grid gap-1 text-sm">Novo estado
            <select value={estado} onChange={(e) => setEstado(e.target.value)} className="h-9 rounded-md border bg-background px-2">
              {Object.entries(ESTADOS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </label>
          <label className="grid flex-1 gap-1 text-sm">Comentário{estado === 'DESCARTADO' && ' (justificativa obrigatória)'}
            <input value={comentario} onChange={(e) => setComentario(e.target.value)} required={estado === 'DESCARTADO'} maxLength={1000}
                   className="h-9 rounded-md border bg-background px-2" />
          </label>
          <Button type="submit" size="sm" disabled={mudar.isPending || exige || estado === p.estado && !comentario}>Salvar</Button>
        </form>
      )}
      <ol className="grid gap-1 text-xs text-muted-foreground" aria-label="Histórico de estados">
        {(detalhe.data?.historico ?? []).map((h, i) => (
          <li key={i}>{fmt.dataHora(h.em)} · {h.usuario}: {h.de ? `${ESTADOS[h.de]} → ` : ''}{ESTADOS[h.para]}{h.comentario && ` — ${h.comentario}`}</li>
        ))}
      </ol>
    </div>
  );
}

function Achado({ clienteId, p }: { clienteId: number; p: PontoAtencao }) {
  const [aberto, setAberto] = useState(false);
  const id = useId();
  return (
    <li className="grid gap-2 rounded-md border p-3">
      <button type="button" aria-expanded={aberto} aria-controls={id} onClick={() => setAberto((a) => !a)}
              className="flex w-full flex-wrap items-center gap-2 text-left">
        <ChevronDown aria-hidden className={cn('size-4 shrink-0 transition-transform', aberto && 'rotate-180')} />
        <SeloSeveridade severidade={p.severidade} />
        <Selo tom="info">{CATEGORIAS[p.categoria]}</Selo>
        <span className="font-medium">{p.titulo}</span>
        <span className="ml-auto flex items-center gap-2 text-sm">
          {p.valor != null && <span className="num">{fmt.moeda(p.valor)}</span>}
          <span className="text-xs text-muted-foreground">{ESTADOS[p.estado]} · {p.regra}</span>
        </span>
      </button>
      <Collapse aberto={aberto} id={id}>
        <div className="grid gap-3 pl-6 text-sm">
          <p>{p.descricao}</p>
          <p><span className="font-medium">Por que importa:</span> {p.fundamento}</p>
          <p><span className="font-medium">O que fazer:</span> {p.recomendacao}</p>
          <ul className="grid gap-1" aria-label={`Evidências — ${p.titulo}`}>
            {p.evidencias.map((e, i) => (
              <li key={i} className="flex flex-wrap items-center gap-2">
                <Link to={`/clientes/${clienteId}/${e.link}`} className="text-primary underline-offset-4 hover:underline">{e.descricao}</Link>
                {e.valor != null && (e.origem
                  ? <span className="w-40"><ValorOrigem rotulo={`Origem de ${e.descricao}: ${fmt.moeda(e.valor)}`} texto={fmt.moeda(e.valor)}>
                      {() => <ListaOrigens clienteId={clienteId} origens={[e.origem!]} />}
                    </ValorOrigem></span>
                  : <span className="num">{fmt.moeda(e.valor)}</span>)}
              </li>
            ))}
          </ul>
          <p className="text-xs text-muted-foreground">Detectado em {fmt.dataHora(p.detectadoEm)}; confirmado em {fmt.dataHora(p.confirmadoEm)}; regra {p.versaoRegra}.</p>
          {aberto && <Estado clienteId={clienteId} p={p} />}
        </div>
      </Collapse>
    </li>
  );
}

/** Pontos de atenção do cliente: priorizados, com filtros e busca; cada um com fundamento, recomendação e evidências. */
export default function PontosAtencaoPage() {
  const clienteId = useClienteId();
  const pontos = usePontos(clienteId);
  const nao = useNaoAvaliadas(clienteId);
  const reavaliar = useReavaliar(clienteId);
  const [severidade, setSeveridade] = useState('');
  const [categoria, setCategoria] = useState('');
  const [estado, setEstado] = useState('ABERTOS');
  const [busca, setBusca] = useState('');
  const lista = useMemo(() => (pontos.data ?? []).filter((p) => (!severidade || p.severidade === severidade)
    && (!categoria || p.categoria === categoria)
    && (estado === 'TODOS' || (estado === 'ABERTOS' ? ['NOVO', 'EM_ANALISE'].includes(p.estado) : p.estado === estado))
    && (!busca || `${p.titulo} ${p.descricao}`.toLowerCase().includes(busca.toLowerCase()))), [pontos.data, severidade, categoria, estado, busca]);

  return (
    <Reveal className="grid gap-4">
      <Cartao aria-labelledby="titulo-pontos" className="grid gap-3">
        <CartaoTitulo id="titulo-pontos" acoes={
          <Button variant="outline" size="sm" disabled={reavaliar.isPending} onClick={() => reavaliar.mutate(undefined, {
            onSuccess: (r) => toast.success(`Reavaliado: ${r.abertos} abertos, ${r.novos} novos, ${r.resolvidos} resolvidos.`),
            onError: (e) => toast.error(mensagemErro(e, 'Não foi possível reavaliar.')) })}>
            <RefreshCw aria-hidden /> Reavaliar
          </Button>}>
          Pontos de atenção
        </CartaoTitulo>
        <div className="flex flex-wrap items-end gap-3 text-sm" role="search" aria-label="Filtros dos pontos de atenção">
          <label className="grid gap-1">Severidade
            <select value={severidade} onChange={(e) => setSeveridade(e.target.value)} className="h-9 rounded-md border bg-background px-2">
              <option value="">Todas</option>
              {Object.entries(SEVERIDADES).map(([k, v]) => <option key={k} value={k}>{v.rotulo}</option>)}
            </select>
          </label>
          <label className="grid gap-1">Categoria
            <select value={categoria} onChange={(e) => setCategoria(e.target.value)} className="h-9 rounded-md border bg-background px-2">
              <option value="">Todas</option>
              {Object.entries(CATEGORIAS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </label>
          <label className="grid gap-1">Estado
            <select value={estado} onChange={(e) => setEstado(e.target.value)} className="h-9 rounded-md border bg-background px-2">
              <option value="ABERTOS">Abertos</option>
              <option value="TODOS">Todos</option>
              {Object.entries(ESTADOS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </label>
          <label className="grid flex-1 gap-1">Buscar
            <input type="search" value={busca} onChange={(e) => setBusca(e.target.value)} className="h-9 rounded-md border bg-background px-2" />
          </label>
        </div>
        {pontos.isPending ? <Skeleton className="h-40" />
          : lista.length === 0
            ? <EstadoVazio icone={ShieldCheck} titulo="Nenhum ponto de atenção" descricao="Nada a apontar com os filtros escolhidos." />
            : <ul className="grid gap-2" aria-label="Pontos de atenção">{lista.map((p) => <Achado key={p.id} clienteId={clienteId} p={p} />)}</ul>}
      </Cartao>

      {(nao.data ?? []).length > 0 && (
        <Cartao aria-labelledby="titulo-nao-avaliadas" className="grid gap-2">
          <CartaoTitulo id="titulo-nao-avaliadas">Regras não avaliadas</CartaoTitulo>
          <ul className="grid gap-1 text-sm" aria-label="Regras não avaliadas">
            {nao.data!.map((r) => <li key={r.codigo}><span className="font-medium">{r.codigo} · {r.titulo}:</span> {r.falta}</li>)}
          </ul>
        </Cartao>
      )}
    </Reveal>
  );
}
