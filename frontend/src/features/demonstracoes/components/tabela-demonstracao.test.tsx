import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { TooltipProvider } from '@/components/ui/tooltip';
import type { Comparativo } from '@/lib/api/tipos';
import { http } from '@/lib/http';
import { TabelaDemonstracao } from './tabela-demonstracao';

vi.mock('@/components/motion/use-movimento-reduzido', () => ({ useMovimentoReduzido: () => true }));

const valor = (linhaId: number, v: number, av: number | null, ah: number | null) => ({ linhaId, valor: v, valorInicial: 0, av, ah });

const COMPARATIVO = {
  tipo: 'BP', origem: 'REFERENCIAL',
  exercicios: [
    { exercicio: 2023, demonstracaoId: 1, documentoId: 10, arquivo: 'ecd-2023.txt', origemDados: 'ECD', dataInicio: '2023-01-01', dataFim: '2023-12-31', tabelaReferencial: 'L100_A' },
    { exercicio: 2024, demonstracaoId: 2, documentoId: 11, arquivo: 'ecd-2024.txt', origemDados: 'ECD', dataInicio: '2024-01-01', dataFim: '2024-12-31', tabelaReferencial: 'L100_A' },
  ],
  linhas: [
    { codigo: '1', descricao: 'ATIVO', nivel: 1, codigoSuperior: '', totalizadora: true, grupo: 'A',
      valores: { 2023: valor(1, 3000, 100, null), 2024: valor(2, 6000, 100, 100) } },
    { codigo: '1.01', descricao: 'ATIVO CIRCULANTE', nivel: 2, codigoSuperior: '1', totalizadora: true, grupo: 'A',
      valores: { 2023: valor(3, 3000, 100, null), 2024: valor(4, 6000, 100, 100) } },
    { codigo: '1.01.01', descricao: 'Caixa', nivel: 3, codigoSuperior: '1.01', totalizadora: false, grupo: 'A', hierarquiaHerdada: true,
      valores: { 2023: valor(5, 2000, 66.67, null), 2024: valor(6, 4000, 66.67, 100) } },
    { codigo: '2', descricao: 'PASSIVO', nivel: 1, codigoSuperior: '', totalizadora: true, grupo: 'P',
      valores: { 2023: valor(7, -3000, 100, null), 2024: valor(8, -6000, 100, 100) } },
  ],
  conferencia: [{ exercicio: 2024, item: 'Ativo', referencial: 6000, publicada: 6500, diferenca: -500 }],
} as unknown as Comparativo;

function renderizar(opcoes: { mostrarAh?: boolean; mostrarAv?: boolean } = {}) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <TooltipProvider>
          <TabelaDemonstracao comparativo={COMPARATIVO} clienteId={7} mostrarAh={opcoes.mostrarAh ?? false} mostrarAv={opcoes.mostrarAv ?? false} />
        </TooltipProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.restoreAllMocks());

describe('TabelaDemonstracao', () => {
  it('mostra exercícios lado a lado com D/C e liga/desliga AH% e AV%', () => {
    const { rerender, unmount } = renderizar();
    expect(screen.getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['Conta', '2023', '2024']);
    expect(screen.getByRole('button', { name: /Origem de PASSIVO em 2024/ })).toHaveTextContent('6.000,00 C');
    unmount();

    renderizar({ mostrarAh: true, mostrarAv: true });
    expect(screen.getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['Conta', '2023', 'AV%', '2024', 'AV%', 'AH%']);
    expect(rerender).toBeDefined();
  });

  it('recolhe e expande a hierarquia', async () => {
    renderizar();
    expect(screen.getByText('Caixa')).toBeInTheDocument(); // mostra até o 3º nível

    await userEvent.click(screen.getByRole('button', { name: 'Recolher ATIVO CIRCULANTE' }));
    await waitFor(() => expect(screen.queryByText('Caixa')).not.toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: 'Expandir ATIVO CIRCULANTE' }));
    expect(await screen.findByText('Caixa')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Recolher ATIVO' }));
    await waitFor(() => expect(screen.queryByText('ATIVO CIRCULANTE')).not.toBeInTheDocument());
    expect(screen.getByRole('button', { name: 'Expandir ATIVO' })).toHaveAttribute('aria-expanded', 'false');
  });

  it('sinaliza a diferença da conferência na linha de topo', () => {
    renderizar();
    expect(screen.getByLabelText('Difere da publicada em 500,00 C')).toBeInTheDocument();
  });

  it('clicar no valor abre a origem: contas com registro e linha', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({
      linhaId: 2, codigo: '1', descricao: 'ATIVO', tipo: 'BP', origem: 'REFERENCIAL', documentoId: 11, arquivo: 'ecd-2024.txt',
      registro: null, linha: null,
      contas: [{ contaId: 1, codigo: '1.1.01', nome: 'Caixa', codigoReferencial: '1.01.01.01.01', valor: 4000,
        saldos: [{ registro: 'I155', linha: 26, valor: 4000, dataFim: '2024-12-31', centroCusto: null }] }],
    });
    renderizar();
    await userEvent.click(screen.getByRole('button', { name: /Origem de ATIVO em 2024/ }));

    expect(await screen.findByText('ecd-2024.txt')).toBeInTheDocument();
    expect(screen.getByText(/I155 · linha 26/)).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith('/api/clientes/7/demonstracoes/linhas/2/origem');
  });

  it('linha com hierarquia herdada tem selo e a origem diz de qual tabela veio a posição', async () => {
    vi.spyOn(http, 'get').mockResolvedValue({
      linhaId: 6, codigo: '1.01.01', descricao: 'Caixa', tipo: 'BP', origem: 'REFERENCIAL', hierarquiaHerdadaDe: 2021,
      documentoId: 11, arquivo: 'ecd-2024.txt', registro: null, linha: null, contas: [],
    });
    renderizar();
    expect(screen.getByText('hierarquia herdada')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Origem de Caixa em 2024/ }));
    expect(await screen.findByText(/não existe na tabela oficial de 2024; a posição na demonstração veio da tabela de 2021/))
      .toBeInTheDocument();
  });
});
