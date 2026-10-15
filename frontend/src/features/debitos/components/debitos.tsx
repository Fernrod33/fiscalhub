import { useQuery } from '@tanstack/react-query';
import { Landmark } from 'lucide-react';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { DataTable, type ColumnDef } from '@/components/data-table';
import { EstadoVazio } from '@/components/estado-vazio';
import type { Debito } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { http } from '@/lib/http';
import { ORGAO_CREDOR, SITUACAO_DEBITO } from '@/lib/rotulos';
import { useClienteId } from '@/features/clientes/rota';

const useDebitos = (clienteId: number) => useQuery({
  queryKey: ['debitos', clienteId],
  queryFn: () => http.get<Debito[]>(`/api/clientes/${clienteId}/debitos`),
});

const NUM = 'text-right whitespace-nowrap';

const colunas: ColumnDef<Debito>[] = [
  { accessorKey: 'tributo', header: 'Tributo' },
  { accessorKey: 'orgaoCredor', header: 'Órgão', cell: ({ getValue }) => ORGAO_CREDOR[getValue<Debito['orgaoCredor']>()] },
  { accessorKey: 'competencia', header: 'Competência', cell: ({ getValue }) => fmt.competencia(getValue<string>()), meta: { classe: 'whitespace-nowrap' } },
  { accessorKey: 'dataVencimento', header: 'Vencimento', cell: ({ getValue }) => fmt.data(getValue<string>()), meta: { classe: 'whitespace-nowrap' } },
  { accessorKey: 'valorPrincipal', header: 'Principal', cell: ({ getValue }) => fmt.moeda(getValue<number>()), meta: { classe: NUM } },
  { accessorKey: 'valorMulta', header: 'Multa', cell: ({ getValue }) => fmt.moeda(getValue<number>()), meta: { classe: NUM } },
  { accessorKey: 'valorJuros', header: 'Juros', cell: ({ getValue }) => fmt.moeda(getValue<number>()), meta: { classe: NUM } },
  { accessorKey: 'situacao', header: 'Situação', cell: ({ getValue }) => SITUACAO_DEBITO[getValue<Debito['situacao']>()], meta: { classe: 'whitespace-nowrap' } },
  { accessorKey: 'referenciaOrigem', header: 'Origem' },
];

/** Débitos em aberto do cliente (somente leitura: vêm da importação do Relatório de Situação Fiscal, Fase 2b). */
export function Debitos() {
  const debitos = useDebitos(useClienteId());
  return (
      <Cartao aria-labelledby="titulo-debitos">
        <CartaoTitulo id="titulo-debitos">Débitos em aberto</CartaoTitulo>
        <DataTable rotulo="Débitos do cliente" colunas={colunas} dados={debitos.data} carregando={debitos.isPending} filtro="Filtrar débitos"
                   vazio={<EstadoVazio icone={Landmark} titulo="Nenhum débito"
                                       descricao="Os débitos aparecem quando o Relatório de Situação Fiscal for importado." />} />
      </Cartao>
  );
}
