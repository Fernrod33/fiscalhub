import { useState, type ReactNode } from 'react';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { cn } from '@/lib/utils';

/**
 * Um número que abre, num popover, de onde ele veio (arquivo, registro e linha). O conteúdo recebe se está aberto,
 * para carregar a origem só quando necessário.
 */
export function ValorOrigem({ rotulo, texto, destaque, children }: {
  rotulo: string; texto: string; destaque?: boolean; children: (aberto: boolean) => ReactNode;
}) {
  const [aberto, setAberto] = useState(false);
  return (
    <Popover open={aberto} onOpenChange={setAberto}>
      <PopoverTrigger asChild>
        <button type="button" aria-label={rotulo}
                className={cn('num w-full whitespace-nowrap rounded-sm px-1 text-right underline-offset-4 hover:bg-accent hover:underline focus-visible:outline-2',
                  destaque && 'font-semibold')}>
          {texto}
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-96 max-w-[calc(100vw-2rem)]" align="end">{children(aberto)}</PopoverContent>
    </Popover>
  );
}
