import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ScrollText } from 'lucide-react';
import { DataTable, type ColumnDef } from '@/components/data-table';
import { EstadoVazio } from '@/components/estado-vazio';
import { Reveal } from '@/components/motion';
import { Paginacao } from '@/components/paginacao';
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
    queryFn: () => http.get<PaginaAuditoria>(`/api/auditoria?pagina=${pagina}&tamanho=${TAMANHO}`),
    placeholderData: keepPreviousData,
  });

  return (
    <Reveal className="grid gap-5">
      <CabecalhoPagina titulo="Auditoria" subtitulo="Acessos e alterações feitas no escritório" />
      <DataTable rotulo="Eventos de auditoria" colunas={colunas} dados={eventos.data?.content} carregando={eventos.isPending}
                 filtro="Filtrar nesta página" tamanhoPagina={TAMANHO}
                 vazio={<EstadoVazio icone={ScrollText} titulo="Nenhum evento registrado" />} />
      <Paginacao meta={eventos.data?.page} aoMudar={setPagina} rotulo="Páginas da auditoria" unidade="eventos" />
    </Reveal>
  );
}
