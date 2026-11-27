import type { ReactNode } from 'react';

/** Título da página: único lugar (com CartaoTitulo) que usa a serifa. */
export function CabecalhoPagina({ titulo, subtitulo, acoes }: { titulo: ReactNode; subtitulo?: ReactNode; acoes?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-3">
      <div className="min-w-0">
        <h1 className="truncate font-serif text-2xl font-semibold tracking-tight">{titulo}</h1>
        {subtitulo && <p className="mt-0.5 text-muted-foreground">{subtitulo}</p>}
      </div>
      {acoes && <div className="flex flex-wrap gap-2">{acoes}</div>}
    </div>
  );
}
