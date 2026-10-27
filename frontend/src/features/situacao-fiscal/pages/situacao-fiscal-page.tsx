import { useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, BadgeCheck, FileWarning, Gauge, Handshake, Landmark } from 'lucide-react';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { DataTable, type ColumnDef } from '@/components/data-table';
import { EstadoVazio } from '@/components/estado-vazio';
import { BarraProgresso, Reveal, Stagger, StaggerItem } from '@/components/motion';
import { Selo } from '@/components/selo';
import { Skeleton } from '@/components/ui/skeleton';
import type { CapacidadeOficial, Debito } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { FONTES_CAPACIDADE, ORGAO_CREDOR, SITUACAO_DEBITO, SITUACAO_PARCELAMENTO, TRIBUTOS } from '@/lib/rotulos';
import { useClienteId } from '@/features/clientes/rota';
import { useCapacidadeOficial, useDebitos, useParcelamentos, usePendencias } from '../api';
import { ListaOrigens, ValorDocumento } from '../components/origem-documento';
import { local, totaisPorOrgao } from '../situacao';

const ORGAOS = Object.keys(ORGAO_CREDOR) as Debito['orgaoCredor'][];

/** Débito: inscrição/CDA/processo ou a competência. */
const identificacao = (d: Debito) => d.numeroInscricao ?? (d.competencia ? `Competência ${fmt.competencia(d.competencia)}` : d.periodo ?? '—');

/**
 * Aba Situação fiscal: o que impede a certidão, débitos por órgão e situação (cada um com as fontes), parcelamentos e
 * transações com o progresso das parcelas, pendências de declaração e a CAPAG oficial. Tudo vem dos documentos dos
 * órgãos (situação fiscal, Regularize, CAPAG, conta corrente e RTI do PR).
 */
export default function SituacaoFiscalPage() {
  const clienteId = useClienteId();
  const debitos = useDebitos(clienteId);
  const [orgao, setOrgao] = useState<Debito['orgaoCredor'] | ''>('');
  const [situacao, setSituacao] = useState<Debito['situacao'] | ''>('');

  if (debitos.isPending) return <Skeleton className="h-96" />;
  const lista = debitos.data ?? [];
  const impeditivos = lista.filter((d) => d.impedeCertidao);
  const filtrados = lista.filter((d) => (!orgao || d.orgaoCredor === orgao) && (!situacao || d.situacao === situacao));

  const colunas: ColumnDef<Debito>[] = [
    { accessorKey: 'orgaoCredor', header: 'Órgão', cell: ({ getValue }) => ORGAO_CREDOR[getValue<Debito['orgaoCredor']>()] },
    { accessorKey: 'tributo', header: 'Tributo', cell: ({ getValue }) => TRIBUTOS[getValue<string>()] ?? getValue<string>() },
    { id: 'identificacao', header: 'Inscrição / competência', accessorFn: identificacao, meta: { classe: 'num whitespace-nowrap' } },
    {
      accessorKey: 'situacao', header: 'Situação', meta: { classe: 'whitespace-nowrap' },
      cell: ({ row: { original: d } }) => (
        <span className="flex flex-wrap gap-1">
          <Selo tom={SITUACAO_DEBITO[d.situacao].tom}>{SITUACAO_DEBITO[d.situacao].rotulo}</Selo>
          {d.impedeCertidao && <Selo tom="danger">Impede certidão</Selo>}
          {d.divergente && <Selo tom="warning">Fontes divergem</Selo>}
        </span>
      ),
    },
    {
      accessorKey: 'valorTotal', header: 'Total', meta: { classe: 'text-right' },
      cell: ({ row: { original: d } }) => (
        <ValorDocumento clienteId={clienteId} rotulo={`${TRIBUTOS[d.tributo] ?? d.tributo} ${identificacao(d)}`} valor={d.valorTotal}
                        origens={d.origens.map((o) => o.origem)} />
      ),
    },
  ];

  return (
    <Reveal className="grid gap-4">
      <Cartao aria-labelledby="titulo-certidao" className="grid gap-3">
        <CartaoTitulo id="titulo-certidao">Certidão</CartaoTitulo>
        {lista.length === 0
          ? <EstadoVazio icone={Landmark} titulo="Nenhum documento de situação fiscal"
                         descricao={<>Importe o Relatório de Situação Fiscal, o extrato do Regularize e a conta corrente do PR na aba{' '}
                           <Link to={`/clientes/${clienteId}/importacoes`} className="text-primary underline">Importações</Link>.</>} />
          : impeditivos.length === 0
            ? <p className="flex items-center gap-2 text-success"><BadgeCheck className="size-5" aria-hidden />Nenhum débito impede a certidão nos documentos importados.</p>
            : (
              <div className="grid gap-2" role="alert">
                <p className="flex items-center gap-2 font-semibold text-danger">
                  <AlertTriangle className="size-5" aria-hidden />
                  {impeditivos.length === 1 ? '1 débito impede a certidão' : `${impeditivos.length} débitos impedem a certidão`}
                </p>
                <ul className="grid gap-2">
                  {impeditivos.map((d) => (
                    <li key={d.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-danger/40 p-2 text-sm">
                      <span>{ORGAO_CREDOR[d.orgaoCredor]} · {TRIBUTOS[d.tributo] ?? d.tributo} · <span className="num">{identificacao(d)}</span>
                        {' '}— {SITUACAO_DEBITO[d.situacao].rotulo}{d.negociacao ? '' : ', sem negociação'}</span>
                      <span className="num font-semibold">{fmt.moeda(d.valorTotal)}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
      </Cartao>

      <Cartao aria-labelledby="titulo-debitos" className="grid gap-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <CartaoTitulo id="titulo-debitos">Débitos</CartaoTitulo>
          <div className="flex flex-wrap gap-3 text-sm">
            <label className="flex items-center gap-2"><span className="text-muted-foreground">Órgão</span>
              <select value={orgao} onChange={(e) => setOrgao(e.target.value as Debito['orgaoCredor'] | '')} className="h-8 rounded-md border bg-background px-2">
                <option value="">Todos</option>
                {ORGAOS.map((o) => <option key={o} value={o}>{ORGAO_CREDOR[o]}</option>)}
              </select>
            </label>
            <label className="flex items-center gap-2"><span className="text-muted-foreground">Situação</span>
              <select value={situacao} onChange={(e) => setSituacao(e.target.value as Debito['situacao'] | '')} className="h-8 rounded-md border bg-background px-2">
                <option value="">Todas</option>
                {(Object.keys(SITUACAO_DEBITO) as Debito['situacao'][]).map((s) => <option key={s} value={s}>{SITUACAO_DEBITO[s].rotulo}</option>)}
              </select>
            </label>
          </div>
        </div>
        <TotaisPorOrgao debitos={lista} />
        <DataTable rotulo="Débitos do cliente" colunas={colunas} dados={filtrados} filtro="Filtrar débitos"
                   vazio={<EstadoVazio icone={Landmark} titulo="Nenhum débito" descricao="Nenhum débito com esses filtros." />} />
      </Cartao>

      <Parcelamentos clienteId={clienteId} />
      <div className="grid gap-4 lg:grid-cols-2">
        <Pendencias clienteId={clienteId} />
        <Capag clienteId={clienteId} />
      </div>
    </Reveal>
  );
}

function TotaisPorOrgao({ debitos }: { debitos: Debito[] }) {
  return (
    <dl className="grid grid-cols-2 gap-2 sm:grid-cols-4">
      {totaisPorOrgao(debitos).map(({ orgao, total }) => (
        <div key={orgao} className="rounded-md border p-2">
          <dt className="text-xs text-muted-foreground">{ORGAO_CREDOR[orgao]}</dt>
          <dd className="num font-semibold">{fmt.moeda(total)}</dd>
        </div>
      ))}
    </dl>
  );
}

function Parcelamentos({ clienteId }: { clienteId: number }) {
  const parcelamentos = useParcelamentos(clienteId);
  return (
    <Cartao aria-labelledby="titulo-parcelamentos" className="grid gap-3">
      <CartaoTitulo id="titulo-parcelamentos">Parcelamentos e transações</CartaoTitulo>
      {parcelamentos.isPending
        ? <Skeleton className="h-24" />
        : (parcelamentos.data ?? []).length === 0
          ? <EstadoVazio icone={Handshake} titulo="Nenhum parcelamento" descricao="Vêm do extrato do Regularize e da conta corrente do PR." />
          : (
            <Stagger as="ul" className="grid gap-3 md:grid-cols-2">
              {parcelamentos.data?.map((p) => {
                const s = SITUACAO_PARCELAMENTO[p.situacao] ?? { rotulo: p.situacao, tom: 'info' as const };
                return (
                  <StaggerItem as="li" key={p.id} className="grid gap-2 rounded-md border p-3">
                    <div className="flex flex-wrap items-baseline justify-between gap-2">
                      <p className="font-semibold">{ORGAO_CREDOR[p.orgao]} · <span className="num">{p.numero}</span></p>
                      <Selo tom={s.tom}>{s.rotulo}</Selo>
                    </div>
                    <p className="text-sm text-muted-foreground">{p.modalidade}</p>
                    {p.parcelasTotal != null && (
                      <>
                        <BarraProgresso valor={p.parcelasPagas ?? 0} maximo={p.parcelasTotal}
                                        rotulo={`Parcelas pagas do ${p.numero}: ${p.parcelasPagas} de ${p.parcelasTotal}`} />
                        <p className="num text-sm">{p.parcelasPagas} de {p.parcelasTotal} parcelas · parcela {fmt.moeda(p.valorParcela)} · saldo {fmt.moeda(p.saldo)}</p>
                      </>
                    )}
                    {p.debitos.length > 0 && <p className="num text-xs text-muted-foreground">Débitos: {p.debitos.join('; ')}</p>}
                    <p className="text-xs text-muted-foreground">{p.origem.arquivo} · {local(p.origem)}</p>
                  </StaggerItem>
                );
              })}
            </Stagger>
          )}
    </Cartao>
  );
}

function Pendencias({ clienteId }: { clienteId: number }) {
  const pendencias = usePendencias(clienteId);
  return (
    <Cartao aria-labelledby="titulo-pendencias" className="grid gap-3">
      <CartaoTitulo id="titulo-pendencias">Pendências</CartaoTitulo>
      {pendencias.isPending
        ? <Skeleton className="h-24" />
        : (pendencias.data ?? []).length === 0
          ? <EstadoVazio icone={FileWarning} titulo="Nenhuma pendência" descricao="Omissões de declaração vêm do Relatório de Situação Fiscal." />
          : (
            <ul className="grid gap-2">
              {pendencias.data?.map((p) => (
                <li key={p.id} className="grid gap-1 rounded-md border p-2 text-sm">
                  <span className="flex flex-wrap items-center justify-between gap-2">
                    <span className="font-medium">{p.descricao}</span>
                    {p.situacao && <Selo tom="danger">{p.situacao}</Selo>}
                  </span>
                  <ListaOrigens clienteId={clienteId} origens={[p.origem]} />
                </li>
              ))}
            </ul>
          )}
    </Cartao>
  );
}

function Capag({ clienteId }: { clienteId: number }) {
  const capacidade = useCapacidadeOficial(clienteId);
  return (
    <Cartao aria-labelledby="titulo-capag" className="grid gap-3">
      <CartaoTitulo id="titulo-capag">Capacidade de pagamento oficial</CartaoTitulo>
      {capacidade.isPending
        ? <Skeleton className="h-24" />
        : (capacidade.data ?? []).length === 0
          ? <EstadoVazio icone={Gauge} titulo="Sem CAPAG importada" descricao="Importe a tela da CAPAG do Regularize." />
          : capacidade.data?.map((c) => <ItemCapag key={c.id} clienteId={clienteId} c={c} />)}
    </Cartao>
  );
}

function ItemCapag({ clienteId, c }: { clienteId: number; c: CapacidadeOficial }) {
  return (
    <div className="grid gap-2">
      <div className="flex items-center gap-3">
        <span className="num grid size-12 place-items-center rounded-md border text-2xl font-semibold" aria-label={`Classe ${c.classe}`}>{c.classe}</span>
        <div className="text-sm">
          <p className="font-medium">{FONTES_CAPACIDADE[c.fonte] ?? c.fonte}</p>
          <p className="num text-muted-foreground">Referência {fmt.data(c.dataReferencia)}{c.razao != null && <> · capacidade ÷ passivo = {fmt.decimal(c.razao)}x</>}</p>
        </div>
      </div>
      <dl className="grid grid-cols-[1fr_auto] gap-x-4 text-sm">
        <dt className="text-muted-foreground">Capacidade de pagamento</dt><dd className="num text-right">{fmt.moeda(c.capacidade)}</dd>
        <dt className="text-muted-foreground">Passivo considerado</dt><dd className="num text-right">{fmt.moeda(c.passivo)}</dd>
      </dl>
      {c.classeConsistente === false && (
        <p role="alert" className="flex items-start gap-1.5 text-sm text-warning">
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
          A classe não bate com a razão capacidade ÷ passivo pela Portaria PGFN 6.757/2022 (A ≥ 2x; B ≥ 1x; C ≥ 0,5x; D &lt; 0,5x).
        </p>
      )}
      <ListaOrigens clienteId={clienteId} origens={[c.origem]} />
    </div>
  );
}
