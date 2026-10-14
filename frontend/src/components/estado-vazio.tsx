import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';

/** Estado vazio desenhado: o que falta e como resolver. */
export function EstadoVazio({ icone: Icone, titulo, descricao, acao }: {
  icone: LucideIcon;
  titulo: string;
  descricao?: ReactNode;
  acao?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-lg border border-dashed bg-surface px-6 py-10 text-center">
      <span className="grid size-10 place-items-center rounded-full bg-muted text-muted-foreground">
        <Icone className="size-5" aria-hidden />
      </span>
      <p className="font-medium">{titulo}</p>
      {descricao && <p className="max-w-md text-muted-foreground">{descricao}</p>}
      {acao && <div className="mt-2">{acao}</div>}
    </div>
  );
}
