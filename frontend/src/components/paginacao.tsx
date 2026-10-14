import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '@/components/ui/button';
import * as fmt from '@/lib/formatadores';

type MetaPagina = { number: number; totalPages: number; totalElements: number };

/** Navegação de páginas para listas paginadas no servidor (PagedModel do Spring). Some com uma página só. */
export function Paginacao({ meta, aoMudar, rotulo, unidade }: {
  meta?: MetaPagina;
  aoMudar: (pagina: number) => void;
  rotulo: string;
  unidade: string;
}) {
  if (!meta || meta.totalPages <= 1) return null;
  return (
    <nav className="flex items-center justify-end gap-2 text-muted-foreground" aria-label={rotulo}>
      <span className="num">Página {meta.number + 1} de {meta.totalPages} · {fmt.inteiro(meta.totalElements)} {unidade}</span>
      <Button variant="outline" size="icon" aria-label="Página anterior" disabled={meta.number === 0} onClick={() => aoMudar(meta.number - 1)}><ChevronLeft aria-hidden /></Button>
      <Button variant="outline" size="icon" aria-label="Próxima página" disabled={meta.number + 1 >= meta.totalPages} onClick={() => aoMudar(meta.number + 1)}><ChevronRight aria-hidden /></Button>
    </nav>
  );
}
