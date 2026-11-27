import type { ComponentProps, ReactNode } from 'react';
import { cn } from '@/lib/utils';

/** Superfície de conteúdo (card). */
export function Cartao({ className, ...props }: ComponentProps<'section'>) {
  return <section className={cn('rounded-lg border bg-surface p-4 shadow-sm', className)} {...props} />;
}

/** Título de seção de card: serifado (regra da direção A + títulos da C). */
export function CartaoTitulo({ children, acoes, id }: { children: ReactNode; acoes?: ReactNode; id?: string }) {
  return (
    <div className="mb-3 flex items-center justify-between gap-2">
      <h2 id={id} className="font-serif text-lg font-semibold tracking-tight">{children}</h2>
      {acoes}
    </div>
  );
}
