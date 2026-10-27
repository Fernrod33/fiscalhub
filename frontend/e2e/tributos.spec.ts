import AxeBuilder from '@axe-core/playwright';
import path from 'node:path';
import { expect, test } from '@playwright/test';
import { criarCliente, criarEscritorioEEntrar } from './apoio';

/** Amostras sintéticas versionadas no backend (as mesmas dos testes de integração). */
const amostra = (nome: string) => path.resolve(process.cwd(), '..', 'backend', 'src', 'test', 'resources', 'amostras', nome);

test('ECD + ECF + EFD do mesmo cliente → aba Tributos → célula → origem do valor', async ({ page }) => {
  await criarEscritorioEEntrar(page);
  const cliente = await criarCliente(page, 'Empresa Exemplo Ltda'); // CNPJ das amostras
  await page.getByRole('link', { name: cliente }).click();
  await page.getByRole('navigation', { name: 'Seções do cliente' }).getByRole('link', { name: 'Importações', exact: true }).click();

  await page.locator('input[type=file]').setInputFiles([amostra('ecd-minima-l9.txt'), amostra('ecf-real-2021.txt'),
    amostra('efd-contribuicoes-nc-2025-01.txt'), amostra('efd-icms-ipi-2025-01.txt')]);
  const documentos = page.getByRole('table', { name: 'Documentos importados' });
  await expect(documentos.getByText('Processado', { exact: true })).toHaveCount(4, { timeout: 60_000 });
  for (const tipo of ['ECD', 'ECF', 'EFD-Contribuições', 'EFD ICMS/IPI']) {
    await expect(documentos.getByText(tipo, { exact: true })).toBeVisible();
  }

  await page.getByRole('navigation', { name: 'Seções do cliente' }).getByRole('link', { name: 'Tributos', exact: true }).click();
  const matriz = page.getByRole('table', { name: 'Apurado, declarado e pago por tributo e competência' });
  await expect(matriz.getByRole('rowheader', { name: 'ICMS-ST · SP' })).toBeVisible();
  await page.waitForTimeout(600); // fim das animações de entrada
  const { violations } = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(violations.filter((v) => v.impact === 'serious' || v.impact === 'critical').map((v) => v.id), 'aba Tributos com dados').toEqual([]);

  await matriz.getByRole('button', { name: /ICMS · PR em 01\/2025, Apurado/ }).click();
  const gaveta = page.getByRole('dialog');
  await expect(gaveta.getByRole('heading', { name: 'Operações próprias' })).toBeVisible();
  await gaveta.getByRole('button', { name: /Origem de A recolher — Operações próprias/ }).click();
  await expect(page.getByText(/^Registro E110 · linha \d+$/)).toBeVisible();
  await expect(page.getByRole('link', { name: 'efd-icms-ipi-2025-01.txt' })).toBeVisible();
  await page.keyboard.press('Escape'); // fecha o popover da origem
  await expect(page.getByText(/^Registro E110 · linha \d+$/)).toBeHidden();
  await page.keyboard.press('Escape'); // fecha a gaveta
  await expect(gaveta).toBeHidden();

  // demonstração do lucro real da ECF: compensação de prejuízo
  const irpj = page.getByRole('region', { name: 'IRPJ', exact: true });
  await expect(irpj.getByRole('button', { name: /Compensação de prejuízos/ })).toContainText('1.500,00');
});
