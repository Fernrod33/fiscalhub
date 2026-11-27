import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DataTable, type ColumnDef } from './data-table';

type Linha = { nome: string; valor: number };
const colunas: ColumnDef<Linha>[] = [
  { accessorKey: 'nome', header: 'Nome' },
  { accessorKey: 'valor', header: 'Valor' },
];
const dados: Linha[] = [{ nome: 'Beta', valor: 2 }, { nome: 'Alfa', valor: 10 }, { nome: 'Gama', valor: 1 }];

const nomes = () => within(screen.getByRole('table')).getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell')[0].textContent);

describe('DataTable', () => {
  it('ordena ao clicar no cabeçalho e marca aria-sort', async () => {
    render(<DataTable rotulo="Teste" colunas={colunas} dados={dados} vazio={<p>vazio</p>} />);

    await userEvent.click(screen.getByRole('button', { name: /nome/i }));
    expect(nomes()).toEqual(['Alfa', 'Beta', 'Gama']);
    expect(screen.getByRole('columnheader', { name: /nome/i })).toHaveAttribute('aria-sort', 'ascending');

    await userEvent.click(screen.getByRole('button', { name: /nome/i }));
    expect(nomes()).toEqual(['Gama', 'Beta', 'Alfa']);
  });

  it('filtra pelo texto da busca', async () => {
    render(<DataTable rotulo="Teste" colunas={colunas} dados={dados} filtro="Filtrar" vazio={<p>vazio</p>} />);
    await userEvent.type(screen.getByRole('textbox', { name: 'Filtrar' }), 'gam');
    expect(nomes()).toEqual(['Gama']);
  });

  it('mostra o estado vazio quando não há dados', () => {
    render(<DataTable rotulo="Teste" colunas={colunas} dados={[]} vazio={<p>Nenhum cliente ainda</p>} />);
    expect(screen.getByText('Nenhum cliente ainda')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('mostra skeleton e aria-busy durante a carga', () => {
    render(<DataTable rotulo="Teste" colunas={colunas} dados={undefined} carregando vazio={<p>vazio</p>} />);
    expect(screen.getByRole('table')).toHaveAttribute('aria-busy', 'true');
  });
});
