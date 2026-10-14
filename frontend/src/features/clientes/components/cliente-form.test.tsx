import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ClienteForm } from './cliente-form';
import { paraRequisicao } from '../cliente-esquema';

describe('ClienteForm', () => {
  it('exige razão social e CNPJ', async () => {
    const aoSalvar = vi.fn();
    render(<ClienteForm aoSalvar={aoSalvar} />);

    await userEvent.click(screen.getByRole('button', { name: 'Cadastrar cliente' }));

    expect(await screen.findByText('Informe a razão social.')).toBeInTheDocument();
    expect(screen.getByText('Informe o CNPJ.')).toBeInTheDocument();
    expect(aoSalvar).not.toHaveBeenCalled();
  });

  it('recusa CNPJ com dígito verificador errado, CEP e UF inválidos', async () => {
    render(<ClienteForm aoSalvar={vi.fn()} />);
    await userEvent.type(screen.getByLabelText('Razão social'), 'Teste Ltda');
    await userEvent.type(screen.getByLabelText('CNPJ'), '11.222.333/0001-82');
    await userEvent.type(screen.getByLabelText('CEP'), '123');
    await userEvent.type(screen.getByLabelText('UF'), 'P1');

    await userEvent.click(screen.getByRole('button', { name: 'Cadastrar cliente' }));

    expect(await screen.findByText('CNPJ inválido.')).toBeInTheDocument();
    expect(screen.getByText('CEP deve ter 8 números.')).toBeInTheDocument();
    expect(screen.getByText('Use a sigla com 2 letras.')).toBeInTheDocument();
  });

  it('envia os valores válidos; a requisição leva CNPJ e CEP só com dígitos', async () => {
    const aoSalvar = vi.fn();
    render(<ClienteForm aoSalvar={aoSalvar} />);
    await userEvent.type(screen.getByLabelText('Razão social'), 'Metalúrgica Teste Ltda');
    await userEvent.type(screen.getByLabelText('CNPJ'), '11.222.333/0001-81');
    await userEvent.type(screen.getByLabelText('CEP'), '80010-000');
    await userEvent.type(screen.getByLabelText('UF'), 'pr');

    await userEvent.click(screen.getByRole('button', { name: 'Cadastrar cliente' }));

    expect(aoSalvar).toHaveBeenCalledOnce();
    const valores = aoSalvar.mock.calls[0][0];
    expect(valores.estado).toBe('PR');
    expect(paraRequisicao(valores)).toMatchObject({ cnpj: '11222333000181', cep: '80010000', razaoSocial: 'Metalúrgica Teste Ltda' });
  });

  it('na edição, preenche os dados do cliente e muda o rótulo do botão', () => {
    render(<ClienteForm aoSalvar={vi.fn()} cliente={{
      id: 1, razaoSocial: 'Existente SA', nomeFantasia: '', cnpj: '11222333000181', inscricaoEstadual: '', inscricaoMunicipal: '',
      endereco: '', cidade: 'Curitiba', estado: 'PR', cep: '', telefone: '', ativa: true, dataCriacao: '', dataAtualizacao: '',
    }} />);
    expect(screen.getByLabelText('Razão social')).toHaveValue('Existente SA');
    expect(screen.getByRole('button', { name: 'Salvar alterações' })).toBeInTheDocument();
  });
});
