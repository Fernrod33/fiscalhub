import { expect, test } from '@playwright/test';
import { criarCliente, criarEscritorioEEntrar, semViolacoesGraves } from './apoio';

test('LGPD: exportar os dados do cliente em ZIP, agendar a exclusão com o CNPJ e cancelar', async ({ page }) => {
  await criarEscritorioEEntrar(page);
  const razao = await criarCliente(page, 'Cliente LGPD Ltda');

  await page.getByRole('link', { name: razao }).click();
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Exportar dados' }).click();
  expect((await download).suggestedFilename()).toMatch(/^fiscalhub-cliente-\d+-.*\.zip$/);

  await page.getByRole('navigation', { name: 'Menu principal' }).getByRole('link', { name: 'Clientes' }).click();
  await page.getByRole('button', { name: `Excluir ${razao}` }).click();
  const dialogo = page.getByRole('dialog');
  await expect(dialogo.getByText(/Fica agendada por 7 dias/)).toBeVisible();
  await dialogo.getByRole('button', { name: 'Continuar' }).click();
  await expect(dialogo.getByRole('button', { name: 'Agendar exclusão' })).toBeDisabled(); // sem o CNPJ, não agenda
  await dialogo.getByLabel(/CNPJ/).fill('11.222.333/0001-81');
  await dialogo.getByRole('button', { name: 'Agendar exclusão' }).click();
  await expect(page.getByText(/Exclusão em/)).toBeVisible();

  await page.getByRole('link', { name: razao }).click();
  await expect(page.getByText(/Exclusão agendada: todos os dados/)).toBeVisible();
  await semViolacoesGraves(page, 'cliente com exclusão agendada');
  await page.getByRole('button', { name: 'Cancelar exclusão' }).click();
  await expect(page.getByText(/Exclusão agendada: todos os dados/)).toBeHidden();
});
