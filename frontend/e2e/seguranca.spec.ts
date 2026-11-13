import { expect, test } from '@playwright/test';
import { criarEscritorioEEntrar, semViolacoesGraves } from './apoio';

test('Segurança: ADMIN ativa o 2º fator no primeiro acesso, entra com código de recuperação e vê as sessões', async ({ page }) => {
  const { email, senha, recuperacao } = await criarEscritorioEEntrar(page);

  await page.getByRole('button', { name: /Menu do usuário/ }).click();
  await page.getByRole('menuitem', { name: 'Segurança' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Segurança' })).toBeVisible();
  await expect(page.getByText('Ativa', { exact: true })).toBeVisible();
  await expect(page.getByText('Este dispositivo')).toBeVisible();
  await semViolacoesGraves(page, 'segurança');

  // sair e entrar de novo: a senha leva à etapa do código; o de recuperação vale uma vez
  await page.getByRole('button', { name: /Menu do usuário/ }).click();
  await page.getByRole('menuitem', { name: 'Sair' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Senha').fill(senha);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Verificação em duas etapas' })).toBeVisible();
  await page.getByLabel('Código').fill(recuperacao[0]);
  await page.getByRole('button', { name: 'Entrar' }).click();
  // volta para a tela de onde saiu, já com a sessão aberta
  await expect(page.getByRole('heading', { level: 1, name: 'Segurança' })).toBeVisible();
  await expect(page.getByText('Este dispositivo')).toBeVisible();
});
