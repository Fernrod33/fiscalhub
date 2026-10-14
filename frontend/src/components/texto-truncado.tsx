import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';

/** Texto longo (ex.: nome de arquivo) truncado com reticências; o texto completo aparece no tooltip (mouse e teclado). */
export function TextoTruncado({ texto, className }: { texto: string; className?: string }) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button type="button" className={cn('block max-w-72 cursor-default truncate rounded-sm text-left focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none', className)}>
          {texto}
        </button>
      </TooltipTrigger>
      <TooltipContent>{texto}</TooltipContent>
    </Tooltip>
  );
}
