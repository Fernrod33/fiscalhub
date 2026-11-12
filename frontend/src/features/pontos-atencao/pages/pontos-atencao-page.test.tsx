import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http } from '@/lib/http';
import PontosAtencaoPage from './pontos-atencao-page';
import PainelPage from '@/features/painel/pages/painel-page';

vi.mock('@/components/motion/use-movimento-reduzido', () => ({ useMovimentoReduzido: () => true }));
vi.mock('@/features/auth/permissoes', () => ({ usePermissoes: () => ({ alterar: true, admin: false }) }));
vi.mock('@/features/auth/sessao', () => ({ useSessao: () => ({ nome: 'Pessoa Teste' }) }));

const ponto = (id: number, regra: string, severidade: string, titulo: string, extra: object = {}) => ({
  id, regra, versaoRegra: `${regra}@2026-10-30`, severidade, categoria: 'CONFORMIDADE', titulo, descricao: `${titulo}: descrição`,
  fundamento: 'Lei 9.430/1996, art. 61', recomendacao: 'Recolher a diferença', valor: 1000 * id, estado: 'NOVO',
  detectadoEm: '2026-10-30T10:00:00', confirmadoEm: '2026-10-30T10:00:00', historico: [],
  evidencias: [{ tipo: 'APURADO', recursoId: 'X', descricao: 'Apurado', valor: 33079, link: 'tributos?item=COFINS-2026-08',
    origem: { documentoId: 1, arquivo: 'EFD.txt', tipoDocumento: 'EFD', pagina: null, registro: 'M600', linha: 12, trecho: null } }],
  ...extra,
});
// na ordem de prioridade que o backend devolve (severidade, valor, data)
const PONTOS = [
  ponto(2, 'R07', 'CRITICA', 'Débito que impede a certidão: PGFN IPI', { categoria: 'RISCO' }),
  ponto(1, 'R01', 'ALTA', 'Pago a menor: COFINS 08/2026'),
  ponto(3, 'R12', 'INFO', 'Prejuízo fiscal acumulado', { categoria: 'OPORTUNIDADE', valor: null }),
];

function renderizar(caminho: string, elemento: React.ReactNode, rota: string) {
  vi.spyOn(http, 'get').mockImplementation((url: string) => Promise.resolve((
    url.endsWith('/pontos-atencao') ? PONTOS
      : url.endsWith('/nao-avaliadas') ? [{ codigo: 'R13', titulo: 'Créditos de PIS/COFINS', falta: 'falta importar a EFD-Contribuições' }]
        : /\/pontos-atencao\/\d+$/.test(url) ? { ...PONTOS[1], historico: [{ de: null, para: 'NOVO', comentario: 'detectado', usuario: 'sistema', em: '2026-10-30T10:00:00' }] }
          : url.endsWith('/resumo') && url.includes('pontos-atencao') ? { clientes: [{ clienteId: 7, razaoSocial: 'Metalúrgica', cnpj: '11222333000181',
            criticos: 2, altos: 3, medios: 1, info: 0, importacoesPendentes: 1, importacoesComErro: 0, avaliadoEm: '2026-10-30T10:00:00' }],
            simulacoesRecentes: [{ id: 1, clienteId: 7, razaoSocial: 'Metalúrgica', criadaEm: '2026-10-30T10:00:00', recomendacao: 'PGFN: Transação', custo: 60873.36 }] }
            : url.includes('/api/documentos') ? (url.includes('resumo') ? {} : { content: [] })
              : url.endsWith('/api/clientes') || url.startsWith('/api/sistema') ? [] : null) as never));
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[caminho]}><Routes><Route path={rota} element={elemento} /></Routes></MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('aba Pontos de atenção', () => {
  it('lista priorizada com severidade em texto, filtros e regras não avaliadas', async () => {
    renderizar('/clientes/7/pontos-de-atencao', <PontosAtencaoPage />, '/clientes/:clienteId/pontos-de-atencao');
    const lista = await screen.findByRole('list', { name: 'Pontos de atenção' });
    expect(within(lista).getAllByRole('button').map((b) => b.textContent)).toEqual([
      expect.stringContaining('Débito que impede'), expect.stringContaining('Pago a menor'), expect.stringContaining('Prejuízo fiscal')]);
    expect(within(lista).getByText('Crítica')).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('Severidade'), 'ALTA');
    expect(within(screen.getByRole('list', { name: 'Pontos de atenção' })).getAllByRole('button', { expanded: false })).toHaveLength(1);
    expect(screen.getByRole('list', { name: 'Regras não avaliadas' })).toHaveTextContent('falta importar a EFD-Contribuições');
  });

  it('expande com fundamento, recomendação e evidência com link; descartar exige justificativa', async () => {
    const patch = vi.spyOn(http, 'patch').mockResolvedValue({ ...PONTOS[1], estado: 'DESCARTADO' } as never);
    renderizar('/clientes/7/pontos-de-atencao', <PontosAtencaoPage />, '/clientes/:clienteId/pontos-de-atencao');
    await userEvent.click(await screen.findByRole('button', { name: /Pago a menor/ }));
    expect(screen.getByText(/Por que importa:/).parentElement).toHaveTextContent('Lei 9.430/1996, art. 61');
    expect(screen.getByRole('link', { name: 'Apurado' })).toHaveAttribute('href', '/clientes/7/tributos?item=COFINS-2026-08');
    expect(screen.getByRole('button', { name: /Origem de Apurado/ })).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('Novo estado'), 'DESCARTADO');
    expect(screen.getByRole('button', { name: 'Salvar' })).toBeDisabled();
    await userEvent.type(screen.getByLabelText(/Comentário/), 'já pago');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    expect(patch).toHaveBeenCalledWith('/api/clientes/7/pontos-atencao/1/estado', { estado: 'DESCARTADO', comentario: 'já pago' });
    expect(await screen.findByRole('list', { name: 'Histórico de estados' })).toHaveTextContent('detectado');
  });
});

describe('painel do escritório', () => {
  it('carteira com achados críticos e altos e as simulações recentes', async () => {
    renderizar('/', <PainelPage />, '/');
    const carteira = await screen.findByRole('table', { name: 'Carteira de clientes' });
    const linha = await within(carteira).findByRole('row', { name: /Metalúrgica/ });
    expect(linha).toHaveTextContent('2');
    expect(within(linha).getByRole('link', { name: 'Metalúrgica' })).toHaveAttribute('href', '/clientes/7/pontos-de-atencao');
    expect(screen.getByRole('table', { name: 'Simulações recentes' })).toHaveTextContent('PGFN: Transação');
  });
});
