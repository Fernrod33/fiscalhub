import type { ReactNode } from 'react';
import { Landmark } from 'lucide-react';
import { Reveal } from '@/components/motion';

/** Moldura das telas de autenticação. */
export function LayoutAuth({ titulo, subtitulo, children, rodape }: {
  titulo: string;
  subtitulo?: string;
  children: ReactNode;
  rodape?: ReactNode;
}) {
  return (
    <main className="grid min-h-svh place-items-center bg-background p-4">
      <Reveal className="w-full max-w-sm">
        <div className="mb-6 flex items-center justify-center gap-2 text-primary">
          <Landmark className="size-6" aria-hidden />
          <span className="text-lg font-semibold tracking-tight text-foreground">FiscalHub</span>
        </div>
        <div className="rounded-lg border bg-surface p-6 shadow-sm">
          <h1 className="font-serif text-2xl font-semibold tracking-tight">{titulo}</h1>
          {subtitulo && <p className="mt-1 text-muted-foreground">{subtitulo}</p>}
          <div className="mt-5">{children}</div>
        </div>
        {rodape && <p className="mt-4 text-center text-muted-foreground">{rodape}</p>}
      </Reveal>
    </main>
  );
}
