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
import { Selo } from '@/components/selo';
import { ACOES_AUDITORIA, RECURSOS_AUDITORIA, RESULTADOS_AUDITORIA } from '@/lib/rotulos';

const TAMANHO = 50;

const colunas: ColumnDef<EventoAuditoria>[] = [
  { accessorKey: 'dataHora', header: 'Data e hora', cell: ({ getValue }) => fmt.dataHora(getValue<string>()), meta: { classe: 'whitespace-nowrap' } },
  { accessorKey: 'acao', header: 'Ação', cell: ({ getValue }) => ACOES_AUDITORIA[getValue<EventoAuditoria['acao']>()] },
  // login que falhou com e-mail desconhecido não tem usuário: mostra o e-mail tentado
  { id: 'usuario', header: 'Usuário', accessorFn: (e) => e.usuarioNome ?? (e.detalhes?.email ? `${e.detalhes.email} (tentativa)` : '—') },
  { id: 'recurso', header: 'Recurso', accessorFn: (e) => (e.recursoTipo ? `${RECURSOS_AUDITORIA[e.recursoTipo] ?? e.recursoTipo} #${e.recursoId ?? '—'}` : '—'),
    meta: { classe: 'whitespace-nowrap' } },
  { accessorKey: 'resultado', header: 'Resultado', meta: { classe: 'whitespace-nowrap' },
    cell: ({ getValue }) => {
      const r = getValue<EventoAuditoria['resultado']>();
      return r ? <Selo tom={RESULTADOS_AUDITORIA[r].tom}>{RESULTADOS_AUDITORIA[r].rotulo}</Selo> : '—';
    } },
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
