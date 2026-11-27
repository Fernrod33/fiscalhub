import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http } from '@/lib/http';
import SituacaoFiscalPage from './situacao-fiscal-page';

vi.mock('@/components/motion/use-movimento-reduzido', () => ({ useMovimentoReduzido: () => true }));

const origem = (arquivo: string, linha: number) => ({ documentoId: 1, arquivo, tipoDocumento: 'RELATORIO_SITUACAO_FISCAL', pagina: 1,
  registro: null, linha, trecho: `trecho ${linha}` });
const debito = (id: number, orgaoCredor: string, tributo: string, situacao: string, valorTotal: number, extra: object = {}) => ({
  id, clienteId: 7, orgaoCredor, tributo, codigoReceita: null, competencia: null, periodo: null, dataVencimento: null,
  valorPrincipal: valorTotal, valorMulta: null, valorJuros: null, valorEncargo: null, valorTotal, situacao, situacaoDocumento: situacao,
  numeroInscricao: null, numeroProcesso: null, ajuizado: null, negociacao: null, impedeCertidao: situacao === 'EM_ABERTO' || situacao === 'INSCRITO_DIVIDA_ATIVA',
  divergente: false, documentoOrigemId: 1, referenciaOrigem: 'página 1, linha 14',
  origens: [{ origem: origem('Relatorio_Situacao_Fiscal.pdf', 14), dataReferencia: '2026-10-14', principal: valorTotal, multa: null,
    juros: null, encargo: null, total: valorTotal, situacao }], ...extra,
});

const DEBITOS = [
  debito(1, 'RFB', 'COFINS', 'EM_ABERTO', 5448.24, { competencia: '2026-08-01' }),
  debito(2, 'PGFN', 'IPI', 'INSCRITO_DIVIDA_ATIVA', 64176.14, { numeroInscricao: '90 3 24 002201-55' }),
  debito(3, 'PGFN', 'IRPJ', 'NEGOCIADO', 230934.79, { numeroInscricao: '90 2 23 004512-38', negociacao: 'Transação 2021.009871-4' }),
  debito(4, 'PGE_PR', 'ICMS', 'PARCELADO', 141872.66, { numeroInscricao: '2024.0012345-8' }),
];
const PARCELAMENTOS = [{ id: 1, orgao: 'PGE_PR', numero: 'PR-2025-0045871', modalidade: 'Parcelamento estadual', parcelasPagas: 12,
  parcelasTotal: 36, valorParcela: 4120.35, saldo: 98884.2, situacao: 'EM_DIA', debitos: ['2024.0012345-8'], dataReferencia: '2026-10-14',
  origem: origem('Conta_Corrente.pdf', 39) }];
const CAPAG = [{ id: 1, fonte: 'PGFN', classe: 'C', capacidade: 400000, passivo: 512791.83, razao: 0.78, classeConsistente: true,
  dataReferencia: '2026-10-01', origem: origem('CAPAG.pdf', 5) }];
const PENDENCIAS = [{ id: 1, tipo: 'OMISSAO_DECLARACAO', descricao: 'Omissão de EFD-Contribuições de 06/2026', periodo: '2026-06-01',
  situacao: 'OMISSA', dataReferencia: '2026-10-14', origem: origem('Relatorio_Situacao_Fiscal.pdf', 17) }];

function renderizar(capag: object[] = CAPAG) {
  vi.spyOn(http, 'get').mockImplementation((url: string) => Promise.resolve(
    url.endsWith('/debitos') ? DEBITOS : url.endsWith('/parcelamentos') ? PARCELAMENTOS : url.endsWith('/capacidade-oficial') ? capag
      : url.endsWith('/pendencias') ? PENDENCIAS : []) as never);
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/clientes/7/situacao-fiscal']}>
        <Routes><Route path="/clientes/:clienteId/situacao-fiscal" element={<SituacaoFiscalPage />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.restoreAllMocks());

describe('Aba Situação fiscal', () => {
  it('destaca o que impede a certidão: inscrição sem negociação e débito em aberto', async () => {
    renderizar();
    const certidao = await screen.findByRole('alert');
    expect(certidao).toHaveTextContent('2 débitos impedem a certidão');
    expect(certidao).toHaveTextContent('90 3 24 002201-55');
    expect(certidao).toHaveTextContent('sem negociação');
    expect(certidao).not.toHaveTextContent('90 2 23 004512-38'); // negociada na transação
  });

  it('débitos com total por órgão e filtro por órgão e situação', async () => {
    renderizar();
    const tabela = await screen.findByRole('table', { name: 'Débitos do cliente' });
    expect(within(tabela).getAllByRole('row')).toHaveLength(5);
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Órgão' }), 'PGFN');
    expect(within(tabela).getAllByRole('row')).toHaveLength(3);
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Situação' }), 'NEGOCIADO');
    expect(within(tabela).getAllByRole('row')).toHaveLength(2);
    expect(screen.getByText('PGFN', { selector: 'dt' }).nextSibling).toHaveTextContent('R$ 295.110,93');
  });

  it('parcelamento com o progresso das parcelas, pendência e CAPAG', async () => {
    renderizar();
    const barra = await screen.findByRole('progressbar', { name: /Parcelas pagas do PR-2025-0045871: 12 de 36/ });
    expect(barra).toHaveAttribute('aria-valuenow', '12');
    expect(barra).toHaveAttribute('aria-valuemax', '36');
    expect(screen.getByText('Em dia')).toBeInTheDocument();
    expect(await screen.findByText('Omissão de EFD-Contribuições de 06/2026')).toBeInTheDocument();
    expect(await screen.findByLabelText('Classe C')).toBeInTheDocument();
    expect(screen.getByText(/capacidade ÷ passivo = 0,78x/)).toBeInTheDocument();
    expect(screen.queryByText(/A classe não bate/)).not.toBeInTheDocument();
  });

  it('CAPAG inconsistente com a Portaria aparece com alerta', async () => {
    renderizar([{ ...CAPAG[0], classe: 'A', classeConsistente: false }]);
    expect(await screen.findByText(/A classe não bate com a razão capacidade ÷ passivo/)).toBeInTheDocument();
  });
});
