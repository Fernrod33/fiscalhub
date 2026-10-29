import { useMemo, useState } from 'react';
import { Calculator, RefreshCw, Save, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { Confirmar } from '@/components/confirmar';
import { DataTable, type ColumnDef } from '@/components/data-table';
import { EstadoVazio } from '@/components/estado-vazio';
import { Reveal, Stagger, StaggerItem } from '@/components/motion';
import { Selo } from '@/components/selo';
import type { Debito, ResultadoModalidade, Simulacao, SimulacaoSalva } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { mensagemErro } from '@/lib/http';
import { ORGAO_CREDOR } from '@/lib/rotulos';
import { cn } from '@/lib/utils';
import { usePermissoes } from '@/features/auth/permissoes';
import { useClienteId } from '@/features/clientes/rota';
import { useDebitos } from '@/features/situacao-fiscal/api';
import { useExcluirSimulacao, useRecalcular, useSalvarSimulacao, useSimulacao, useSimulacoesSalvas, useSimular } from '../api';
import { DetalheModalidade } from '../components/detalhe-modalidade';

const hoje = () => new Date().toISOString().slice(0, 10);
const chave = (m: ResultadoModalidade) => `${m.chave}|${m.cenario}`;

function descricao(d: Debito) {
  return `${d.tributo}${d.numeroInscricao ? ` ${d.numeroInscricao}` : d.competencia ? ` ${fmt.competencia(d.competencia)}` : ''}`;
}

/** Passo 1: débitos (todos importados), data-base e prazo; nenhum valor é digitado. */
function Passo1({ debitos, aoSimular, ocupado }: {
  debitos: Debito[]; ocupado: boolean;
  aoSimular: (p: { debitoIds: number[]; dataBase: string; parcelas?: number; reparcelamento: boolean }) => void;
}) {
  const [marcados, setMarcados] = useState<Set<number>>(new Set());
  const [dataBase, setDataBase] = useState(hoje());
  const [parcelas, setParcelas] = useState('');
  const [reparcelamento, setReparcelamento] = useState(false);
  const porOrgao = useMemo(() => debitos.reduce<Record<string, Debito[]>>((g, d) => ((g[d.orgaoCredor] ??= []).push(d), g), {}), [debitos]);
  const alternar = (id: number) => setMarcados((s) => { const n = new Set(s); if (n.has(id)) n.delete(id); else n.add(id); return n; });
  return (
    <form className="grid gap-4" onSubmit={(e) => {
      e.preventDefault();
      aoSimular({ debitoIds: [...marcados], dataBase, parcelas: parcelas ? Number(parcelas) : undefined, reparcelamento });
    }}>
      {Object.entries(porOrgao).map(([orgao, lista]) => (
        <fieldset key={orgao} className="grid gap-1">
          <legend className="mb-1 text-sm font-medium">{ORGAO_CREDOR[orgao as Debito['orgaoCredor']]}</legend>
          {lista.map((d) => (
            <label key={d.id} className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={marcados.has(d.id)} onChange={() => alternar(d.id)} />
              <span>{descricao(d)}</span>
              <span className="num ml-auto text-muted-foreground">{fmt.moeda(d.valorTotal ?? d.valorPrincipal)}</span>
              <span className="text-xs text-muted-foreground">{d.situacaoDocumento}</span>
            </label>
          ))}
        </fieldset>
      ))}
      <div className="flex flex-wrap items-end gap-4">
        <label className="grid gap-1 text-sm">Data-base
          <input type="date" required value={dataBase} onChange={(e) => setDataBase(e.target.value)} className="rounded-md border bg-surface px-2 py-1" />
        </label>
        <label className="grid gap-1 text-sm">Prestações (vazio = o máximo de cada modalidade)
          <input type="number" min={1} max={145} value={parcelas} onChange={(e) => setParcelas(e.target.value)} className="w-32 rounded-md border bg-surface px-2 py-1" />
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={reparcelamento} onChange={(e) => setReparcelamento(e.target.checked)} /> Simular reparcelamento dos já parcelados
        </label>
        <Button type="submit" disabled={marcados.size === 0 || ocupado}><Calculator aria-hidden /> Simular</Button>
      </div>
    </form>
  );
}

/** Passo 2: as modalidades de cada órgão lado a lado; a recomendada em destaque. */
function Comparacao({ s, escolhida, aoEscolher }: { s: Simulacao; escolhida?: string; aoEscolher: (k: string) => void }) {
  const orgaos = [...new Set(s.modalidades.map((m) => m.orgao))];
  const recomendadas = new Set(s.recomendacao.map((r) => `${r.chave}|${r.cenario}`));
  return (
    <div className="grid gap-6">
      {s.insumos.avisos.length > 0 && (
        <ul className="grid list-disc gap-1 pl-5 text-sm text-muted-foreground" aria-label="Avisos dos insumos">
          {s.insumos.avisos.map((a) => <li key={a}>{a}</li>)}
        </ul>
      )}
      {orgaos.map((orgao) => (
        <section key={orgao} aria-label={`Modalidades — ${ORGAO_CREDOR[orgao]}`} className="grid gap-2">
          <h3 className="font-medium">{ORGAO_CREDOR[orgao]}</h3>
          <Stagger as="ul" className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {s.modalidades.filter((m) => m.orgao === orgao && m.tipo !== 'PROGRAMA').map((m) => {
              const k = chave(m);
              return (
                <StaggerItem as="li" key={k}>
                  <button type="button" aria-pressed={escolhida === k} onClick={() => aoEscolher(k)}
                          aria-label={`${m.nome}${m.cenarioRotulo ? ` — ${m.cenarioRotulo}` : ''}`}
                          className={cn('grid h-full w-full gap-2 rounded-md border p-3 text-left transition-colors hover:bg-accent',
                            escolhida === k && 'border-primary ring-2 ring-primary', !m.elegivel && 'opacity-80')}>
                    <span className="flex flex-wrap items-center gap-2 text-sm font-medium">
                      {m.nome}
                      {recomendadas.has(k) && <Selo tom="success">recomendada</Selo>}
                      {m.fonteSecundaria && <Selo tom="warning">fonte secundária</Selo>}
                    </span>
                    {m.cenarioRotulo && <span className="text-xs text-muted-foreground">{m.cenarioRotulo}</span>}
                    {!m.elegivel && <span className="text-xs text-danger">{m.bloqueio ?? 'Não elegível'}</span>}
                    <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-sm">
                      <dt className="text-muted-foreground">Custo projetado</dt><dd className="num text-right font-semibold">{fmt.moeda(m.custoProjetado)}</dd>
                      <dt className="text-muted-foreground">Desconto</dt><dd className="num text-right">{m.desconto ? fmt.moeda(m.desconto.valor) : '—'}</dd>
                      <dt className="text-muted-foreground">Entrada</dt><dd className="num text-right">{fmt.moeda(m.entrada)}</dd>
                      <dt className="text-muted-foreground">Prestações</dt><dd className="num text-right">{m.cronograma.length}</dd>
                      <dt className="text-muted-foreground">Parcela</dt><dd className="num text-right">{fmt.moeda(m.valorParcela)}</dd>
                      <dt className="text-muted-foreground">× à vista</dt><dd className="num text-right">{fmt.moeda(m.economiaVsAVista)}</dd>
                    </dl>
                    {m.selo && <Selo tom={m.selo.cabe ? 'success' : 'warning'}>{m.selo.texto}</Selo>}
                  </button>
                </StaggerItem>
              );
            })}
          </Stagger>
          {s.modalidades.filter((m) => m.orgao === orgao && m.tipo === 'PROGRAMA').map((m) => (
            <p key={m.chave} className="text-xs text-muted-foreground">{m.nome}: {m.bloqueio}</p>
          ))}
        </section>
      ))}
    </div>
  );
}

function Resultado({ clienteId, s, salvar }: { clienteId: number; s: Simulacao; salvar?: () => void }) {
  const [escolhida, setEscolhida] = useState<string>(() => {
    const r = s.recomendacao[0];
    return r ? `${r.chave}|${r.cenario}` : '';
  });
  const m = s.modalidades.find((x) => chave(x) === escolhida);
  return (
    <Reveal className="grid gap-4">
      <p className="text-sm text-muted-foreground">
        Data-base {fmt.data(s.dataBase)} · regras {s.versaoRegras} · {s.versaoIndices}
        {s.insumos.capagOficial && ` · CAPAG oficial ${s.insumos.capagOficial}`}
        {s.insumos.parcelaSustentavel != null && ` · parcela sustentável ${fmt.moeda(s.insumos.parcelaSustentavel)}`}
      </p>
      <Comparacao s={s} escolhida={escolhida} aoEscolher={setEscolhida} />
      {salvar && <div><Button onClick={salvar}><Save aria-hidden /> Salvar simulação</Button></div>}
      {m && (
        <Cartao aria-labelledby="titulo-detalhe">
          <CartaoTitulo id="titulo-detalhe">{m.nome}{m.cenarioRotulo ? ` — ${m.cenarioRotulo}` : ''}</CartaoTitulo>
          <DetalheModalidade clienteId={clienteId} m={m} />
        </Cartao>
      )}
    </Reveal>
  );
}

function Historico({ clienteId, aoAbrir }: { clienteId: number; aoAbrir: (id: number) => void }) {
  const salvas = useSimulacoesSalvas(clienteId);
  const recalcular = useRecalcular(clienteId);
  const excluir = useExcluirSimulacao(clienteId);
  const { alterar } = usePermissoes();
  const [apagar, setApagar] = useState<number>();
  const colunas: ColumnDef<SimulacaoSalva>[] = [
    { accessorKey: 'versao', header: 'Versão', cell: ({ row }) => `v${row.original.versao}` },
    { accessorKey: 'dataBase', header: 'Data-base', cell: ({ getValue }) => fmt.data(getValue<string>()) },
    { accessorKey: 'criadoEm', header: 'Salva em', cell: ({ getValue }) => fmt.dataHora(getValue<string>()) },
    { accessorKey: 'recomendacao', header: 'Recomendação', cell: ({ row }) => row.original.motorAntigo
      ? <Selo tom="warning">motor antigo</Selo> : row.original.recomendacao ?? '—' },
    { accessorKey: 'custoRecomendado', header: 'Custo', cell: ({ getValue }) => fmt.moeda(getValue<number>()), meta: { classe: 'text-right whitespace-nowrap' } },
    { id: 'acoes', header: 'Ações', cell: ({ row }) => (
      <span className="flex gap-1">
        <Button size="sm" variant="outline" onClick={() => aoAbrir(row.original.id)}>Abrir</Button>
        {alterar && (
          <Button size="sm" variant="outline" disabled={recalcular.isPending} aria-label={`Recalcular a simulação v${row.original.versao}`}
                  onClick={() => recalcular.mutate(row.original.id, {
                    onSuccess: () => toast.success('Nova versão criada com as regras e os índices de hoje.'),
                    onError: (e) => toast.error(mensagemErro(e, 'Não foi possível recalcular.')) })}>
            <RefreshCw aria-hidden /> Recalcular
          </Button>
        )}
        {alterar && (
          <Button size="sm" variant="ghost" aria-label={`Excluir a simulação v${row.original.versao}`} onClick={() => setApagar(row.original.id)}>
            <Trash2 aria-hidden />
          </Button>
        )}
      </span>
    ) },
  ];
  return (
    <>
      <DataTable rotulo="Simulações salvas" colunas={colunas} dados={salvas.data} carregando={salvas.isPending}
                 vazio={<EstadoVazio icone={Calculator} titulo="Nenhuma simulação salva" descricao="Simule acima e salve para guardar a proposta." />} />
      <Confirmar aberto={apagar != null} aoMudar={(a) => !a && setApagar(undefined)} titulo="Excluir simulação"
                 descricao="A simulação sai do histórico; relatórios já emitidos continuam." rotuloAcao="Excluir" ocupado={excluir.isPending}
                 aoConfirmar={() => apagar != null && excluir.mutate(apagar, { onSuccess: () => setApagar(undefined) })} />
    </>
  );
}

/** Simulações de parcelamento e transação: débitos → comparação → detalhe; histórico com versões. */
export default function SimulacoesPage() {
  const clienteId = useClienteId();
  const debitos = useDebitos(clienteId);
  const simular = useSimular(clienteId);
  const salvar = useSalvarSimulacao(clienteId);
  const { alterar } = usePermissoes();
  const [pedido, setPedido] = useState<Parameters<typeof simular.mutate>[0]>();
  const [aberta, setAberta] = useState<number>();
  const salva = useSimulacao(clienteId, aberta);
  const atual = aberta != null ? salva.data : simular.data;

  return (
    <div className="grid gap-4">
      <Cartao aria-labelledby="titulo-simular">
        <CartaoTitulo id="titulo-simular">1. Débitos e data-base</CartaoTitulo>
        {debitos.isPending ? <Skeleton className="h-32" />
          : !debitos.data?.length
            ? <EstadoVazio icone={Calculator} titulo="Nenhum débito importado" descricao="Importe os relatórios dos órgãos (Situação Fiscal, Regularize, conta corrente do PR)." />
            : <Passo1 debitos={debitos.data} ocupado={simular.isPending} aoSimular={(p) => {
                setAberta(undefined);
                setPedido(p);
                simular.mutate(p, { onError: (e) => toast.error(mensagemErro(e, 'Não foi possível simular.')) });
              }} />}
      </Cartao>

      <Cartao aria-labelledby="titulo-comparar">
        <CartaoTitulo id="titulo-comparar">2. Comparar e 3. detalhar</CartaoTitulo>
        {simular.isPending || (aberta != null && salva.isPending) ? <Skeleton className="h-48" />
          : atual?.motorAntigo ? <p className="text-sm">{atual.motorAntigo}</p>
            : atual ? <Resultado key={`${atual.id ?? 'nova'}-${atual.versao}`} clienteId={clienteId} s={atual}
                                 salvar={alterar && aberta == null && pedido ? () => salvar.mutate(pedido, {
                                   onSuccess: () => toast.success('Simulação salva.'),
                                   onError: (e) => toast.error(mensagemErro(e, 'Não foi possível salvar.')) }) : undefined} />
              : <p className="text-sm text-muted-foreground">Escolha os débitos e simule.</p>}
      </Cartao>

      <Cartao aria-labelledby="titulo-historico">
        <CartaoTitulo id="titulo-historico">Histórico</CartaoTitulo>
        <Historico clienteId={clienteId} aoAbrir={setAberta} />
      </Cartao>
    </div>
  );
}
