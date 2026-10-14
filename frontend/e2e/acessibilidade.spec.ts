import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import path from 'node:path';
import { criarCliente, criarEscritorioEEntrar } from './apoio';

const amostra = (nome: string) => path.resolve(process.cwd(), '..', 'backend', 'src', 'test', 'resources', 'amostras', nome);

/** Violações sérias ou críticas de WCAG 2.x A/AA na tela atual, nos temas claro e escuro. */
async function verificar(page: Page, tela: string) {
  for (const tema of ['claro', 'escuro']) {
    await page.evaluate((t) => localStorage.setItem('fiscalhub.tema', JSON.stringify(t)), tema);
    await page.reload();
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(600); // fim das animações de entrada
    const { violations } = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    const graves = violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')
      .map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`);
    expect(graves, `${tela} (${tema})`).toEqual([]);
  }
}

test('login, clientes e área do cliente sem violações sérias de acessibilidade', async ({ page }) => {
  await page.goto('/login');
  await verificar(page, 'login');

  await criarEscritorioEEntrar(page);
  const cliente = await criarCliente(page);
  await verificar(page, 'clientes');

  // verificar() recarrega a página: espera a navegação (com view transition) terminar antes
  await page.getByRole('link', { name: cliente }).click();
  await expect(page).toHaveURL(/\/visao-geral$/);
  await verificar(page, 'área do cliente — visão geral');
  await page.getByRole('navigation', { name: 'Seções do cliente' }).getByRole('link', { name: 'Importações', exact: true }).click();
  await expect(page).toHaveURL(/\/importacoes$/);
  await verificar(page, 'área do cliente — importações');
});

test('central de importação, demonstrações e assistente da planilha sem violações sérias', async ({ page }) => {
  await criarEscritorioEEntrar(page);
  const cliente = await criarCliente(page, 'Empresa Exemplo Ltda');
  await page.getByRole('link', { name: cliente }).click();
  await page.getByRole('navigation', { name: 'Seções do cliente' }).getByRole('link', { name: 'Importações', exact: true }).click();
  await page.locator('input[type=file]').setInputFiles([amostra('ecd-minima-l9.txt'), amostra('balancete.xlsx')]);
  await expect(page.getByRole('link', { name: 'Mapear colunas e período' })).toBeVisible({ timeout: 30_000 });
  await verificar(page, 'importações com fila');

  await page.getByRole('navigation', { name: 'Seções do cliente' }).getByRole('link', { name: 'Demonstrações', exact: true }).click();
  await expect(page).toHaveURL(/\/demonstracoes$/);
  await expect(page.getByRole('table', { name: 'Balanço patrimonial' })).toBeVisible();
  await verificar(page, 'demonstrações');

  await page.getByRole('navigation', { name: 'Menu principal' }).getByRole('link', { name: 'Importações' }).click();
  await expect(page).toHaveURL(/\/importacoes$/);
  await verificar(page, 'central do escritório');
  await page.getByRole('link', { name: 'Mapear planilha balancete.xlsx' }).click();
  await expect(page).toHaveURL(/\/planilha$/);
  await expect(page.getByRole('button', { name: 'Continuar' })).toBeVisible();
  await verificar(page, 'assistente da planilha');
});
