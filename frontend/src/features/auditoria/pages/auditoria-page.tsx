import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight, ScrollText } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { DataTable, type ColumnDef } from '@/components/data-table';
import { EstadoVazio } from '@/components/estado-vazio';
import { Reveal } from '@/components/motion';
import { CabecalhoPagina } from '@/components/layout/cabecalho-pagina';
import type { EventoAuditoria, PaginaAuditoria } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { http } from '@/lib/http';
import { ACOES_AUDITORIA } from '@/lib/rotulos';

const TAMANHO = 50;

const colunas: ColumnDef<EventoAuditoria>[] = [
  { accessorKey: 'dataHora', header: 'Data e hora', cell: ({ getValue }) => fmt.dataHora(getValue<string>()), meta: { classe: 'whitespace-nowrap' } },
  { accessorKey: 'acao', header: 'Ação', cell: ({ getValue }) => ACOES_AUDITORIA[getValue<EventoAuditoria['acao']>()] },
  { accessorKey: 'usuarioId', header: 'Usuário', meta: { classe: 'text-right whitespace-nowrap' } },
  { id: 'recurso', header: 'Recurso', accessorFn: (e) => (e.recursoTipo ? `${e.recursoTipo.toLowerCase()} #${e.recursoId ?? '—'}` : '—') },
  { id: 'resultado', header: 'Resultado', accessorFn: (e) => String(e.detalhes?.resultado ?? '—'), meta: { classe: 'whitespace-nowrap' } },
  { accessorKey: 'ip', header: 'IP', meta: { classe: 'whitespace-nowrap' } },
];

/** Trilha de auditoria do escritório (só ADMIN). Paginação no servidor; o filtro atua na página carregada. */
export default function AuditoriaPage() {
  const [pagina, setPagina] = useState(0);
  const eventos = useQuery({
    queryKey: ['auditoria', pagina],
    queryFn: () => http.get<PaginaAuditoria>(`/api/auditoria?page=${pagina}&size=${TAMANHO}`),
    placeholderData: keepPreviousData,
  });
  const meta = eventos.data?.page;

  return (
    <Reveal className="grid gap-5">
      <CabecalhoPagina titulo="Auditoria" subtitulo="Acessos e alterações feitas no escritório" />
      <DataTable rotulo="Eventos de auditoria" colunas={colunas} dados={eventos.data?.content} carregando={eventos.isPending}
                 filtro="Filtrar nesta página" tamanhoPagina={TAMANHO}
                 vazio={<EstadoVazio icone={ScrollText} titulo="Nenhum evento registrado" />} />
      {meta && meta.totalPages > 1 && (
        <nav className="flex items-center justify-end gap-2 text-muted-foreground" aria-label="Páginas da auditoria">
          <span className="num">Página {meta.number + 1} de {meta.totalPages} · {fmt.inteiro(meta.totalElements)} eventos</span>
          <Button variant="outline" size="icon" aria-label="Página anterior" disabled={pagina === 0} onClick={() => setPagina((p) => p - 1)}><ChevronLeft aria-hidden /></Button>
          <Button variant="outline" size="icon" aria-label="Próxima página" disabled={pagina + 1 >= meta.totalPages} onClick={() => setPagina((p) => p + 1)}><ChevronRight aria-hidden /></Button>
        </nav>
      )}
    </Reveal>
  );
}
