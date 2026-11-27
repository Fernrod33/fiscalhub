import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http } from '@/lib/http';
import SimulacoesPage from './simulacoes-page';

vi.mock('@/components/motion/use-movimento-reduzido', () => ({ useMovimentoReduzido: () => true }));
vi.mock('@/features/auth/permissoes', () => ({ usePermissoes: () => ({ alterar: true, admin: false }) }));

const DEBITO = { id: 11, orgaoCredor: 'PGFN', tributo: 'IPI', numeroInscricao: '90 3 24 002201-55', competencia: null, valorTotal: 64176.14,
  valorPrincipal: 38750, situacaoDocumento: 'ATIVA A SER AJUIZADA', origens: [] };
const parcela = (numero: number, valor: number, projetada = false) => ({ numero, tipo: 'PARCELA', vencimento: '2026-11-30', nominal: 306.14,
  percentualCorrecao: 1, valor, projetada, saldoNominal: 0, saldoProjetado: 0 });
const modalidade = (chave: string, nome: string, cenario: string, extra: object = {}) => ({
  chave, versaoRegra: `${chave}@2026-10-01`, nome, tipo: 'TRANSACAO', orgao: 'PGFN', cenario, cenarioRotulo: null, elegivel: true, bloqueio: null,
  avisos: [], elegibilidade: [{ debitoId: 11, descricao: 'IPI 90 3 24 002201-55', elegivel: true, motivos: [] }],
  debitos: [{ debitoId: 11, descricao: 'IPI 90 3 24 002201-55', orgao: 'PGFN', vencimento: null, principal: 38750, percentualMulta: null,
    multa: 7750, percentualJuros: null, juros: 6980.12, encargo: 10696.02, total: 64176.14, desconto: 0, posicao: 'posição do documento em 14/10/2026',
    fundamento: '', origem: null }],
  totalConsolidado: 64176.14, desconto: null, entrada: 0, prestacoesEntrada: 0, parcelas: 2, valorParcela: 306.14, indice: 'SELIC',
  selicProjetada: 1.01, selicProjetadaOrigem: 'média', cronograma: [parcela(1, 309.2), parcela(2, 312.3, true)], custoNominal: 612.28,
  custoProjetado: 621.5, economiaVsAVista: 100, selo: { cabe: true, maiorParcela: 312.3, parcelaSustentavel: 9000, percentualComprometido: 3.47,
    texto: 'Cabe na parcela sustentável' },
  memoria: [{ descricao: 'Desconto da transação', formula: 'até 100%', valor: -25426.14, unidade: 'R$', fundamento: 'Edital 6/2026' }],
  fundamento: 'Edital PGDAU 6/2026', rescisao: '3 prestações', garantia: null, nota: null, fonteSecundaria: true,
  fontes: [{ chave: 'edital', titulo: 'Edital PGDAU 6/2026', url: 'https://www.in.gov.br/x', data: '2026-10-29', sha256: 'c1779ef58acf', secundaria: true, observacao: null }],
  ...extra,
});
const SIMULACAO = {
  id: null, versao: 1, anteriorId: null, dataBase: '2026-10-14', debitoChaves: [], pedido: null, versaoRegras: '2026-10-29',
  versaoIndices: 'SELIC até 2025-01; UPF/PR até 2025-04',
  insumos: { capagOficial: 'C', capagOficialData: '2026-10-14', capagEstimada: 'D', parcelaSustentavel: 9000, selicProjetadaParametro: null, pessoaFisica: false, avisos: [] },
  modalidades: [
    modalidade('PGFN_TRANSACAO_EDITAL_6_2026', 'Transação por adesão', 'OFICIAL', { cenarioRotulo: 'CAPAG oficial C' }),
    modalidade('PGFN_TRANSACAO_EDITAL_6_2026', 'Transação por adesão', 'SE_REVISAO_DEFERIDA',
      { cenarioRotulo: 'se a revisão da CAPAG for deferida (CAPAG estimada D)' }),
    modalidade('PGFN_ORDINARIO', 'Parcelamento ordinário — PGFN', 'OFICIAL', { tipo: 'PARCELAMENTO', custoProjetado: 83301.85,
      selo: { cabe: false, maiorParcela: 1707.09, parcelaSustentavel: 1000, percentualComprometido: 170.71, texto: 'Compromete 170,71% da parcela sustentável' } }),
  ],
  recomendacao: [{ orgao: 'PGFN', chave: 'PGFN_TRANSACAO_EDITAL_6_2026', versaoRegra: 'x', cenario: 'OFICIAL', nome: 'Transação por adesão',
    custoProjetado: 621.5, economiaVsAVista: 100, cabe: true, justificativa: 'Menor custo' }],
  custoRecomendado: 621.5, motorAntigo: null, criadoEm: null, criadoPor: null,
};

function renderizar() {
  vi.spyOn(http, 'get').mockImplementation((url: string) => Promise.resolve((url.endsWith('/debitos') ? [DEBITO]
    : url.endsWith('/simulacoes') ? [] : null) as never));
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/clientes/7/simulacoes']}>
        <Routes><Route path="/clientes/:clienteId/simulacoes" element={<SimulacoesPage />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('aba Simulações', () => {
  it('simula os débitos escolhidos e compara as modalidades com a recomendada e o selo', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue(SIMULACAO as never);
    renderizar();
    await userEvent.click(await screen.findByRole('checkbox', { name: /IPI 90 3 24 002201-55/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Simular' }));
    expect(post).toHaveBeenCalledWith('/api/clientes/7/simulacoes/calcular', expect.objectContaining({ debitoIds: [11] }));

    const pgfn = await screen.findByRole('region', { name: 'Modalidades — PGFN' });
    const oficial = within(pgfn).getByRole('button', { name: 'Transação por adesão — CAPAG oficial C' });
    expect(oficial).toHaveAttribute('aria-pressed', 'true'); // a recomendada abre selecionada
    expect(within(oficial).getByText('recomendada')).toBeInTheDocument();
    expect(within(pgfn).getByRole('button', { name: /se a revisão da CAPAG for deferida/ })).toBeInTheDocument();
    expect(within(pgfn).getByText('Compromete 170,71% da parcela sustentável')).toBeInTheDocument();
  });

  it('detalhe com cronograma projetado, memória recolhível e fonte secundária', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(SIMULACAO as never);
    renderizar();
    await userEvent.click(await screen.findByRole('checkbox', { name: /IPI/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Simular' }));
    await userEvent.click(await screen.findByRole('button', { name: /Ver cronograma/ }));
    const cron = screen.getByRole('table', { name: 'Cronograma — Transação por adesão' });
    expect(within(cron).getAllByRole('row')[2]).toHaveTextContent('projeção');
    await userEvent.click(screen.getByRole('button', { name: /Ver memória de cálculo/ }));
    expect(screen.getByRole('list', { name: 'Memória de cálculo — Transação por adesão' })).toHaveTextContent('Desconto da transação');
    await userEvent.click(screen.getByRole('button', { name: /Ver regra e fontes/ }));
    expect(screen.getAllByText('fonte secundária').length).toBeGreaterThan(0);
  });
});
