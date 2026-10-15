import path from 'node:path';
import { expect, test } from '@playwright/test';
import { criarCliente, criarEscritorioEEntrar } from './apoio';

/** Amostras sintéticas versionadas no backend (as mesmas dos testes de integração). */
const amostra = (nome: string) => path.resolve(process.cwd(), '..', 'backend', 'src', 'test', 'resources', 'amostras', nome);

test('ECD sintética → processado → Demonstrações → total do Ativo → origem do valor', async ({ page }) => {
  await criarEscritorioEEntrar(page);
  const cliente = await criarCliente(page, 'Empresa Exemplo Ltda'); // CNPJ 11.222.333/0001-81, o da ECD
  await page.getByRole('link', { name: cliente }).click();
  await page.getByRole('navigation', { name: 'Seções do cliente' }).getByRole('link', { name: 'Importações', exact: true }).click();

  await page.locator('input[type=file]').setInputFiles(amostra('ecd-minima-l9.txt'));
  await expect(page.getByRole('status').filter({ hasText: /^Pronto$/ })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByRole('table', { name: 'Documentos importados' }).getByText('Processado', { exact: true })).toBeVisible();

  await page.getByRole('navigation', { name: 'Seções do cliente' }).getByRole('link', { name: 'Demonstrações', exact: true }).click();
  const ativo = page.getByRole('button', { name: /Origem de ATIVO em 2021/ });
  await expect(ativo).toHaveText('6.000,00 D');

  await ativo.click();
  const contas = page.getByRole('list', { name: 'Contas que compõem o valor' });
  await expect(contas).toContainText('1.1.01 Caixa');
  await expect(contas).toContainText('I155 · linha 26');
  await expect(page.getByRole('link', { name: 'ecd-minima-l9.txt' })).toBeVisible();
});
