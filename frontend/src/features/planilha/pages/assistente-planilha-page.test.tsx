import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http } from '@/lib/http';
import AssistentePlanilhaPage from './assistente-planilha-page';

vi.mock('@/components/motion/use-movimento-reduzido', () => ({ useMovimentoReduzido: () => true }));
// Radix Select usa APIs de ponteiro que o jsdom não tem
Element.prototype.hasPointerCapture ??= () => false;
Element.prototype.scrollIntoView ??= () => {};

const PREVIA = {
  abas: ['Balancete'], aba: 'Balancete', periodoInicio: null, periodoFim: null,
  linhas: [['Balancete de verificação'], ['Conta', 'Descrição', 'Saldo atual'], ['1.1.01', 'Caixa', '4000'], ['2.1', 'Fornecedores', '-4000']],
  sugestao: { reconhecido: false, linhaCabecalho: 1, colunas: null, planoReferencial: '1' },
};
const DOCUMENTO = { id: 5, clienteId: 3, nomeOriginalArquivo: 'balancete.xlsx', status: 'AGUARDANDO_MAPEAMENTO', periodoFim: null };

function renderizar() {
  vi.spyOn(http, 'get').mockImplementation((url: string) =>
    Promise.resolve(url.includes('/planilha') ? PREVIA : url.includes('/status') ? { status: 'RECEBIDO', etapa: 'RECEBIDO', progresso: 0 } : DOCUMENTO) as never);
  const post = vi.spyOn(http, 'post').mockResolvedValue(undefined);
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/importacoes/5/planilha']}>
        <Routes><Route path="/importacoes/:documentoId/planilha" element={<AssistentePlanilhaPage />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return post;
}

async function escolher(rotulo: RegExp, opcao: string) {
  await userEvent.click(screen.getByRole('combobox', { name: rotulo }));
  await userEvent.click(await screen.findByRole('option', { name: opcao }));
}

afterEach(() => vi.restoreAllMocks());

describe('Assistente do balancete', () => {
  it('só continua com os campos obrigatórios e envia colunas, cabeçalho e período (nenhum valor digitado)', async () => {
    const post = renderizar();
    const continuar = await screen.findByRole('button', { name: 'Continuar' });
    expect(continuar).toBeDisabled();

    await userEvent.click(screen.getByRole('radio', { name: '2' })); // linha 2 é o cabeçalho
    await escolher(/Código da conta/, 'A · Conta');
    await escolher(/Descrição da conta/, 'B · Descrição');
    await escolher(/^Saldo final/, 'C · Saldo atual');
    expect(continuar).toBeEnabled();
    await userEvent.click(continuar);

    await userEvent.type(await screen.findByLabelText('Início do período'), '2024-01-01');
    await userEvent.type(screen.getByLabelText('Fim do período'), '2024-12-31');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar e processar' }));

    expect(post).toHaveBeenCalledWith('/api/documentos/5/planilha/mapeamento', {
      aba: 'Balancete', linhaCabecalho: 2, colunas: { codigo: 0, descricao: 1, saldoFinal: 2 },
      periodoInicio: '2024-01-01', periodoFim: '2024-12-31', planoReferencial: '1',
    });
    expect(await screen.findByRole('list', { name: 'Etapas do processamento' })).toBeInTheDocument();
  });
});
