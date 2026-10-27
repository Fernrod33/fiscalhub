import AxeBuilder from '@axe-core/playwright';
import path from 'node:path';
import { expect, test, type Page } from '@playwright/test';
import { criarCliente, criarEscritorioEEntrar } from './apoio';

/** Massa fictícia versionada no backend (a mesma do MassaFicticiaAceiteTest). */
const massa = (arquivo: string) => path.resolve(process.cwd(), '..', 'backend', 'src', 'test', 'resources', 'massa-ficticia', arquivo);
const ARQUIVOS = [
  'efd-contribuicoes/EFD_CONTRIBUICOES_082026_11222333000181.txt', 'efd-icms-ipi/EFD_ICMS_IPI_082026_11222333000181.txt',
  'situacao-fiscal/Relatorio_Situacao_Fiscal_11222333000181_20261014.pdf', 'pgfn/Extrato_Inscricoes_Regularize_11222333000181.pdf',
  'pgfn/CAPAG_11222333000181.pdf', 'dctfweb/DCTFWeb_082026_Declaracao_Completa_11222333000181.pdf',
  'pagamentos/DARF_DCTFWeb_082026_PIS_COFINS_IPI.pdf', 'pagamentos/Relatorio_Consolidado_Pagamentos_11222333000181.pdf',
  'parana/Conta_Corrente_e_Divida_Ativa_ICMS_9012345663.pdf', 'parana/Pagamentos_ICMS_GR-PR_9012345663.pdf',
];

async function semViolacoesGraves(page: Page, tela: string) {
  await page.waitForTimeout(600); // fim das animações de entrada
  const { violations } = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`), tela).toEqual([]);
}

test('massa fictícia → Tributos: COFINS 08/2026 paga a menor com a origem → Situação fiscal: inscrição impeditiva', async ({ page }) => {
  test.setTimeout(180_000);
  await criarEscritorioEEntrar(page);
  const cliente = await criarCliente(page, 'Metalurgica Pinheiral'); // CNPJ 11.222.333/0001-81, o da massa
  await page.getByRole('link', { name: cliente }).click();
  const secoes = page.getByRole('navigation', { name: 'Seções do cliente' });
  await secoes.getByRole('link', { name: 'Importações', exact: true }).click();

  await page.locator('input[type=file]').setInputFiles(ARQUIVOS.map(massa));
  const documentos = page.getByRole('table', { name: 'Documentos importados' });
  await expect(documentos.getByText(/^Processado/)).toHaveCount(ARQUIVOS.length, { timeout: 120_000 });
  for (const tipo of ['Situação fiscal', 'Regularize', 'CAPAG', 'DCTFWeb', 'DARF', 'Conta corrente PR', 'Relação GR-PR']) {
    await expect(documentos.getByText(tipo, { exact: true })).toBeVisible();
  }

  await secoes.getByRole('link', { name: 'Tributos', exact: true }).click();
  const matriz = page.getByRole('table', { name: 'Apurado, declarado e pago por tributo e competência' });
  const diferenca = matriz.getByRole('button', { name: /^COFINS em 08\/2026, Diferença: R\$\s?5\.079,00, Pago a menor/ });
  await expect(diferenca).toBeVisible();
  await expect(diferenca).toContainText('Pago a menor');
  await semViolacoesGraves(page, 'tributos com a conciliação');

  await diferenca.click();
  const gaveta = page.getByRole('dialog', { name: 'COFINS · 08/2026' });
  await expect(gaveta.getByText('Impede certidão')).toBeVisible(); // o débito da COFINS no relatório de situação fiscal
  await gaveta.getByRole('button', { name: /Origem de Pago — COFINS 08\/2026/ }).click();
  await expect(page.getByRole('link', { name: 'DARF_DCTFWeb_082026_PIS_COFINS_IPI.pdf' })).toBeVisible();
  const origemDoDarf = page.getByText('Página 1 · linha 13', { exact: true });
  await expect(origemDoDarf).toBeVisible();
  await page.keyboard.press('Escape'); // fecha o popover da origem
  await expect(origemDoDarf).toBeHidden();
  await page.keyboard.press('Escape'); // fecha a gaveta
  await expect(gaveta).toBeHidden();

  await secoes.getByRole('link', { name: 'Situação fiscal', exact: true }).click();
  const certidao = page.getByRole('alert');
  await expect(certidao).toContainText('90 3 24 002201-55');
  await expect(certidao).toContainText('sem negociação');
  await expect(page.getByRole('progressbar', { name: /Parcelas pagas do PR-2025-0045871: 12 de 36/ })).toBeVisible();
  await expect(page.getByLabel('Classe C')).toBeVisible();
  await semViolacoesGraves(page, 'situação fiscal com dados');

  await secoes.getByRole('link', { name: 'Visão geral', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Situação fiscal' })).toBeVisible();
  await semViolacoesGraves(page, 'visão geral com o resumo');
});
