import { cn } from '@/lib/utils';

/** Grupo de botões com uma escolha (radiogroup acessível). */
export function Alternador<T extends string>({ rotulo, valor, opcoes, aoMudar }: {
  rotulo: string; valor: T; opcoes: [T, string][]; aoMudar: (v: T) => void;
}) {
  return (
    <div role="radiogroup" aria-label={rotulo} className="inline-flex rounded-md border bg-surface p-0.5">
      {opcoes.map(([v, r]) => (
        <button key={v} type="button" role="radio" aria-checked={valor === v} onClick={() => aoMudar(v)}
                className={cn('rounded-sm px-3 py-1 text-sm transition-colors duration-(--dur-fast)',
                  valor === v ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-accent hover:text-foreground')}>
          {r}
        </button>
      ))}
    </div>
  );
}
