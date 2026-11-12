import { Fragment, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Skeleton } from '@/components/ui/skeleton';

/** **negrito** dentro da linha. */
function inline(texto: string): ReactNode {
  return texto.split(/(\*\*[^*]+\*\*)/).map((p, i) => (p.startsWith('**') ? <strong key={i}>{p.slice(2, -2)}</strong> : <Fragment key={i}>{p}</Fragment>));
}

/**
 * Markdown simples dos textos legais (public/legal): títulos, aviso (>), listas, tabelas e parágrafos. Nenhum HTML do
 * arquivo entra na página: tudo vira elemento React.
 */
export function TextoLegal({ arquivo }: { arquivo: string }) {
  const texto = useQuery({ queryKey: ['legal', arquivo], queryFn: () => fetch(`/legal/${arquivo}`).then((r) => r.text()), staleTime: Infinity });
  if (!texto.data) return <Skeleton className="h-64" />;
  return (
    <div className="grid gap-3 text-sm leading-relaxed">
      {texto.data.trim().split(/\n\s*\n/).map((b, i) => {
        const linhas = b.split('\n');
        if (b.startsWith('# ')) return <h2 key={i} className="font-serif text-xl font-semibold">{b.slice(2)}</h2>;
        if (b.startsWith('## ')) return <h3 key={i} className="mt-2 font-semibold">{b.slice(3)}</h3>;
        if (b.startsWith('> ')) {
          return <p key={i} role="note" className="rounded-md border border-warning bg-warning-bg p-3 text-warning">{inline(linhas.map((l) => l.replace(/^>\s?/, '')).join(' '))}</p>;
        }
        if (b.startsWith('- ')) {
          return <ul key={i} className="ml-5 list-disc">{b.split(/\n(?=- )/).map((l, j) => <li key={j}>{inline(l.slice(2).replace(/\n\s*/g, ' '))}</li>)}</ul>;
        }
        if (b.startsWith('|')) {
          const [cab, , ...corpo] = linhas.map((l) => l.split('|').slice(1, -1).map((c) => c.trim()));
          return (
            <table key={i} className="w-full border-collapse text-left">
              <thead><tr>{cab.map((c, j) => <th key={j} className="border-b p-1.5 font-medium">{c}</th>)}</tr></thead>
              <tbody>{corpo.map((l, j) => <tr key={j}>{l.map((c, k) => <td key={k} className="border-b p-1.5">{inline(c)}</td>)}</tr>)}</tbody>
            </table>
          );
        }
        return <p key={i}>{inline(linhas.join(' '))}</p>;
      })}
    </div>
  );
}
