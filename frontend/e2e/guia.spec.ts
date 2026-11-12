import path from 'node:path';
import { expect, test, type Page } from '@playwright/test';
import { ARQUIVOS_MASSA_COMPLETA as ARQUIVOS, criarCliente, criarEscritorioEEntrar, massa } from './apoio';

/**
 * Capturas do guia do consultor (docs/guia), nos dois temas, a partir da massa fictícia: rodar de novo depois de mudar
 * uma tela mantém o guia em dia. Só pelo npm run guia (ou GUIA=1); fora disso o teste é pulado.
 */
const PASTA = path.resolve(process.cwd(), '..', 'docs', 'guia', 'img');

async function capturar(page: Page, nome: string) {
  for (const tema of ['light', 'dark'] as const) {
    await page.emulateMedia({ colorScheme: tema, reducedMotion: 'reduce' });
    await page.waitForTimeout(150); // a troca de tema é síncrona; só o repaint
    await page.screenshot({ path: path.join(PASTA, `${nome}-${tema === 'light' ? 'claro' : 'escuro'}.png`) });
  }
  await page.emulateMedia({ colorScheme: 'light' });
}

test('guia do consultor: do cadastro à proposta de regularização', async ({ page }) => {
  test.skip(!process.env.GUIA && process.env.npm_lifecycle_event !== 'guia', 'só pelo npm run guia (ou GUIA=1)');
  test.setTimeout(600_000);
  await page.setViewportSize({ width: 1366, height: 820 });

  await page.goto('/cadastro');
  await capturar(page, '01-cadastro');
  await criarEscritorioEEntrar(page);
  await capturar(page, '02-painel-vazio');

  const cliente = await criarCliente(page, 'Metalurgica Pinheiral');
  await capturar(page, '03-clientes');
  await page.getByRole('link', { name: cliente }).click();
  const secoes = page.getByRole('navigation', { name: 'Seções do cliente' });
  const aba = (nome: string) => secoes.getByRole('link', { name: nome, exact: true }).click();

  await aba('Importações');
  await page.locator('input[type=file]').setInputFiles(ARQUIVOS.map(massa));
  await expect.poll(async () => {
    const r = await (await page.request.get('/api/documentos/resumo')).json();
    return (r.PROCESSADO ?? 0) + (r.PROCESSADO_COM_ALERTAS ?? 0);
  }, { timeout: 400_000, intervals: [3_000] }).toBe(ARQUIVOS.length);
  await page.reload();
  await capturar(page, '04-importacoes');

  await aba('Visão geral');
  await expect(page.getByRole('region', { name: 'Capacidade de pagamento' })).toBeVisible({ timeout: 30_000 });
  await capturar(page, '05-visao-geral');
  await aba('Demonstrações');
  await expect(page.getByRole('table').first()).toBeVisible({ timeout: 30_000 });
  await capturar(page, '06-demonstracoes');
  await aba('Tributos');
  await expect(page.getByRole('table').first()).toBeVisible({ timeout: 30_000 });
  await capturar(page, '07-tributos');
  await aba('Situação fiscal');
  await expect(page.getByText(/certidão/i).first()).toBeVisible({ timeout: 30_000 });
  await capturar(page, '08-situacao-fiscal');
  await aba('Capacidade');
  await expect(page.getByRole('region', { name: 'PGFN — CAPAG' })).toBeVisible({ timeout: 30_000 });
  await capturar(page, '09-capacidade');
  await aba('Pontos de atenção');
  await expect(page.getByText(/Débito que impede a certidão/).first()).toBeVisible({ timeout: 60_000 });
  await capturar(page, '10-pontos-de-atencao');
  await aba('Simulações');
  await expect(page.getByRole('heading', { name: /Débitos/ }).first()).toBeVisible({ timeout: 30_000 });
  await capturar(page, '11-simulacoes');
  await aba('Relatórios');
  await expect(page.frameLocator('iframe[title="Prévia do relatório"]').locator('body')).toBeVisible({ timeout: 30_000 });
  await capturar(page, '12-relatorios');

  await page.getByRole('navigation', { name: 'Menu principal' }).getByRole('link', { name: 'Painel' }).click();
  await capturar(page, '13-painel');
});
