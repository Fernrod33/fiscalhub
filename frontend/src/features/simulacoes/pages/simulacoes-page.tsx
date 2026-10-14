import { useQuery } from '@tanstack/react-query';
import { Calculator } from 'lucide-react';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { DataTable, type ColumnDef } from '@/components/data-table';
import { EstadoVazio } from '@/components/estado-vazio';
import { Reveal } from '@/components/motion';
import type { Simulacao } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { http } from '@/lib/http';
import { useClienteId } from '@/features/clientes/rota';

const useSimulacoes = (clienteId: number) => useQuery({
  queryKey: ['simulacoes', clienteId],
  queryFn: () => http.get<Simulacao[]>(`/api/simulacoes/cliente/${clienteId}`),
});

const NUM = 'text-right whitespace-nowrap';

const colunas: ColumnDef<Simulacao>[] = [
  { accessorKey: 'dataCalculo', header: 'Data do cálculo', cell: ({ getValue }) => fmt.data(getValue<string>()), meta: { classe: 'whitespace-nowrap' } },
  { accessorKey: 'modalidade', header: 'Modalidade' },
  { accessorKey: 'valorOriginal', header: 'Valor original', cell: ({ getValue }) => fmt.moeda(getValue<number>()), meta: { classe: NUM } },
  { accessorKey: 'valorAtualizado', header: 'Atualizado', cell: ({ getValue }) => fmt.moeda(getValue<number>()), meta: { classe: NUM } },
  { accessorKey: 'numeroParcelas', header: 'Parcelas', meta: { classe: NUM } },
  { accessorKey: 'valorParcela', header: 'Parcela', cell: ({ getValue }) => fmt.moeda(getValue<number>()), meta: { classe: NUM } },
  { accessorKey: 'aprovada', header: 'Aprovada', cell: ({ getValue }) => (getValue<boolean>() ? 'Sim' : 'Não') },
];

/** Simulações salvas (somente leitura; o simulador novo vem na Fase 5). */
export default function SimulacoesPage() {
  const simulacoes = useSimulacoes(useClienteId());
  return (
    <Reveal>
      <Cartao aria-labelledby="titulo-simulacoes">
        <CartaoTitulo id="titulo-simulacoes">Simulações de parcelamento</CartaoTitulo>
        <DataTable rotulo="Simulações" colunas={colunas} dados={simulacoes.data} carregando={simulacoes.isPending}
                   vazio={<EstadoVazio icone={Calculator} titulo="Nenhuma simulação"
                                       descricao="O simulador de parcelamento (RFB e SEFA-PR) chega numa próxima fase." />} />
      </Cartao>
    </Reveal>
  );
}
