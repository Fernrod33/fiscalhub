import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http } from '@/lib/http';
import CapacidadePage from './capacidade-page';

vi.mock('@/components/motion/use-movimento-reduzido', () => ({ useMovimentoReduzido: () => true }));
vi.mock('@/features/auth/permissoes', () => ({ usePermissoes: () => ({ alterar: true, admin: false }) }));

const origem = { documentoId: 1, arquivo: 'Conta_Corrente.pdf', tipoDocumento: 'CONTA_CORRENTE_PR', pagina: 1, registro: null, linha: 9, trecho: 'Situação cadastral: ATIVA' };
const criterio = (nome: string, valor: string | null, pontos: string | null, extra: object = {}) => ({
  nome, valor, formato: 'TEXTO', pontos, faixa: null, fundamento: 'Resolução Conjunta PGE/SEFA 01/2025, art. 7º', origens: [origem], naoDisponivel: null, ...extra,
});
const resultado = (metodologia: string, rotulo: string, rotuloTexto: string, extra: object = {}) => ({
  metodologia, titulo: metodologia, rotulo, rotuloTexto, valor: null, classe: null, criterios: [], itens: [], parametros: {},
  normas: [], notas: [], alertas: [], ...extra,
});
const PR = resultado('PR', 'ESTIMATIVA', 'recálculo pela Resolução Conjunta PGE/SEFA 01/2025 — não é a classificação oficial', {
  valor: 17, classe: 'B', parametros: { oficial: 'B', dataCalculo: '2026-10-14' },
  criterios: [criterio('Situação cadastral', 'ATIVA', '4'), criterio('Índice de endividamento', null, null,
    { naoDisponivel: 'não disponível — importe a EFD ICMS/IPI dos meses [07/2026]', origens: [] })],
  itens: [{ id: '2023.0045678-2', descricao: 'ICMS', valor: '315000.00', indice: '1.658437', classe: 'C', classeOficial: 'C', criterios: [], observacao: null }],
});
const CAPACIDADE = {
  capagOficial: resultado('CAPAG_OFICIAL', 'OFICIAL', 'oficial (documento do órgão)', { classe: 'C', valor: 400000 }),
  metodologias: [
    resultado('CAPAG_ESTIMADA', 'ESTIMATIVA', 'estimativa do consultor', { classe: 'D', valor: 120000,
      alertas: ['Revisão da CAPAG potencialmente vantajosa: a classe estimada (D) é mais favorável que a oficial (C).'] }),
    PR,
    resultado('GERENCIAL', 'GERENCIAL', 'análise do consultor — não é critério oficial', { valor: 2019.9 }),
  ],
  indicadores: [{ exercicio: 2025, ebitda: 50000, fco: 80795.98 }],
  parametros: { capag: { faixaA: 2, faixaB: 1, faixaC: 0.5, metodoConsiderado: 'MENOR', exercicios: 2, realizacaoDisponivel: 100,
    realizacaoCreditos: 80, realizacaoEstoques: 50, realizacaoImobilizado: 30 }, gerencial: { percentualFcoLivre: 30 } },
};

function renderizar() {
  vi.spyOn(http, 'get').mockImplementation((url: string) => Promise.resolve((url.endsWith('/capacidade') ? CAPACIDADE
    : url.endsWith('/dfc/exercicios') ? [] : null) as never));
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/clientes/7/capacidade']}>
        <Routes><Route path="/clientes/:clienteId/capacidade" element={<CapacidadePage />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('aba Capacidade', () => {
  it('mostra a oficial e a estimada lado a lado com os rótulos e o destaque da revisão', async () => {
    renderizar();
    const pgfn = await screen.findByRole('region', { name: 'PGFN — CAPAG' });
    expect(within(pgfn).getByLabelText('Classe C')).toBeInTheDocument();
    expect(within(pgfn).getByLabelText('Classe D')).toBeInTheDocument();
    expect(within(pgfn).getByText('Oficial')).toBeInTheDocument();
    expect(within(pgfn).getByText('Estimativa')).toBeInTheDocument();
    expect(within(pgfn).getByRole('status')).toHaveTextContent('Revisão da CAPAG potencialmente vantajosa');
    const ger = screen.getByRole('region', { name: 'Análise gerencial' });
    expect(within(ger).getByText('análise do consultor — não é critério oficial')).toBeInTheDocument();
  });

  it('detalhe por critério com pontos, fundamento, origem e o insumo faltando', async () => {
    renderizar();
    const pr = await screen.findByRole('region', { name: 'Paraná — PGE/SEFA' });
    const tabela = within(pr).getByRole('table', { name: 'Critérios do devedor' });
    const situacao = within(tabela).getByRole('row', { name: /Situação cadastral/ });
    expect(situacao).toHaveTextContent('4');
    expect(situacao).toHaveTextContent('art. 7º');
    expect(within(situacao).getByRole('button', { name: /Origem de Situação cadastral/ })).toBeInTheDocument();
    expect(within(tabela).getByRole('row', { name: /Índice de endividamento/ })).toHaveTextContent('não disponível — importe a EFD ICMS/IPI');
  });

  it('simulador do PR mostra antes e depois sem alterar o resultado atual', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ ...PR, valor: 18, classe: 'A', rotuloTexto: 'simulação' } as never);
    renderizar();
    const simulador = await screen.findByRole('region', { name: 'Simular ações nas CDAs' });
    await userEvent.click(within(simulador).getByRole('radio', { name: 'Quitar' }));
    await userEvent.click(within(simulador).getByRole('button', { name: 'Simular' }));
    expect(post).toHaveBeenCalledWith('/api/clientes/7/capacidade/pr/simulacao', { quitar: ['2023.0045678-2'], parcelar: [] });
    const resultadoSim = await within(simulador).findByLabelText('Resultado da simulação');
    expect(resultadoSim).toHaveTextContent('Antes');
    expect(resultadoSim).toHaveTextContent('17');
    expect(resultadoSim).toHaveTextContent('Depois (simulação)');
    expect(resultadoSim).toHaveTextContent('18');
    expect(within(resultadoSim).getByText('A')).toBeInTheDocument();
  });
});
