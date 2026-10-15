import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http } from '@/lib/http';
import TributosPage from './tributos-page';

vi.mock('@/components/motion/use-movimento-reduzido', () => ({ useMovimentoReduzido: () => true }));

const origem = (registro: string, linha: number) => ({ registro, linha });
const apuracao = (id: number, tributo: string, esfera: string, uf: string | null, dataInicio: string, dataFim: string,
                  periodicidade: string, descricao: string, valorARecolher: number, extra: object = {}) => ({
  id, tributo, esfera, uf, dataInicio, dataFim, periodicidade, regime: tributo === 'IRPJ' || tributo === 'CSLL' ? 'LUCRO_REAL' : null,
  descricao, baseCalculo: null, valorApurado: valorARecolher, deducoes: 0, valorARecolher, saldoCredor: null,
  origem: { valorARecolher: origem('N630', 50 + id) }, documentoId: 9, arquivo: 'ecf-2021.txt', escrituracao: 'ECF', ...extra,
});

const APURACOES = [
  apuracao(1, 'IRPJ', 'FEDERAL', null, '2021-01-01', '2021-01-31', 'MENSAL', 'Estimativa mensal', 45),
  apuracao(2, 'IRPJ', 'FEDERAL', null, '2021-01-01', '2021-12-31', 'ANUAL', 'Ajuste anual', 480,
    { baseCalculo: 3500, valorApurado: 525, deducoes: 45, origem: { baseCalculo: origem('N630', 61), valorARecolher: origem('N630', 65) } }),
  apuracao(3, 'CSLL', 'FEDERAL', null, '2021-01-01', '2021-12-31', 'ANUAL', 'Ajuste anual', 288),
  apuracao(4, 'ICMS', 'ESTADUAL', 'PR', '2025-01-01', '2025-01-31', 'MENSAL', 'Operações próprias', 5000),
];
const LINHAS_IRPJ = [
  { grupo: 'LUCRO', codigo: '2', descricao: 'Lucro Líquido Antes do IRPJ', valor: 4500, registro: 'M300', linha: 30 },
  { grupo: 'ADICAO', codigo: '6', descricao: 'Provisões não dedutíveis', valor: 500, registro: 'M300', linha: 31 },
  { grupo: 'COMPENSACAO', codigo: '173', descricao: 'Compensação de prejuízos', valor: 1500, registro: 'M300', linha: 36 },
  { grupo: 'CALCULO', codigo: '26', descricao: 'IMPOSTO DE RENDA A PAGAR', valor: 480, registro: 'N630', linha: 65 },
];

function renderizar() {
  vi.spyOn(http, 'get').mockImplementation((url: string) => Promise.resolve(
    url.endsWith('/apuracoes') ? APURACOES
      : url.includes('/apuracoes/2/detalhe') ? { apuracao: APURACOES[1], linhas: LINHAS_IRPJ, obrigacoes: [] }
        : url.includes('/detalhe') ? { apuracao: APURACOES[2], linhas: [], obrigacoes: [] }
          : url.includes('/saldos-fiscais') ? [{ id: 1, tipo: 'PREJUIZO_FISCAL', uf: null, codigo: 'PF01', descricao: 'Prejuízo',
            dataInicio: '2021-01-01', dataFim: '2021-12-31', saldoInicial: 10000, saldoFinal: 8500, documentoId: 9, arquivo: 'ecf-2021.txt',
            registro: 'M500', linha: 40 }]
            : []) as never);
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/clientes/7/tributos']}>
        <Routes><Route path="/clientes/:clienteId/tributos" element={<TributosPage />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const matriz = () => screen.getByRole('table', { name: 'Valores apurados por tributo e competência' });
const cabecalhos = () => within(matriz()).getAllByRole('columnheader').map((c) => c.textContent);

afterEach(() => vi.restoreAllMocks());

describe('Aba Tributos', () => {
  it('matriz agrupa por esfera e troca a periodicidade somando as apurações da competência', async () => {
    renderizar();
    await screen.findByRole('table', { name: 'Valores apurados por tributo e competência' });
    expect(cabecalhos()).toEqual(['Tributo', '01/2021', '12/2021', '01/2025']);
    expect(within(matriz()).getByRole('rowheader', { name: 'ICMS · PR' })).toBeInTheDocument();
    expect(within(matriz()).getAllByRole('rowgroup').length).toBeGreaterThan(0);

    await userEvent.click(screen.getByRole('radio', { name: 'Trimestral' }));
    expect(cabecalhos()).toEqual(['Tributo', '1º tri/2021', '4º tri/2021', '1º tri/2025']);

    await userEvent.click(screen.getByRole('radio', { name: 'Anual' }));
    expect(cabecalhos()).toEqual(['Tributo', '2021', '2025']);
    // IRPJ de 2021: estimativa de janeiro (45) + ajuste anual (480)
    expect(screen.getByRole('button', { name: /IRPJ em 2021, A recolher: R\$\s?525,00/ })).toBeInTheDocument();
  });

  it('célula abre o detalhe com os valores, a origem e as linhas do cálculo', async () => {
    renderizar();
    await userEvent.click(await screen.findByRole('button', { name: /IRPJ em 12\/2021, A recolher/ }));
    const gaveta = await screen.findByRole('dialog');
    expect(within(gaveta).getByText('Ajuste anual')).toBeInTheDocument();
    expect(within(gaveta).getByText('Base de cálculo')).toBeInTheDocument();

    await userEvent.click(within(gaveta).getByRole('button', { name: /Origem de A recolher — Ajuste anual/ }));
    expect(await screen.findByText('Registro N630 · linha 65')).toBeInTheDocument();

    await userEvent.click(within(gaveta).getByRole('button', { name: 'Linhas do cálculo e obrigações' }));
    expect(await within(gaveta).findByText('IMPOSTO DE RENDA A PAGAR')).toBeInTheDocument();
  });

  it('demonstração do lucro real em cascata, com grupos que abrem', async () => {
    renderizar();
    const irpj = await screen.findByRole('region', { name: 'IRPJ' });
    const adicoes = await within(irpj).findByRole('button', { name: /Adições/ });
    expect(adicoes).toHaveTextContent('R$ 500,00');
    expect(adicoes).toHaveAttribute('aria-expanded', 'false');
    await userEvent.click(adicoes);
    expect(within(irpj).getByText('Provisões não dedutíveis')).toBeInTheDocument();
    expect(within(irpj).getByRole('button', { name: /Compensação de prejuízos/ })).toHaveTextContent('R$ 1.500,00');
    expect(within(irpj).getByText('= A recolher').nextSibling).toHaveTextContent('R$ 480,00');
  });

  it('saldo fiscal aparece em card', async () => {
    renderizar();
    expect(await screen.findByText('Prejuízo fiscal', { selector: 'p' })).toBeInTheDocument();
  });

  it('sem importações, cada bloco diz qual arquivo importar', async () => {
    vi.spyOn(http, 'get').mockResolvedValue([] as never);
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={['/clientes/7/tributos']}>
          <Routes><Route path="/clientes/:clienteId/tributos" element={<TributosPage />} /></Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(await screen.findByText(/IRPJ e CSLL vêm da ECF; PIS e COFINS, da EFD-Contribuições/)).toBeInTheDocument();
    expect(screen.getByText(/A demonstração do lucro real ou presumido vem da ECF/)).toBeInTheDocument();
    expect(await screen.findByText(/Prejuízo fiscal e base negativa vêm da Parte B do e-Lalur/)).toBeInTheDocument();
  });
});
