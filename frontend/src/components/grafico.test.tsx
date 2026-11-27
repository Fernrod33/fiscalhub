import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';

const movimento = vi.hoisted(() => ({ reduzido: false }));
vi.mock('@/components/motion/use-movimento-reduzido', () => ({ useMovimentoReduzido: () => movimento.reduzido }));

import { Grafico } from './grafico';

const DADOS = [{ competencia: '07/2026', apurado: 1000, pago: 1000 }, { competencia: '08/2026', apurado: 33079, pago: 28000 }];
const nbsp = (s: string) => s.replace(/ /g, ' ');

describe('Grafico', () => {
  it('tem rótulo, legenda das séries e a tabela com os valores formatados (identidade não depende só da cor)', () => {
    render(<Grafico titulo="Apurado × pago por competência" tipo="barras" formato="moeda" categoria="competencia"
                    series={[{ chave: 'apurado', nome: 'Apurado' }, { chave: 'pago', nome: 'Pago' }]} dados={DADOS} />);
    const figura = screen.getByRole('figure', { name: 'Apurado × pago por competência' });
    const tabela = within(figura).getByRole('table', { name: 'Apurado × pago por competência' });
    expect(within(tabela).getByRole('columnheader', { name: 'Apurado' })).toBeInTheDocument();
    const agosto = within(tabela).getByRole('row', { name: /08\/2026/ });
    expect(within(agosto).getByRole('cell', { name: nbsp('R$ 33.079,00') })).toBeInTheDocument();
    expect(within(agosto).getByRole('cell', { name: nbsp('R$ 28.000,00') })).toBeInTheDocument();
  });

  it('sem animação de entrada quando o sistema pede menos movimento', () => {
    movimento.reduzido = true;
    render(<Grafico titulo="Carga" tipo="linha" formato="percentual" categoria="exercicio"
                    series={[{ chave: 'carga', nome: 'Carga efetiva' }]} dados={[{ exercicio: '2025', carga: 12.5 }]} />);
    expect(screen.getByRole('figure', { name: 'Carga' })).toHaveAttribute('data-animado', 'false');
    expect(screen.getByRole('cell', { name: '12,50%' })).toBeInTheDocument();
  });
});
