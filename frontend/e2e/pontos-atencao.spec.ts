import { expect, test } from '@playwright/test';
import { ARQUIVOS_MASSA_COMPLETA as ARQUIVOS, criarCliente, criarEscritorioEEntrar, massa, semViolacoesGraves, API } from './apoio';

test('massa → painel com críticos → Pontos de atenção → R04 → evidência na célula de Tributos → descartar → Diagnóstico', async ({ page }) => {
  test.setTimeout(480_000);
  await criarEscritorioEEntrar(page);
  const cliente = await criarCliente(page, 'Metalurgica Pinheiral');
  await page.getByRole('link', { name: cliente }).click();
  const secoes = page.getByRole('navigation', { name: 'Seções do cliente' });
  await secoes.getByRole('link', { name: 'Importações', exact: true }).click();
  await page.locator('input[type=file]').setInputFiles(ARQUIVOS.map(massa));
  const api = API;
  await expect.poll(async () => {
    const r = await (await page.request.get(`${api}/api/documentos/resumo`)).json();
    return (r.PROCESSADO ?? 0) + (r.PROCESSADO_COM_ALERTAS ?? 0);
  }, { timeout: 300_000, intervals: [2_000] }).toBe(ARQUIVOS.length);

  // a reavaliação automática roda depois de cada importação; a sob demanda deixa o teste determinístico
  await secoes.getByRole('link', { name: 'Pontos de atenção', exact: true }).click();
  await page.getByRole('button', { name: 'Reavaliar' }).click();
  await expect(page.getByText(/Reavaliado: \d+ abertos/)).toBeVisible({ timeout: 60_000 });

  await page.getByRole('link', { name: 'Painel', exact: true }).first().click();
  const carteira = page.getByRole('table', { name: 'Carteira de clientes' });
  const linha = carteira.getByRole('row', { name: new RegExp(cliente) });
  await expect(linha).toBeVisible();
  await expect(linha.getByRole('cell').nth(1)).not.toHaveText('0');
  await semViolacoesGraves(page, 'painel do escritório');
  await linha.getByRole('link', { name: cliente }).click();

  const lista = page.getByRole('list', { name: 'Pontos de atenção' });
  await expect(lista).toBeVisible();
  await semViolacoesGraves(page, 'aba Pontos de atenção');
  await lista.getByRole('button', { name: /ICMS-ST não recolhido com saldo credor/ }).click();
  await page.getByRole('list', { name: /Evidências — ICMS-ST/ }).getByRole('link').first().click();
  await expect(page).toHaveURL(/\/tributos\?item=ICMS_ST-PR-2026-08/);
  await expect(page.getByRole('dialog')).toContainText('Apurado × declarado × pago');
  await page.keyboard.press('Escape');

  await secoes.getByRole('link', { name: 'Pontos de atenção', exact: true }).click();
  await page.getByRole('list', { name: 'Pontos de atenção' }).getByRole('button', { name: /ICMS-ST não recolhido com saldo credor/ }).click();
  await page.getByLabel('Novo estado').selectOption('DESCARTADO');
  await page.getByLabel(/Comentário/).fill('ST recolhido em guia avulsa, aguardando baixa');
  await page.getByRole('button', { name: 'Salvar' }).click();
  await expect(page.getByText('Ponto marcado como descartado.')).toBeVisible();

  await secoes.getByRole('link', { name: 'Relatórios', exact: true }).click();
  await page.getByRole('radio', { name: /^Diagnóstico fiscal/ }).check();
  await page.getByLabel('Incluir resolvidos e descartados').check();
  const previa = page.frameLocator('iframe[title="Prévia do relatório"]');
  await expect(previa.getByText('ST recolhido em guia avulsa', { exact: false })).toBeVisible({ timeout: 30_000 });
  const emitido = page.waitForResponse((r) => r.url().endsWith('/relatorios') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Emitir' }).click();
  const registro = await (await emitido).json();
  await expect(page.getByText(`Relatório nº ${registro.numero} emitido.`)).toBeVisible();
});
