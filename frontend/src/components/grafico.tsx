import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useMovimentoReduzido } from '@/components/motion/use-movimento-reduzido';
import * as fmt from '@/lib/formatadores';

export type SerieGrafico = { chave: string; nome: string };
export type LinhaGrafico = Record<string, string | number | null>;

type Props = {
  titulo: string;
  tipo: 'barras' | 'empilhadas' | 'linha';
  dados: LinhaGrafico[];
  /** Campo de cada linha com o rótulo do eixo x (competência, exercício). */
  categoria: string;
  series: SerieGrafico[];
  formato: 'moeda' | 'percentual';
  altura?: number;
};

/** Paleta categórica do design system (--chart-1..6), em ordem fixa; nunca uma cor gerada. */
const COR = (i: number) => `var(--chart-${(i % 6) + 1})`;

/** Hachura por série (a 1ª é sólida): identidade sem depender só da cor, como no PDF (GraficoSvg). */
const MARCAS = ['', 'M0,6 L6,0', 'M0,0 L6,6', '', 'M0,3 L6,3', 'M3,0 L3,6'];

/**
 * Gráfico das telas (Recharts) com a paleta dos tokens: tooltip e eixo formatados, legenda, padrões e uma tabela
 * para leitor de tela. Animação de entrada só sem a preferência de movimento reduzido.
 * Os gráficos dos relatórios (PDF) são outro uso: SVG gerado no backend.
 */
export function Grafico({ titulo, tipo, dados, categoria, series, formato, altura = 240 }: Props) {
  const reduzido = useMovimentoReduzido();
  const formatar = (v: unknown) => (typeof v === 'number' ? (formato === 'moeda' ? fmt.moeda(v) : fmt.percentual(v)) : '—');
  const eixo = (v: number) => (formato === 'moeda' ? compacto(v) : fmt.percentual(v));
  const comuns = {
    data: dados,
    margin: { top: 8, right: 8, bottom: 0, left: 8 },
    // a leitura acessível é a tabela abaixo; o SVG fica fora do foco e da árvore de acessibilidade
    accessibilityLayer: false,
  };
  const grade = <CartesianGrid stroke="var(--border)" vertical={false} />;
  const eixos = (
    <>
      <XAxis dataKey={categoria} tick={{ fill: 'var(--muted-foreground)', fontSize: 12 }} stroke="var(--border)" />
      <YAxis tickFormatter={eixo} tick={{ fill: 'var(--muted-foreground)', fontSize: 12 }} stroke="var(--border)" width={72} />
      <Tooltip formatter={(v, nome) => [formatar(v), nome]} cursor={{ fill: 'var(--accent)' }}
               contentStyle={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 4, color: 'var(--foreground)' }} />
      {series.length > 1 && <Legend wrapperStyle={{ color: 'var(--foreground)', fontSize: 12 }} />}
    </>
  );

  return (
    <figure aria-label={titulo} data-animado={!reduzido} className="grid gap-1">
      <div aria-hidden style={{ height: altura }}>
        <ResponsiveContainer width="100%" height="100%" initialDimension={{ width: 640, height: altura }}>
          {tipo === 'linha' ? (
            <LineChart {...comuns}>
              {grade}{eixos}
              {series.map((s, i) => (
                <Line key={s.chave} dataKey={s.chave} name={s.nome} stroke={COR(i)} strokeWidth={2} dot={{ r: 4, fill: COR(i) }}
                      connectNulls={false} isAnimationActive={!reduzido} />
              ))}
            </LineChart>
          ) : (
            <BarChart {...comuns}>
              <defs>
                {series.map((s, i) => (
                  <pattern key={s.chave} id={`padrao-${i}`} width="6" height="6" patternUnits="userSpaceOnUse">
                    <rect width="6" height="6" style={{ fill: COR(i) }} />
                    {MARCAS[i % 6] && <path d={MARCAS[i % 6]} stroke="var(--surface)" strokeWidth="1.2" />}
                    {i % 6 === 3 && <circle cx="3" cy="3" r="1.1" style={{ fill: 'var(--surface)' }} />}
                  </pattern>
                ))}
              </defs>
              {grade}{eixos}
              {series.map((s, i) => (
                <Bar key={s.chave} dataKey={s.chave} name={s.nome} fill={`url(#padrao-${i})`} stackId={tipo === 'empilhadas' ? 'pilha' : undefined}
                     maxBarSize={28} radius={tipo === 'empilhadas' ? 0 : [4, 4, 0, 0]} isAnimationActive={!reduzido} />
              ))}
            </BarChart>
          )}
        </ResponsiveContainer>
      </div>
      <figcaption className="sr-only">{titulo}</figcaption>
      <table className="sr-only">
        <caption>{titulo}</caption>
        <thead><tr><th scope="col">{categoria}</th>{series.map((s) => <th key={s.chave} scope="col">{s.nome}</th>)}</tr></thead>
        <tbody>
          {dados.map((d) => (
            <tr key={String(d[categoria])}>
              <th scope="row">{d[categoria]}</th>
              {series.map((s) => <td key={s.chave}>{formatar(d[s.chave])}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}

/** Eixo de moeda curto: 1.234.567 -> "R$ 1,2 mi". */
function compacto(v: number): string {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', notation: 'compact', maximumFractionDigits: 1 }).format(v);
}
