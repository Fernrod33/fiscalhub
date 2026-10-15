import { useState, type CSSProperties } from 'react';
import { AlertTriangle, ChevronRight } from 'lucide-react';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { Collapse } from '@/components/motion';
import { Selo } from '@/components/selo';
import type { Comparativo, LinhaComparativa } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { cn } from '@/lib/utils';
import { OrigemValor } from './origem-valor';

type No = { linha: LinhaComparativa; filhos: No[]; profundidade: number };

/** Hierarquia pelo código superior; linha cujo superior não está na demonstração vira raiz. */
function arvore(linhas: LinhaComparativa[]): No[] {
  const porCodigo = new Map(linhas.map((l) => [l.codigo, { linha: l, filhos: [] as No[], profundidade: 0 }]));
  const raizes: No[] = [];
  for (const no of porCodigo.values()) {
    const pai = no.linha.codigoSuperior ? porCodigo.get(no.linha.codigoSuperior) : undefined;
    if (pai) pai.filhos.push(no);
    else raizes.push(no);
  }
  const fixar = (nos: No[], p: number) => nos.forEach((n) => { n.profundidade = p; fixar(n.filhos, p + 1); });
  fixar(raizes, 0);
  return raizes;
}

const ITENS_TOPO: Record<string, (l: LinhaComparativa) => boolean> = {
  Ativo: (l) => l.grupo === 'A',
  'Passivo + PL': (l) => l.grupo === 'P',
  'Resultado do exercício': () => true,
};

/** O que todas as linhas compartilham (colunas, estado de recolhimento, opções). */
type Contexto = {
  anos: number[];
  grade: CSSProperties;
  recolhidos: Set<string>;
  alternar: (codigo: string) => void;
  sinal: (l: LinhaComparativa, ano: number, raiz: boolean) => Comparativo['conferencia'][number] | undefined;
  mostrarAh: boolean;
  mostrarAv: boolean;
  clienteId: number;
};

function Linha({ no, ctx }: { no: No; ctx: Contexto }) {
const { anos, grade, recolhidos, alternar, sinal, mostrarAh, mostrarAv, clienteId } = ctx;
  const { linha: l, filhos, profundidade } = no;
  const aberto = !recolhidos.has(l.codigo);
  const destaque = l.totalizadora || profundidade === 0;
  const idFilhos = `filhos-${l.codigo.replace(/\W/g, '_')}`;
  return (
    <div role="none">
      <div role="row" className={cn('grid items-center border-t py-1', profundidade === 0 && 'bg-muted/50')} style={grade}>
        <div role="rowheader" className={cn('flex min-w-0 items-center gap-1 pr-2', destaque && 'font-semibold')}
             style={{ paddingLeft: `${profundidade * 1.25 + 0.25}rem` }}>
          {filhos.length > 0
            ? <button type="button" onClick={() => alternar(l.codigo)} aria-expanded={aberto} aria-controls={idFilhos}
                      aria-label={`${aberto ? 'Recolher' : 'Expandir'} ${l.descricao}`}
                      className="grid size-5 shrink-0 place-items-center rounded-sm hover:bg-accent">
                <ChevronRight className={cn('size-4 transition-transform duration-(--dur-fast)', aberto && 'rotate-90')} aria-hidden />
              </button>
            : <span className="size-5 shrink-0" aria-hidden />}
          <span className="truncate" title={`${l.codigo} · ${l.descricao}`}>{l.descricao}</span>
          {l.derivada && <Selo tom="info">derivada</Selo>}
        </div>
        {anos.map((ano, i) => {
          const v = l.valores[ano];
          const diferenca = sinal(l, ano, profundidade === 0);
          return [
            <div role="cell" key={`v${ano}`} className="flex items-center justify-end gap-1">
              {diferenca && (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <AlertTriangle className="size-4 shrink-0 text-warning" aria-label={`Difere da publicada em ${fmt.saldo(diferenca.diferenca)}`} />
                  </TooltipTrigger>
                  <TooltipContent>Referencial difere da publicada em {fmt.saldo(diferenca.diferenca)}</TooltipContent>
                </Tooltip>
              )}
              {v ? <OrigemValor clienteId={clienteId} linhaId={v.linhaId} valor={v.valor} descricao={l.descricao} exercicio={ano} destaque={destaque} />
                : <span className="num px-1 text-muted-foreground">—</span>}
            </div>,
            mostrarAv && <div role="cell" key={`av${ano}`} className="num px-1 text-right text-muted-foreground">{v?.av == null ? '—' : fmt.percentual(v.av)}</div>,
            mostrarAh && i > 0 && <div role="cell" key={`ah${ano}`} className="num px-1 text-right text-muted-foreground">{v?.ah == null ? '—' : fmt.percentual(v.ah)}</div>,
          ];
        })}
      </div>
      {filhos.length > 0 && (
        <Collapse aberto={aberto} id={idFilhos}>
          <div role="none">{filhos.map((f) => <Linha key={f.linha.codigo} no={f} ctx={ctx} />)}</div>
        </Collapse>
      )}
    </div>
  );
}

/**
 * Balanço ou DRE (um componente para os dois): exercícios lado a lado, AH% e AV% opcionais, grupos recolhíveis e
 * cada valor abre a origem. Valores na convenção do sistema (devedor +, credor −), mostrados com D/C.
 */
export function TabelaDemonstracao({ comparativo, clienteId, mostrarAh, mostrarAv }: {
  comparativo: Comparativo; clienteId: number; mostrarAh: boolean; mostrarAv: boolean;
}) {
  const anos = comparativo.exercicios.map((e) => e.exercicio);
  const raizes = arvore(comparativo.linhas);
  const [recolhidos, setRecolhidos] = useState<Set<string>>(() => new Set(
    comparativo.linhas.filter((l) => (l.nivel ?? 1) >= 3).map((l) => l.codigo))); // mostra até o 3º nível
  const alternar = (codigo: string) => setRecolhidos((r) => {
    const n = new Set(r);
    if (n.has(codigo)) n.delete(codigo); else n.add(codigo);
    return n;
  });

  // conferência referencial x publicada: diferenças sinalizadas nas linhas de topo
  const diferencas = comparativo.conferencia.filter((c) => Math.abs(c.diferenca) >= 0.01);
  const sinal = (l: LinhaComparativa, ano: number, raiz: boolean) => (raiz
    ? diferencas.find((d) => d.exercicio === ano && ITENS_TOPO[d.item]?.(l)) : undefined);

  const colunasPorAno = (i: number) => 1 + (mostrarAv ? 1 : 0) + (mostrarAh && i > 0 ? 1 : 0);
  const grade: CSSProperties = {
    gridTemplateColumns: `minmax(16rem, 1fr) ${anos.map((_, i) => Array(colunasPorAno(i)).fill('minmax(7rem, auto)').join(' ')).join(' ')}`,
  };

  const ctx: Contexto = { anos, grade, recolhidos, alternar, sinal, mostrarAh, mostrarAv, clienteId };
  return (
    <div className="overflow-x-auto rounded-md border">
      <div role="table" aria-label={comparativo.tipo === 'BP' ? 'Balanço patrimonial' : 'Demonstração do resultado'} className="min-w-fit">
        <div role="rowgroup" className="bg-muted text-xs uppercase text-muted-foreground">
          <div role="row" className="grid py-2" style={grade}>
            <div role="columnheader" className="px-2">Conta</div>
            {anos.map((ano, i) => [
              <div role="columnheader" key={`h${ano}`} className="num px-1 text-right">{ano}</div>,
              mostrarAv && <div role="columnheader" key={`hav${ano}`} className="px-1 text-right"><abbr title="Análise vertical">AV%</abbr></div>,
              mostrarAh && i > 0 && <div role="columnheader" key={`hah${ano}`} className="px-1 text-right"><abbr title={`Análise horizontal sobre ${anos[i - 1]}`}>AH%</abbr></div>,
            ])}
          </div>
        </div>
        {/* um só corpo: os níveis da hierarquia são agrupamentos visuais (role none), não rowgroups aninhados */}
        <div role="rowgroup">{raizes.map((r) => <Linha key={r.linha.codigo} no={r} ctx={ctx} />)}</div>
      </div>
    </div>
  );
}
