import { useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, BookOpen } from 'lucide-react';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Alternador } from '@/components/alternador';
import { Skeleton } from '@/components/ui/skeleton';
import { Cartao } from '@/components/cartao';
import { EstadoVazio } from '@/components/estado-vazio';
import { Reveal } from '@/components/motion';
import * as fmt from '@/lib/formatadores';
import { cn } from '@/lib/utils';
import { useClienteId } from '@/features/clientes/rota';
import { useComparativo, useEscrituracoes, type OrigemDemonstracao, type TipoDemonstracao } from '../api';
import { TabelaDemonstracao } from '../components/tabela-demonstracao';

/** Aba Demonstrações: Balanço e DRE (referencial ou publicada), exercícios lado a lado, AH/AV e origem de cada valor. */
export default function DemonstracoesPage() {
  const clienteId = useClienteId();
  const escrituracoes = useEscrituracoes(clienteId);
  const anosDisponiveis = [...new Set((escrituracoes.data ?? []).filter((e) => e.situacao === 'VIGENTE')
    .map((e) => Number(e.dataFim.slice(0, 4))))].sort();
  const [tipo, setTipo] = useState<TipoDemonstracao>('BP');
  const [origem, setOrigem] = useState<OrigemDemonstracao>('REFERENCIAL');
  const [escolhidos, setEscolhidos] = useState<number[]>();
  const [mostrarAh, setMostrarAh] = useState(true);
  const [mostrarAv, setMostrarAv] = useState(false);
  const exercicios = escolhidos ?? anosDisponiveis.slice(-3); // padrão: os três mais recentes
  const comparativo = useComparativo(clienteId, tipo, origem, exercicios);

  if (escrituracoes.isPending) return <Skeleton className="h-96" />;
  if (anosDisponiveis.length === 0) {
    return (
      <Reveal>
        <EstadoVazio icone={BookOpen} titulo="Nenhuma demonstração ainda"
                     descricao={<>Importe a ECD (SPED Contábil) ou um balancete em planilha na aba{' '}
                       <Link to={`/clientes/${clienteId}/importacoes`} className="text-primary underline">Importações</Link>.</>} />
      </Reveal>
    );
  }

  const alternarAno = (ano: number) => setEscolhidos(
    (exercicios.includes(ano) ? exercicios.filter((a) => a !== ano) : [...exercicios, ano]).sort());
  const diferencas = (comparativo.data?.conferencia ?? []).filter((c) => Math.abs(c.diferenca) >= 0.01);

  return (
    <Reveal className="grid gap-4">
      <Cartao className="flex flex-wrap items-center gap-x-5 gap-y-3">
        <Alternador rotulo="Demonstração" valor={tipo} aoMudar={setTipo} opcoes={[['BP', 'Balanço'], ['DRE', 'DRE']]} />
        <Alternador rotulo="Origem" valor={origem} aoMudar={setOrigem} opcoes={[['REFERENCIAL', 'Referencial'], ['PUBLICADA', 'Publicada']]} />
        <fieldset className="flex flex-wrap items-center gap-2">
          <legend className="sr-only">Exercícios</legend>
          <span className="text-muted-foreground" aria-hidden>Exercícios</span>
          {anosDisponiveis.map((ano) => (
            <label key={ano} className={cn('num cursor-pointer rounded-full border px-2.5 py-0.5 text-sm',
              exercicios.includes(ano) ? 'border-primary bg-accent text-foreground' : 'text-muted-foreground')}>
              <input type="checkbox" className="sr-only" checked={exercicios.includes(ano)} onChange={() => alternarAno(ano)} />
              {ano}
            </label>
          ))}
        </fieldset>
        <label className="flex items-center gap-1.5"><input type="checkbox" className="size-4 accent-primary" checked={mostrarAh}
                                                           onChange={(e) => setMostrarAh(e.target.checked)} /> AH%</label>
        <label className="flex items-center gap-1.5"><input type="checkbox" className="size-4 accent-primary" checked={mostrarAv}
                                                           onChange={(e) => setMostrarAv(e.target.checked)} /> AV%</label>
      </Cartao>

      {diferencas.length > 0 && (
        <Alert>
          <AlertTriangle aria-hidden className="text-warning" />
          <AlertTitle>Referencial e publicada não batem</AlertTitle>
          <AlertDescription>
            <ul className="num">
              {diferencas.map((d) => (
                <li key={`${d.exercicio}-${d.item}`}>
                  {d.exercicio} · {d.item}: referencial {fmt.saldo(d.referencial)}, publicada {fmt.saldo(d.publicada)}, diferença {fmt.saldo(d.diferenca)}
                </li>
              ))}
            </ul>
            Confira as contas sem mapeamento referencial nas inconsistências do arquivo.
          </AlertDescription>
        </Alert>
      )}

      {comparativo.isPending
        ? <Skeleton className="h-96" />
        : !comparativo.data || comparativo.data.linhas.length === 0
          ? <EstadoVazio icone={BookOpen} titulo={`Sem ${tipo === 'BP' ? 'balanço' : 'DRE'} ${origem === 'PUBLICADA' ? 'publicado(a)' : 'referencial'} nesses exercícios`}
                         descricao={origem === 'PUBLICADA'
                           ? 'A demonstração publicada vem dos registros J100/J150 da ECD. Balancete em planilha só tem o referencial.'
                           : 'O referencial depende do plano referencial (COD_PLAN_REF) da ECD ou do de-para do balancete.'} />
          : <TabelaDemonstracao comparativo={comparativo.data} clienteId={clienteId} mostrarAh={mostrarAh} mostrarAv={mostrarAv} />}
    </Reveal>
  );
}
