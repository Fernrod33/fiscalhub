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

const ORIGEM_DARF = { documentoId: 20, arquivo: 'DARF_COFINS.pdf', tipoDocumento: 'DARF', pagina: 1, registro: null, linha: 13,
  trecho: '5856-01 | COFINS - Não cumulativa | 28.000,00' };
const item = (id: string, tributo: string, esfera: string, uf: string | null, periodo: string, status: string,
              apurado: number | null, declarado: number | null, pago: number | null, diferenca: number, extra: object = {}) => ({
  id, tributo, esfera, uf, periodo, vencimento: `${periodo}-25`, status,
  apurado: apurado == null ? null : { valor: apurado, origens: [{ documentoId: 9, arquivo: 'efd.txt', tipoDocumento: 'EFD_CONTRIBUICOES',
    pagina: null, registro: 'M600', linha: 40, trecho: null }] },
  declarado: declarado == null ? null : { valor: declarado, origens: [] },
  pago: pago == null ? null : { valor: pago, multa: 0, juros: 0, dataPagamento: `${periodo}-25`, origens: [ORIGEM_DARF] },
  diferenca, acrescimos: null, erroAcrescimos: null, codigosReceita: [], codigosIlustrativos: [], debitos: [], ...extra,
});
const CONCILIACAO = {
  tolerancia: 0.01, dataCalculo: '2026-10-27', alertas: [],
  itens: [
    item('IRPJ-2021-01', 'IRPJ', 'FEDERAL', null, '2021-01', 'CONCILIADO', 45, null, null, 0),
    item('IRPJ-2021-12', 'IRPJ', 'FEDERAL', null, '2021-12', 'CONCILIADO', 480, null, null, 0),
    item('CSLL-2021-12', 'CSLL', 'FEDERAL', null, '2021-12', 'CONCILIADO', 288, null, null, 0),
    item('ICMS-PR-2025-01', 'ICMS', 'ESTADUAL', 'PR', '2025-01', 'CONCILIADO', 5000, 5000, 5000, 0),
    item('COFINS-2026-08', 'COFINS', 'FEDERAL', null, '2026-08', 'PAGO_A_MENOR', 33079, 33079, 28000, 5079, {
      codigosReceita: ['5856-01'],
      acrescimos: { principal: 5079, vencimento: '2026-09-25', pagamento: '2026-10-27', diasAtraso: 32, percentualMulta: 10.56,
        multa: 536.34, selicDe: null, selicAte: null, percentualJuros: 1, juros: 50.79 },
      debitos: [{ id: 3, orgao: 'RFB', situacao: 'EM_ABERTO', valorTotal: 5448.24, impedeCertidao: true,
        origens: [{ documentoId: 21, arquivo: 'Relatorio_Situacao_Fiscal.pdf', tipoDocumento: 'RELATORIO_SITUACAO_FISCAL', pagina: 1,
          registro: null, linha: 14, trecho: '5856-01 - COFINS | 08/2026' }] }],
    }),
  ],
};

function renderizar() {
  vi.spyOn(http, 'get').mockImplementation((url: string) => Promise.resolve(
    url.endsWith('/apuracoes') ? APURACOES
      : url.endsWith('/conciliacao') ? CONCILIACAO
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

const METRICAS = ['Apurado', 'Declarado', 'Pago', 'Diferença'];
const NOME_MATRIZ = 'Apurado, declarado e pago por tributo e competência';
const matriz = () => screen.getByRole('table', { name: NOME_MATRIZ });
const competencias = () => within(matriz()).getAllByRole('columnheader').map((c) => c.textContent)
  .filter((t) => t && !METRICAS.includes(t));

afterEach(() => vi.restoreAllMocks());

describe('Aba Tributos', () => {
  it('matriz com apurado, declarado, pago e diferença por competência, e a periodicidade somando os meses', async () => {
    renderizar();
    await screen.findByRole('table', { name: NOME_MATRIZ });
    expect(competencias()).toEqual(['Tributo', '01/2021', '12/2021', '01/2025', '08/2026']);
    expect(within(matriz()).getAllByRole('columnheader', { name: 'Declarado' })).toHaveLength(4);
    expect(within(matriz()).getByRole('rowheader', { name: 'ICMS · PR' })).toBeInTheDocument();
    expect(within(matriz()).getByRole('button', { name: /COFINS em 08\/2026, Diferença: R\$\s?5\.079,00, Pago a menor/ }))
      .toHaveTextContent('Pago a menor');

    await userEvent.click(screen.getByRole('radio', { name: 'Anual' }));
    expect(competencias()).toEqual(['Tributo', '2021', '2025', '2026']);
    // IRPJ de 2021: janeiro (45) + dezembro (480)
    expect(screen.getByRole('button', { name: /IRPJ em 2021, Apurado: R\$\s?525,00/ })).toBeInTheDocument();
  });

  it('filtro por status deixa só as células com aquele status', async () => {
    renderizar();
    await screen.findByRole('table', { name: NOME_MATRIZ });
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Status' }), 'PAGO_A_MENOR');
    expect(competencias()).toEqual(['Tributo', '08/2026']);
    expect(within(matriz()).queryByRole('rowheader', { name: 'IRPJ' })).not.toBeInTheDocument();
  });

  it('gaveta da conciliação: os três valores com origem, acréscimos e o débito da situação fiscal', async () => {
    renderizar();
    await userEvent.click(await screen.findByRole('button', { name: /COFINS em 08\/2026, Pago:/ }));
    const gaveta = await screen.findByRole('dialog');
    expect(within(gaveta).getAllByText('Pago a menor').length).toBeGreaterThan(0);
    expect(within(gaveta).getByLabelText('Acréscimos devidos')).toHaveTextContent('32 dias');
    expect(within(gaveta).getByText('Impede certidão')).toBeInTheDocument();
    expect(within(gaveta).getByText('Relatorio_Situacao_Fiscal.pdf')).toBeInTheDocument();
    await userEvent.click(within(gaveta).getByRole('button', { name: /Origem de Pago — COFINS 08\/2026/ }));
    expect(await screen.findByText('DARF_COFINS.pdf')).toBeInTheDocument();
    expect(screen.getByText(/Página 1 · linha 13/)).toBeInTheDocument();
    expect(screen.getByText(/5856-01 \| COFINS - Não cumulativa/)).toBeInTheDocument();
  });

  it('gaveta traz também o cálculo da apuração do período, com a origem e as linhas', async () => {
    renderizar();
    await userEvent.click(await screen.findByRole('button', { name: /IRPJ em 12\/2021, Apurado/ }));
    const gaveta = await screen.findByRole('dialog');
    expect(within(gaveta).getByText('Ajuste anual')).toBeInTheDocument();
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
    expect(await screen.findByText(/O apurado vem da ECF e das EFD; o declarado, da DCTF/)).toBeInTheDocument();
    expect(screen.getByText(/A demonstração do lucro real ou presumido vem da ECF/)).toBeInTheDocument();
    expect(await screen.findByText(/Prejuízo fiscal e base negativa vêm da Parte B do e-Lalur/)).toBeInTheDocument();
  });
});
