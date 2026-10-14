import { expect, type Page } from '@playwright/test';

/** Cadastra um escritório novo (dados únicos por execução) e entra com o ADMIN dele. */
export async function criarEscritorioEEntrar(page: Page) {
  const sufixo = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  const email = `e2e-${sufixo}@teste.com`;
  const senha = 'senha-e2e-1';

  await page.goto('/cadastro');
  await page.getByLabel('Nome do escritório').fill(`Escritório E2E ${sufixo}`);
  await page.getByLabel('Seu nome').fill('Pessoa Teste');
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Senha').fill(senha);
  await page.getByRole('button', { name: 'Cadastrar' }).click();
  await expect(page).toHaveURL(/\/login$/);

  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Senha').fill(senha);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Olá, Pessoa' })).toBeVisible();
  return { email, senha };
}

/** Cadastra um cliente pela tela de clientes e devolve a razão social. */
export async function criarCliente(page: Page, razaoSocial = 'Cliente E2E Ltda') {
  await page.getByRole('navigation', { name: 'Menu principal' }).getByRole('link', { name: 'Clientes' }).click();
  await page.getByRole('button', { name: 'Novo cliente' }).first().click();
  const dialogo = page.getByRole('dialog');
  await dialogo.getByLabel('Razão social').fill(razaoSocial);
  await dialogo.getByLabel('CNPJ').fill('11.222.333/0001-81');
  await dialogo.getByRole('button', { name: 'Cadastrar cliente' }).click();
  await expect(page.getByRole('link', { name: razaoSocial })).toBeVisible();
  return razaoSocial;
}
