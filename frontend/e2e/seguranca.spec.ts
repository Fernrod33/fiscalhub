import { createHmac } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { criarEscritorioEEntrar, semViolacoesGraves } from './apoio';

/** TOTP da RFC 6238 (SHA1, 6 dígitos, 30 s), como o aplicativo autenticador calcula. */
function totp(base32: string, passo = Math.floor(Date.now() / 30_000)) {
  const alfabeto = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const c of base32.replace(/\s/g, '')) bits += alfabeto.indexOf(c).toString(2).padStart(5, '0');
  const chave = Buffer.from(bits.match(/.{8}/g)!.map((b) => parseInt(b, 2)));
  const contador = Buffer.alloc(8);
  contador.writeBigUInt64BE(BigInt(passo));
  const h = createHmac('sha1', chave).update(contador).digest();
  const o = h[h.length - 1] & 0xf;
  return String((h.readUInt32BE(o) & 0x7fffffff) % 1_000_000).padStart(6, '0');
}

test('Segurança: ativar a verificação em duas etapas, entrar com código de recuperação e ver as sessões', async ({ page }) => {
  const { email, senha } = await criarEscritorioEEntrar(page);

  await page.getByRole('button', { name: /Menu do usuário/ }).click();
  await page.getByRole('menuitem', { name: 'Segurança' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Segurança' })).toBeVisible();

  await page.getByRole('button', { name: 'Configurar aplicativo' }).click();
  await expect(page.getByRole('img', { name: 'QR code para o aplicativo autenticador' })).toBeVisible();
  const chave = (await page.getByLabel('Chave para digitar no aplicativo').textContent())!;
  await page.getByLabel(/Digite o código de 6 dígitos/).fill(totp(chave));
  await page.getByRole('button', { name: 'Ativar' }).click();
  const codigos = page.getByRole('list', { name: 'Códigos de recuperação' }).getByRole('listitem');
  await expect(codigos).toHaveCount(10);
  const recuperacao = (await codigos.first().textContent())!;
  await page.getByRole('button', { name: 'Guardei os códigos' }).click();
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
  await page.getByLabel('Código').fill(recuperacao);
  await page.getByRole('button', { name: 'Entrar' }).click();
  // volta para a tela de onde saiu, já com a sessão aberta
  await expect(page.getByRole('heading', { level: 1, name: 'Segurança' })).toBeVisible();
  await expect(page.getByText('Este dispositivo')).toBeVisible();
});
