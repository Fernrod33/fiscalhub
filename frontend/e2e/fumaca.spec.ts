import { expect, test } from '@playwright/test';
import { criarCliente, criarEscritorioEEntrar } from './apoio';

const ABAS = ['Visão geral', 'Importações', 'Demonstrações', 'Tributos', 'Capacidade', 'Simulações', 'Pontos de atenção', 'Relatórios'];

test('signup → login → cliente → abas → importação até o status final', async ({ page }) => {
  await criarEscritorioEEntrar(page);
  const cliente = await criarCliente(page);

  await page.getByRole('link', { name: cliente }).click();
  await expect(page.getByRole('heading', { level: 1, name: cliente })).toBeVisible();

  const abas = page.getByRole('navigation', { name: 'Seções do cliente' });
  for (const aba of ABAS) {
    await abas.getByRole('link', { name: aba, exact: true }).click();
    await expect(abas.getByRole('link', { name: aba, exact: true })).toHaveAttribute('aria-current', 'page');
  }

  await abas.getByRole('link', { name: 'Importações', exact: true }).click();
  await page.locator('input[type=file]').setInputFiles({
    name: 'sped.txt', mimeType: 'text/plain', buffer: Buffer.from(`|0000|EFD|teste-e2e-${Date.now()}|\n`),
  });

  const tabela = page.getByRole('table', { name: 'Documentos importados' });
  await expect(tabela.getByRole('row', { name: /sped\.txt/ })).toBeVisible();
  // o job roda em segundo plano; a tela acompanha por polling até o status final
  // (SPED ainda sem leitura: o arquivo fica guardado, com alerta)
  await expect(tabela.getByRole('row', { name: /sped\.txt/ }).getByText('Processado com alertas', { exact: true })).toBeVisible({ timeout: 20_000 });
});
