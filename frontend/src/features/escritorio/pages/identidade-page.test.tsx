import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http } from '@/lib/http';
import { FormularioIdentidade } from './identidade-page';

vi.mock('@/components/motion/use-movimento-reduzido', () => ({ useMovimentoReduzido: () => true }));

const INICIAL = { escritorioId: 1, nome: 'Escritório Silva', nomeExibicao: '', temLogo: false, corPrincipal: '#0b4f6c',
  rodape: '', responsavelNome: '', responsavelRegistro: '' };

function renderizar() {
  render(<QueryClientProvider client={new QueryClient()}><FormularioIdentidade inicial={INICIAL} /></QueryClientProvider>);
}

describe('Configurações → Escritório', () => {
  it('cor sem contraste AA no branco bloqueia o salvar e sugere um ajuste que passa', async () => {
    const put = vi.spyOn(http, 'put').mockResolvedValue({ ...INICIAL, corPrincipal: '#000000' });
    renderizar();
    const cor = screen.getByLabelText('Cor principal');
    await userEvent.clear(cor);
    await userEvent.type(cor, '#ffd84d');
    expect(screen.getByText(/Sobre branco \(relatório\)/)).toHaveTextContent('abaixo do AA');
    expect(screen.getByRole('button', { name: 'Salvar' })).toBeDisabled();
    await userEvent.click(screen.getAllByRole('button', { name: /^Usar #/ })[0]);
    expect(screen.getByText(/Sobre branco \(relatório\)/)).toHaveTextContent('(AA)');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    expect(put).toHaveBeenCalledWith('/api/escritorio/identidade', expect.objectContaining({ corPrincipal: expect.stringMatching(/^#[0-9a-f]{6}$/) }));
  });

  it('prévia ao vivo do cabeçalho e do rodapé', async () => {
    renderizar();
    await userEvent.type(screen.getByLabelText('Nome de exibição'), 'Silva & Souza');
    await userEvent.type(screen.getByLabelText('Texto do rodapé'), 'OAB/PR 123');
    const previa = screen.getByLabelText('Prévia do cabeçalho e do rodapé');
    expect(previa).toHaveTextContent('Silva & Souza · OAB/PR 123');
  });
});
