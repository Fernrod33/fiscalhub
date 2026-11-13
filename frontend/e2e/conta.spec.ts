import { expect, test } from '@playwright/test';
import { confirmarIdentidade, criarEscritorioEEntrar, entrar, semViolacoesGraves } from './apoio';

test('Reautenticação: mudar papel e desativar usuário pedem senha e código; cancelar não altera nada', async ({ page }) => {
  const conta = await criarEscritorioEEntrar(page);
  await page.goto('/configuracoes/usuarios');
  await page.getByRole('button', { name: 'Novo usuário' }).click();
  const novo = page.getByRole('dialog', { name: 'Novo usuário' });
  await novo.getByLabel('Nome').fill('Pessoa Assistente');
  await novo.getByLabel('E-mail').fill(`assist-${Date.now()}@teste.com`);
  await novo.getByLabel('Senha provisória').fill('provisoria-e2e-forte');
  await novo.getByRole('button', { name: 'Criar usuário' }).click();
  await expect(novo).toBeHidden();

  // cancelar a confirmação: o papel continua o mesmo
  await page.getByRole('combobox', { name: 'Papel de Pessoa Assistente' }).click();
  await page.getByRole('option', { name: 'Assistente' }).click();
  const reauth = page.getByRole('dialog', { name: 'Confirme que é você' });
  await expect(reauth).toBeVisible();
  await semViolacoesGraves(page, 'reautenticação');
  await reauth.getByRole('button', { name: 'Cancelar' }).click();
  await expect(page.getByText('Ação cancelada: a senha não foi confirmada.')).toBeVisible();
  await expect(page.getByRole('combobox', { name: 'Papel de Pessoa Assistente' })).toHaveText('Consultor');

  // senha errada fica no diálogo; a certa repete a ação
  await page.getByRole('combobox', { name: 'Papel de Pessoa Assistente' }).click();
  await page.getByRole('option', { name: 'Assistente' }).click();
  await reauth.getByLabel('Senha').fill('senha-errada-123');
  await reauth.getByLabel('Código').fill('000000');
  await reauth.getByRole('button', { name: 'Confirmar' }).click();
  await expect(reauth.getByRole('alert')).toBeVisible();
  await confirmarIdentidade(page, conta);
  await expect(page.getByText('Papel alterado.')).toBeVisible();
  await expect(page.getByRole('combobox', { name: 'Papel de Pessoa Assistente' })).toHaveText('Assistente');

  // a confirmação vale 5 minutos nesta sessão: desativar logo em seguida não pede de novo
  await page.getByRole('row', { name: /Pessoa Assistente/ }).getByRole('button', { name: 'Desativar' }).click();
  await page.getByRole('dialog', { name: 'Desativar usuário' }).getByRole('button', { name: 'Desativar' }).click();
  await expect(page.getByText('Usuário desativado.')).toBeVisible();
  await expect(reauth).toBeHidden();
});

test('Inatividade: aviso 2 minutos antes, continuar mantém a sessão; sem resposta, volta ao login e depois à mesma tela', async ({ page }) => {
  await page.clock.install();
  const conta = await criarEscritorioEEntrar(page);
  await page.getByRole('navigation', { name: 'Menu principal' }).getByRole('link', { name: 'Clientes' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Clientes' })).toBeVisible();

  // 30 min no servidor, que grava o último uso de minuto em minuto: o aviso sai a partir dos 27 min sem requisição
  await page.clock.fastForward('27:30');
  const aviso = page.getByRole('dialog', { name: 'A sessão vai expirar' });
  await expect(aviso).toBeVisible();
  await semViolacoesGraves(page, 'aviso de inatividade');
  await aviso.getByRole('button', { name: 'Continuar conectado' }).click();
  await expect(aviso).toBeHidden();

  await page.clock.fastForward('27:30');
  await expect(aviso).toBeVisible();
  await page.clock.fastForward('02:00');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByText('A sessão foi encerrada por inatividade.', { exact: false })).toBeVisible();
  await entrar(page, conta);
  await expect(page.getByRole('heading', { level: 1, name: 'Clientes' })).toBeVisible();
});
