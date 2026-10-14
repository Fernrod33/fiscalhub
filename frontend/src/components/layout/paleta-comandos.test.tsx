import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { PaletaComandos } from './paleta-comandos';
import { chavesClientes } from '@/features/clientes/api';

// cmdk usa ResizeObserver e scrollIntoView, ausentes no jsdom
globalThis.ResizeObserver ??= class { observe() {} unobserve() {} disconnect() {} };
Element.prototype.scrollIntoView ??= () => {};

const qc = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity } } });
qc.setQueryData(chavesClientes.todos, [{ id: 7, razaoSocial: 'Metalúrgica Exemplo Ltda', cnpj: '11222333000181' }]);

function Cenario() {
  const [aberta, setAberta] = useState(false);
  return (
    <QueryClientProvider client={qc}>
      <MemoryRouter><PaletaComandos aberta={aberta} aoMudar={setAberta} /></MemoryRouter>
    </QueryClientProvider>
  );
}

describe('PaletaComandos', () => {
  it('abre com Ctrl+K e lista navegação e clientes', async () => {
    render(<Cenario />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    await userEvent.keyboard('{Control>}k{/Control}');

    expect(await screen.findByRole('dialog', { name: 'Paleta de comandos' })).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Buscar cliente, tela ou ação…')).toHaveFocus();
    expect(screen.getByText('Clientes', { selector: '[cmdk-item] *, [cmdk-item]' })).toBeInTheDocument();
    expect(screen.getByText('Metalúrgica Exemplo Ltda')).toBeInTheDocument();
  });
});
