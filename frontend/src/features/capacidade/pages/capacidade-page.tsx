import { useState } from 'react';
import { useQueries } from '@tanstack/react-query';
import { AlertTriangle, Gauge } from 'lucide-react';
import { toast } from 'sonner';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { EstadoVazio } from '@/components/estado-vazio';
import { Grafico } from '@/components/grafico';
import { AnimatedNumber, Reveal } from '@/components/motion';
import type { Capacidade, Dfc, ParametrosEscritorio, ResultadoCapacidade } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { http, mensagemErro } from '@/lib/http';
import { cn } from '@/lib/utils';
import { usePermissoes } from '@/features/auth/permissoes';
import { useClienteId } from '@/features/clientes/rota';
import { useCapacidade, useDfcExercicios, useSalvarParametros } from '../api';
import { SeloRotulo, TabelaCriterios } from '../components/criterios';
import { SimuladorPr } from '../components/simulador-pr';

const metodologia = (c: Capacidade, chave: string) => c.metodologias.find((m) => m.metodologia === chave)!;

/** A classe em destaque, com o rótulo do resultado (oficial, estimativa ou gerencial). */
function Classe({ titulo, resultado, valorRotulo }: { titulo: string; resultado: ResultadoCapacidade | null; valorRotulo: string }) {
  return (
    <div className="grid content-start gap-2 rounded-md border p-3">
      <p className="text-sm font-medium">{titulo}</p>
      {!resultado
        ? <p className="text-sm text-muted-foreground">Não disponível — importe a tela de CAPAG do Regularize.</p>
        : (
          <>
            <p className="flex items-baseline gap-3">
              <span className="font-serif text-4xl font-semibold" aria-label={`Classe ${resultado.classe ?? 'não calculada'}`}>
                {resultado.classe ?? '—'}
              </span>
              {resultado.valor != null && <span className="num text-sm text-muted-foreground">{valorRotulo} {fmt.moeda(resultado.valor)}</span>}
            </p>
            <SeloRotulo resultado={resultado} />
          </>
        )}
    </div>
  );
}

/**
 * Aba Capacidade: PGFN (CAPAG oficial × estimada), Paraná (recálculo × RTI e simulador) e análise gerencial. Tudo vem
 * pronto do backend (critérios, pontos, faixas, fundamento e origem); a tela só exibe e edita os parâmetros.
 */
export default function CapacidadePage() {
  const clienteId = useClienteId();
  const capacidade = useCapacidade(clienteId);
  if (capacidade.isPending) return <Skeleton className="h-96" />;
  if (!capacidade.data) {
    return <EstadoVazio icone={Gauge} titulo="Capacidade indisponível" descricao={mensagemErro(capacidade.error, 'Não foi possível calcular.')} />;
  }
  const c = capacidade.data;
  const est = metodologia(c, 'CAPAG_ESTIMADA');
  const pr = metodologia(c, 'PR');
  const ger = metodologia(c, 'GERENCIAL');
  const revisao = est.alertas.find((a) => a.startsWith('Revisão da CAPAG'));

  return (
    <Reveal className="grid gap-4">
      <Cartao aria-labelledby="titulo-pgfn" className="grid gap-3">
        <CartaoTitulo id="titulo-pgfn">PGFN — CAPAG</CartaoTitulo>
        {revisao && (
          <Alert className="border-warning motion-safe:animate-[destaque-alerta_1.4s_ease-out_3]" role="status">
            <AlertTriangle aria-hidden className="text-warning" />
            <AlertTitle>Revisão da CAPAG potencialmente vantajosa</AlertTitle>
            <AlertDescription>{revisao}</AlertDescription>
          </Alert>
        )}
        <div className="grid gap-3 md:grid-cols-2">
          <Classe titulo="CAPAG oficial (Regularize)" resultado={c.capagOficial} valorRotulo="capacidade" />
          <Classe titulo="CAPAG estimada (dois métodos)" resultado={est} valorRotulo="valor considerado" />
        </div>
        {c.capagOficial && <TabelaCriterios clienteId={clienteId} titulo="Critérios da CAPAG oficial" criterios={c.capagOficial.criterios} />}
        <TabelaCriterios clienteId={clienteId} titulo="Memória de cálculo da estimativa" criterios={est.criterios} />
        <ul className="grid gap-1 text-sm" aria-label="Créditos irrecuperáveis (art. 25)">
          {est.itens.map((i) => (
            <li key={i.id} className="flex flex-wrap justify-between gap-2">
              <span className="num">{i.id}</span>
              <span className={cn('text-muted-foreground', i.classe === 'IRRECUPERAVEL' && 'font-medium text-danger')}>
                {i.classe === 'IRRECUPERAVEL' ? `irrecuperável — ${i.observacao}` : i.classe === 'RECUPERAVEL'
                  ? 'recuperável (nenhum inciso do art. 25 se aplica)' : 'irrecuperabilidade não verificável — faltam dados'}
              </span>
            </li>
          ))}
        </ul>
        <Notas resultado={est} />
      </Cartao>

      <Cartao aria-labelledby="titulo-pr" className="grid gap-3">
        <CartaoTitulo id="titulo-pr">Paraná — PGE/SEFA</CartaoTitulo>
        <div className="grid gap-3 md:grid-cols-2">
          <Classe titulo="Classe do devedor (recálculo)" resultado={pr} valorRotulo="" />
          <div className="grid content-start gap-2 rounded-md border p-3">
            <p className="text-sm font-medium">Classificação oficial (RTI)</p>
            <span className="font-serif text-4xl font-semibold">{String(pr.parametros.oficial ?? '—')}</span>
            <SeloRotulo resultado={{ rotulo: 'OFICIAL', rotuloTexto: 'oficial (documento do órgão)' }} />
          </div>
        </div>
        {pr.valor != null && <p className="num text-sm">Soma dos índices: {fmt.inteiro(pr.valor)} (data do cálculo {fmt.data(String(pr.parametros.dataCalculo))})</p>}
        {pr.alertas.map((a) => <p key={a} className="text-sm text-warning">{a}</p>)}
        <TabelaCriterios clienteId={clienteId} titulo="Critérios do devedor" criterios={pr.criterios} abertoInicial />
        <table className="w-full text-sm" aria-label="CDAs do Paraná">
          <thead>
            <tr className="border-b text-left text-xs text-muted-foreground">
              <th scope="col" className="py-1 font-medium">CDA</th>
              <th scope="col" className="py-1 text-right font-medium">Valor</th>
              <th scope="col" className="py-1 text-right font-medium">Índice</th>
              <th scope="col" className="py-1 font-medium">Classe recalculada</th>
              <th scope="col" className="py-1 font-medium">Oficial (RTI)</th>
            </tr>
          </thead>
          <tbody>
            {pr.itens.map((i) => (
              <tr key={i.id} className="border-b last:border-0">
                <th scope="row" className="num py-1 text-left font-normal">{i.id}</th>
                <td className="num py-1 text-right">{fmt.moeda(Number(i.valor))}</td>
                <td className="num py-1 text-right">{i.indice ?? '—'}</td>
                <td className="py-1">{i.classe ?? 'não disponível'}{i.observacao && <span className="block text-xs text-muted-foreground">{i.observacao}</span>}</td>
                <td className="py-1">{i.classeOficial ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {pr.itens.length > 0 && <SimuladorPr clienteId={clienteId} atual={pr} />}
        <Notas resultado={pr} />
      </Cartao>

      <Cartao aria-labelledby="titulo-gerencial" className="grid gap-3">
        <CartaoTitulo id="titulo-gerencial">Análise gerencial</CartaoTitulo>
        <SeloRotulo resultado={ger} />
        <p className="flex items-baseline gap-2">
          <span className="text-sm text-muted-foreground">Parcela mensal sustentável</span>
          {ger.valor == null ? <span>não disponível</span>
            : <AnimatedNumber valor={ger.valor} formatar={fmt.moeda} className="text-xl font-semibold" />}
        </p>
        <TabelaCriterios clienteId={clienteId} titulo="Cálculo da parcela e do passivo" criterios={ger.criterios} />
        {c.indicadores.length > 0 && (
          <Grafico titulo="EBITDA × caixa operacional por exercício" tipo="barras" formato="moeda" categoria="exercicio"
                   series={[{ chave: 'ebitda', nome: 'EBITDA' }, { chave: 'fco', nome: 'Caixa operacional' }]}
                   dados={c.indicadores.map((i) => ({ exercicio: String(i.exercicio), ebitda: i.ebitda ?? null, fco: i.fco ?? null }))} />
        )}
        <ComposicaoDfc clienteId={clienteId} />
        <Notas resultado={ger} />
      </Cartao>

      <Parametros parametros={c.parametros} />
    </Reveal>
  );
}

function Notas({ resultado }: { resultado: ResultadoCapacidade }) {
  return (
    <details className="text-xs text-muted-foreground">
      <summary className="cursor-pointer">Notas e normas</summary>
      <ul className="mt-1 grid list-disc gap-1 pl-4">
        {[...resultado.notas, ...resultado.normas.map((n) => `Norma: ${n}`)].map((n) => <li key={n}>{n}</li>)}
      </ul>
    </details>
  );
}

const ATIVIDADES = [['OPERACIONAL', 'Operacional'], ['INVESTIMENTO', 'Investimento'], ['FINANCIAMENTO', 'Financiamento']] as const;

/** Composição da DFC direta por atividade em cada exercício com ECD. */
function ComposicaoDfc({ clienteId }: { clienteId: number }) {
  const anos = useDfcExercicios(clienteId).data ?? [];
  const dfcs = useQueries({ queries: anos.map((a) => ({
    queryKey: ['documentos', 'dfc', clienteId, a], queryFn: () => http.get<Dfc>(`/api/clientes/${clienteId}/dfc?exercicio=${a}`),
  })) });
  const dados = dfcs.flatMap((q) => (q.data ? [q.data] : [])).map((d) => ({
    exercicio: String(d.exercicio), ...Object.fromEntries(ATIVIDADES.map(([k]) => [k, d.atividades[k] ?? 0])),
  }));
  if (dados.length === 0) return null;
  return (
    <Grafico titulo="DFC pelo método direto — composição por atividade" tipo="empilhadas" formato="moeda" categoria="exercicio"
             series={ATIVIDADES.map(([chave, nome]) => ({ chave, nome }))} dados={dados} />
  );
}

/** Parâmetros das estimativas (ADMIN e CONSULTOR editam; a alteração é auditada e recalcula tudo). */
function Parametros({ parametros }: { parametros: ParametrosEscritorio }) {
  const { alterar } = usePermissoes();
  const salvar = useSalvarParametros();
  const [p, setP] = useState(parametros);
  const capag = (k: keyof ParametrosEscritorio['capag'], v: string) => setP((x) => ({ ...x, capag: { ...x.capag, [k]: k === 'metodoConsiderado' ? v : Number(v) } }));
  const campos: [keyof ParametrosEscritorio['capag'], string][] = [
    ['faixaA', 'Faixa A (CAPAG ÷ dívida ≥)'], ['faixaB', 'Faixa B (≥)'], ['faixaC', 'Faixa C (≥)'], ['exercicios', 'Exercícios nas médias'],
    ['realizacaoDisponivel', 'Realização do disponível (%)'], ['realizacaoCreditos', 'Realização dos créditos (%)'],
    ['realizacaoEstoques', 'Realização dos estoques (%)'], ['realizacaoImobilizado', 'Realização do imobilizado (%)'],
  ];
  return (
    <Cartao aria-labelledby="titulo-parametros" className="grid gap-3">
      <CartaoTitulo id="titulo-parametros">Parâmetros das estimativas</CartaoTitulo>
      <p className="text-sm text-muted-foreground">
        O que a norma não fixa (faixas A–D, GRF, PLR, método que prevalece, parcela sustentável). Valem para todos os clientes do escritório.
        {parametros.atualizadoPor && <> Última alteração: {parametros.atualizadoPor}, {fmt.dataHora(parametros.atualizadoEm)}.</>}
      </p>
      <form className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4" onSubmit={(e) => {
        e.preventDefault();
        salvar.mutate(p, { onSuccess: () => toast.success('Parâmetros salvos; a capacidade foi recalculada.'),
          onError: (err) => toast.error(mensagemErro(err, 'Não foi possível salvar.')) });
      }}>
        {campos.map(([k, rotulo]) => (
          <label key={k} className="grid gap-1 text-sm">
            <span>{rotulo}</span>
            <input type="number" step="any" value={String(p.capag[k])} disabled={!alterar} onChange={(e) => capag(k, e.target.value)}
                   className="num h-9 rounded-md border bg-background px-2" />
          </label>
        ))}
        <label className="grid gap-1 text-sm">
          <span>Método considerado na classe</span>
          <select value={p.capag.metodoConsiderado} disabled={!alterar} onChange={(e) => capag('metodoConsiderado', e.target.value)}
                  className="h-9 rounded-md border bg-background px-2">
            <option value="MENOR">O menor dos dois</option>
            <option value="MAIOR">O maior dos dois</option>
            <option value="RESULTADO_OPERACIONAL">Resultado operacional</option>
            <option value="FLUXO_DE_CAIXA">Fluxo de caixa</option>
          </select>
        </label>
        <label className="grid gap-1 text-sm">
          <span>Parcela sustentável (% do caixa livre)</span>
          <input type="number" step="any" value={p.gerencial.percentualFcoLivre} disabled={!alterar}
                 onChange={(e) => setP((x) => ({ ...x, gerencial: { percentualFcoLivre: Number(e.target.value) } }))}
                 className="num h-9 rounded-md border bg-background px-2" />
        </label>
        {alterar && <div className="flex items-end"><Button type="submit" disabled={salvar.isPending}>Salvar parâmetros</Button></div>}
      </form>
    </Cartao>
  );
}
