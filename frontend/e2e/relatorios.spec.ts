import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { expect, test, type Download } from '@playwright/test';
import { ARQUIVOS_MASSA, criarCliente, criarEscritorioEEntrar, massa, semViolacoesGraves } from './apoio';

const sha256 = async (d: Download) => createHash('sha256').update(await readFile((await d.path())!)).digest('hex');

test('massa → Relatórios: prévia, emissão do relatório de tributos, PDF e Excel com a divergência da COFINS 08/2026', async ({ page }) => {
  test.setTimeout(180_000);
  await criarEscritorioEEntrar(page);
  const cliente = await criarCliente(page, 'Metalurgica Pinheiral'); // CNPJ da massa
  await page.getByRole('link', { name: cliente }).click();
  const secoes = page.getByRole('navigation', { name: 'Seções do cliente' });
  await secoes.getByRole('link', { name: 'Importações', exact: true }).click();
  await page.locator('input[type=file]').setInputFiles(ARQUIVOS_MASSA.map(massa));
  await expect(page.getByRole('table', { name: 'Documentos importados' }).getByText(/^Processado/))
    .toHaveCount(ARQUIVOS_MASSA.length, { timeout: 120_000 });

  // gráfico apurado × pago acima da matriz (tabela acessível com os valores formatados)
  await secoes.getByRole('link', { name: 'Tributos', exact: true }).click();
  await expect(page.getByRole('figure', { name: 'Apurado × pago por competência' })).toBeVisible();

  await secoes.getByRole('link', { name: 'Relatórios', exact: true }).click();
  await page.getByLabel('Competência inicial').fill('2026-08');
  await page.getByLabel('Competência final').fill('2026-08');
  const previa = page.frameLocator('iframe[title="Prévia do relatório"]');
  await expect(previa.getByText('PRÉVIA — sem número, não emitida')).toBeVisible({ timeout: 30_000 });
  await expect(previa.getByText('competências 08/2026 a 08/2026')).toBeVisible();
  const divergencia = previa.getByRole('row').filter({ hasText: 'Pago a menor' }).filter({ hasText: 'COFINS' });
  await expect(divergencia.first()).toContainText('5.079,00');
  await semViolacoesGraves(page, 'aba Relatórios com a prévia');

  const emitido = page.waitForResponse((r) => r.url().endsWith('/relatorios') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Emitir' }).click();
  const registro = await (await emitido).json();
  await expect(page.getByText(`Relatório nº ${registro.numero} emitido.`)).toBeVisible();
  const lista = page.getByRole('table', { name: 'Relatórios emitidos' });
  await expect(lista.getByRole('row').filter({ hasText: String(registro.numero).padStart(6, '0') })).toContainText('Tributos apurados');

  // o arquivo baixado é o gravado na emissão (o conteúdo do PDF e do Excel é conferido no MassaFicticiaAceiteTest)
  const pdf = page.waitForEvent('download');
  await page.getByRole('button', { name: `Baixar PDF do relatório ${registro.numero}` }).click();
  const arquivoPdf = await pdf;
  expect(arquivoPdf.suggestedFilename()).toBe(`relatorio-${String(registro.numero).padStart(6, '0')}-tributos.pdf`);
  expect(await sha256(arquivoPdf)).toBe(registro.sha256Pdf);
  expect((await readFile((await arquivoPdf.path())!)).subarray(0, 5).toString()).toBe('%PDF-');

  const xlsx = page.waitForEvent('download');
  await page.getByRole('button', { name: `Baixar Excel do relatório ${registro.numero}` }).click();
  const arquivoXlsx = await xlsx;
  expect(await sha256(arquivoXlsx)).toBe(registro.sha256Xlsx);
  expect((await readFile((await arquivoXlsx.path())!)).subarray(0, 2).toString()).toBe('PK');
  await semViolacoesGraves(page, 'aba Relatórios com o histórico');

  // visão geral: carga tributária efetiva (a massa não tem os 12 meses de EFD: "não disponível", sem estimativa)
  await secoes.getByRole('link', { name: 'Visão geral', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Carga tributária efetiva' })).toBeVisible();
  await expect(page.getByLabel('Exercícios sem carga calculada')).toContainText('2026 não disponível');
});

test('Configurações → Escritório: contraste da cor e prévia ao vivo', async ({ page }) => {
  await criarEscritorioEEntrar(page);
  await page.goto('/configuracoes/escritorio');
  await page.getByLabel('Nome de exibição').fill('Silva & Souza Advogados');
  await page.getByLabel('Texto do rodapé').fill('OAB/PR 12.345');
  const previa = page.getByLabel('Prévia do cabeçalho e do rodapé');
  await expect(previa).toContainText('Silva & Souza Advogados · OAB/PR 12.345');
  await page.getByLabel('Cor principal').fill('#ffd84d');
  await expect(page.getByRole('button', { name: 'Salvar' })).toBeDisabled();
  await page.getByRole('button', { name: /^Usar #/ }).first().click();
  await page.getByRole('button', { name: 'Salvar' }).click();
  await expect(page.getByText('Identidade do escritório salva.')).toBeVisible();
  await semViolacoesGraves(page, 'Configurações → Escritório');
});
