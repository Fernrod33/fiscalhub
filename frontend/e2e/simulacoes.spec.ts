import { expect, test } from '@playwright/test';
import { ARQUIVOS_MASSA_COMPLETA as ARQUIVOS, criarCliente, criarEscritorioEEntrar, massa, semViolacoesGraves } from './apoio';

/** A massa completa: a ECD dá a parcela sustentável do selo; o extrato e a CAPAG da PGFN, a inscrição de IPI e a classe C. */
test('massa → Simulações: inscrição de IPI, parcelamento × transação, selo e proposta em PDF', async ({ page }) => {
  test.setTimeout(480_000);
  await criarEscritorioEEntrar(page);
  const cliente = await criarCliente(page, 'Metalurgica Pinheiral');
  await page.getByRole('link', { name: cliente }).click();
  const secoes = page.getByRole('navigation', { name: 'Seções do cliente' });
  await secoes.getByRole('link', { name: 'Importações', exact: true }).click();
  await page.locator('input[type=file]').setInputFiles(ARQUIVOS.map(massa));
  const api = process.env.VITE_API_URL ?? 'http://localhost:8080';
  await expect.poll(async () => {
    const r = await (await page.request.get(`${api}/api/documentos/resumo`)).json();
    return (r.PROCESSADO ?? 0) + (r.PROCESSADO_COM_ALERTAS ?? 0);
  }, { timeout: 300_000, intervals: [2_000] }).toBe(ARQUIVOS.length);

  await secoes.getByRole('link', { name: 'Simulações', exact: true }).click();
  await page.getByRole('checkbox', { name: /IPI 90 3 24 002201-55/ }).check();
  await page.getByRole('button', { name: 'Simular' }).click();
  const pgfn = page.getByRole('region', { name: 'Modalidades — PGFN' });
  const transacao = pgfn.getByRole('button', { name: /Transação por adesão.*CAPAG oficial C/ });
  await expect(transacao).toBeVisible({ timeout: 60_000 });
  await expect(transacao).toContainText('recomendada');
  await expect(transacao).toContainText('Cabe na parcela sustentável');
  const parcelamento = pgfn.getByRole('button', { name: 'Parcelamento ordinário — PGFN' });
  await parcelamento.click();
  await expect(parcelamento).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('heading', { name: 'Parcelamento ordinário — PGFN' })).toBeVisible();
  await transacao.click();
  await expect(page.getByRole('figure', { name: /Saldo nominal × projetado — Transação/ })).toBeVisible();
  await semViolacoesGraves(page, 'aba Simulações');

  await page.getByRole('button', { name: 'Salvar simulação' }).click();
  await expect(page.getByText('Simulação salva.')).toBeVisible();
  await expect(page.getByRole('table', { name: 'Simulações salvas' })).toContainText('Transação por adesão');

  await secoes.getByRole('link', { name: 'Relatórios', exact: true }).click();
  await page.getByRole('radio', { name: /^Proposta de regularização/ }).check();
  const previa = page.frameLocator('iframe[title="Prévia do relatório"]');
  await expect(previa.getByText('PRÉVIA — sem número, não emitida')).toBeVisible({ timeout: 30_000 });
  await expect(previa.getByText('Notas de risco')).toBeVisible();
  const emitido = page.waitForResponse((r) => r.url().endsWith('/relatorios') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Emitir' }).click();
  const registro = await (await emitido).json();
  await expect(page.getByText(`Relatório nº ${registro.numero} emitido.`)).toBeVisible();
  await expect(page.getByRole('table', { name: 'Relatórios emitidos' })).toContainText('Proposta de regularização');
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: `Baixar PDF do relatório ${registro.numero}` }).click();
  expect((await download).suggestedFilename()).toMatch(/\.pdf$/);

  await secoes.getByRole('link', { name: 'Visão geral', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Melhor proposta salva' })).toContainText('Transação por adesão');
});
