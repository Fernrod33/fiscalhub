import { useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Calculator, Landmark, PiggyBank } from 'lucide-react';
import { Alternador } from '@/components/alternador';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { EstadoVazio } from '@/components/estado-vazio';
import { Reveal } from '@/components/motion';
import { Selo } from '@/components/selo';
import { Skeleton } from '@/components/ui/skeleton';
import type { Apuracao, StatusConciliacao } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { REGIMES, STATUS_CONCILIACAO } from '@/lib/rotulos';
import { cn } from '@/lib/utils';
import { useClienteId } from '@/features/clientes/rota';
import { useConciliacao } from '@/features/situacao-fiscal/api';
import { useAlertasFiscais, useApuracoes, useSaldosFiscais } from '../api';
import { AlertasFiscais } from '../components/alertas-fiscais';
import { DemonstracaoLucro } from '../components/demonstracao-lucro';
import { DetalheCelula } from '../components/detalhe-celula';
import { MatrizTributos } from '../components/matriz-tributos';
import { SaldosFiscais } from '../components/saldos-fiscais';
import type { Celula, Periodicidade } from '../matriz';

/** Qual arquivo traz cada bloco (estado vazio). */
function ImporteOs({ clienteId, children }: { clienteId: number; children: ReactNode }) {
  return <>{children} Importe na aba <Link to={`/clientes/${clienteId}/importacoes`} className="text-primary underline">Importações</Link>.</>;
}

/**
 * Aba Tributos: matriz tributo × competência com apurado, declarado, pago, diferença e o status da conciliação;
 * demonstração do lucro real ou presumido, saldos fiscais e alertas de conferência. Débitos: aba Situação fiscal.
 */
export default function TributosPage() {
  const clienteId = useClienteId();
  const apuracoes = useApuracoes(clienteId);
  const conciliacao = useConciliacao(clienteId);
  const saldos = useSaldosFiscais(clienteId);
  const alertas = useAlertasFiscais(clienteId);
  const [periodicidade, setPeriodicidade] = useState<Periodicidade>('MENSAL');
  const [status, setStatus] = useState<StatusConciliacao | ''>('');
  const [celula, setCelula] = useState<Celula | null>(null);

  if (apuracoes.isPending || conciliacao.isPending) return <Skeleton className="h-96" />;
  const lista = apuracoes.data ?? [];
  const itens = conciliacao.data?.itens ?? [];
  const presentes = (Object.keys(STATUS_CONCILIACAO) as StatusConciliacao[]).filter((s) => itens.some((i) => i.status === s));
  const filtrados = status ? itens.filter((i) => i.status === status) : itens;

  return (
    <Reveal className="grid gap-4">
      <Cartao aria-labelledby="titulo-apurado" className="grid gap-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <CartaoTitulo id="titulo-apurado">Apurado × declarado × pago</CartaoTitulo>
          <div className="flex flex-wrap items-center gap-3">
            <label className="flex items-center gap-2 text-sm">
              <span className="text-muted-foreground">Status</span>
              <select value={status} onChange={(e) => setStatus(e.target.value as StatusConciliacao | '')}
                      className="h-8 rounded-md border bg-background px-2 text-sm">
                <option value="">Todos</option>
                {presentes.map((s) => <option key={s} value={s}>{STATUS_CONCILIACAO[s].rotulo}</option>)}
              </select>
            </label>
            <Alternador rotulo="Periodicidade" valor={periodicidade} aoMudar={setPeriodicidade}
                        opcoes={[['MENSAL', 'Mensal'], ['TRIMESTRAL', 'Trimestral'], ['ANUAL', 'Anual']]} />
          </div>
        </div>
        {itens.length === 0
          ? <EstadoVazio icone={Landmark} titulo="Nada para conciliar"
                         descricao={<ImporteOs clienteId={clienteId}>O apurado vem da ECF e das EFD; o declarado, da DCTF, da DCTFWeb e
                           da conta corrente do PR; o pago, dos DARF, do relatório de pagamentos e das GR-PR.</ImporteOs>} />
          : <MatrizTributos itens={filtrados} periodicidade={periodicidade} aoAbrir={setCelula} />}
        {(conciliacao.data?.alertas ?? []).length > 0 && (
          <ul className="grid gap-1 text-sm" aria-label="Alertas da conciliação">
            {conciliacao.data?.alertas.map((a, i) => (
              <li key={i} className="flex items-start gap-2"><Selo tom="warning">Alerta</Selo><span>{a.mensagem}</span></li>
            ))}
          </ul>
        )}
      </Cartao>

      <PainelLucro clienteId={clienteId} apuracoes={lista} />

      <Cartao aria-labelledby="titulo-saldos" className="grid gap-3">
        <CartaoTitulo id="titulo-saldos">Saldos fiscais</CartaoTitulo>
        {saldos.isPending
          ? <Skeleton className="h-32" />
          : (saldos.data ?? []).length === 0
            ? <EstadoVazio icone={PiggyBank} titulo="Nenhum saldo fiscal"
                           descricao={<ImporteOs clienteId={clienteId}>Prejuízo fiscal e base negativa vêm da Parte B do e-Lalur (ECF);
                             créditos de PIS e COFINS, da EFD-Contribuições; saldos credores de ICMS e IPI, da EFD ICMS/IPI.</ImporteOs>} />
            : <SaldosFiscais clienteId={clienteId} saldos={saldos.data ?? []} />}
      </Cartao>

      {(alertas.data ?? []).length > 0 && (
        <Cartao aria-labelledby="titulo-alertas" className="grid gap-3">
          <CartaoTitulo id="titulo-alertas">Alertas de conferência</CartaoTitulo>
          <AlertasFiscais clienteId={clienteId} alertas={alertas.data ?? []} />
        </Cartao>
      )}

      <DetalheCelula clienteId={clienteId} celula={celula} apuracoes={lista} aoFechar={() => setCelula(null)} />
    </Reveal>
  );
}

/** IRPJ/CSLL por período de apuração (ajuste anual ou trimestre): regime e demonstração do lucro em cascata. */
function PainelLucro({ clienteId, apuracoes }: { clienteId: number; apuracoes: Apuracao[] }) {
  const periodos = apuracoes.filter((a) => a.tributo === 'IRPJ' && a.periodicidade !== 'MENSAL');
  const [escolhido, setEscolhido] = useState<number>();
  const irpj = periodos.find((a) => a.id === escolhido) ?? periodos.at(-1);
  const csll = irpj && apuracoes.find((a) => a.tributo === 'CSLL' && a.dataInicio === irpj.dataInicio && a.dataFim === irpj.dataFim
    && a.periodicidade === irpj.periodicidade);

  return (
    <Cartao aria-labelledby="titulo-lucro" className="grid gap-3">
      <CartaoTitulo id="titulo-lucro">IRPJ e CSLL</CartaoTitulo>
      {!irpj
        ? <EstadoVazio icone={Calculator} titulo="Sem apuração de IRPJ e CSLL"
                       descricao={<ImporteOs clienteId={clienteId}>A demonstração do lucro real ou presumido vem da ECF.</ImporteOs>} />
        : (
          <>
            <div role="radiogroup" aria-label="Período de apuração" className="flex flex-wrap gap-2">
              {periodos.map((p) => (
                <button key={p.id} type="button" role="radio" aria-checked={p.id === irpj.id} onClick={() => setEscolhido(p.id)}
                        className={cn('num rounded-full border px-2.5 py-0.5 text-sm',
                          p.id === irpj.id ? 'border-primary bg-accent text-foreground' : 'text-muted-foreground hover:bg-accent')}>
                  {p.dataFim.slice(0, 4)} · {p.descricao}
                </button>
              ))}
            </div>
            <p className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
              {irpj.regime && <Selo tom="info">{REGIMES[irpj.regime] ?? irpj.regime}</Selo>}
              <span className="num">{fmt.data(irpj.dataInicio)} a {fmt.data(irpj.dataFim)} · {irpj.arquivo}</span>
            </p>
            <div className="grid gap-4 lg:grid-cols-2">
              <section aria-labelledby="titulo-irpj" className="grid gap-2">
                <h3 id="titulo-irpj" className="font-semibold">IRPJ</h3>
                <DemonstracaoLucro clienteId={clienteId} apuracao={irpj} />
              </section>
              {csll && (
                <section aria-labelledby="titulo-csll" className="grid gap-2">
                  <h3 id="titulo-csll" className="font-semibold">CSLL</h3>
                  <DemonstracaoLucro clienteId={clienteId} apuracao={csll} />
                </section>
              )}
            </div>
          </>
        )}
    </Cartao>
  );
}
