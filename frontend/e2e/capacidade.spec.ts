import { expect, test } from '@playwright/test';
import { ARQUIVOS_MASSA, criarCliente, criarEscritorioEEntrar, massa, semViolacoesGraves } from './apoio';

/** A massa completa da Metalúrgica: ECD 2022-2025, ECF, EFD de 2025 e 2026, PGFN, PR (a mesma do MassaFicticiaAceiteTest). */
const ARQUIVOS = [
  ...ARQUIVOS_MASSA,
  'ecd/ECD_2022_11222333000181_ORIGINAL.txt', 'ecd/ECD_2023_11222333000181_ORIGINAL.txt', 'ecd/ECD_2024_11222333000181_ORIGINAL.txt',
  'ecd/ECD_2024_11222333000181_SUBSTITUTA.txt', 'ecd/ECD_2025_11222333000181_ORIGINAL.txt', 'ecf/ECF_2025_LUCRO_REAL_11222333000181.txt',
  'parana/RTI_9012345663.pdf', 'parana/GR-PR_Parcelamento_parcela_12-36.pdf',
  ...Array.from({ length: 12 }, (_, i) => String(i + 1).padStart(2, '0')).flatMap((m) => [
    `efd-icms-ipi/EFD_ICMS_IPI_${m}2025_11222333000181.txt`, `efd-contribuicoes/EFD_CONTRIBUICOES_${m}2025_11222333000181.txt`]),
  ...Array.from({ length: 7 }, (_, i) => `efd-icms-ipi/EFD_ICMS_IPI_0${i + 1}2026_11222333000181.txt`),
];

test('massa → Capacidade: CAPAG oficial C × estimada, simulação no PR e emissão do relatório e do dossiê', async ({ page }) => {
  test.setTimeout(420_000);
  await criarEscritorioEEntrar(page);
  const cliente = await criarCliente(page, 'Metalurgica Pinheiral');
  await page.getByRole('link', { name: cliente }).click();
  const secoes = page.getByRole('navigation', { name: 'Seções do cliente' });
  await secoes.getByRole('link', { name: 'Importações', exact: true }).click();
  await page.locator('input[type=file]').setInputFiles(ARQUIVOS.map(massa));
  // a tabela pagina em 20: espera pelo resumo da API (mesma sessão) até todos terminarem
  const api = process.env.VITE_API_URL ?? 'http://localhost:8080';
  await expect.poll(async () => {
    const r = await (await page.request.get(`${api}/api/documentos/resumo`)).json();
    return (r.PROCESSADO ?? 0) + (r.PROCESSADO_COM_ALERTAS ?? 0);
  }, { timeout: 300_000, intervals: [2_000] }).toBe(ARQUIVOS.length);

  await secoes.getByRole('link', { name: 'Capacidade', exact: true }).click();
  const pgfn = page.getByRole('region', { name: 'PGFN — CAPAG' });
  await expect(pgfn.getByLabel('Classe C')).toBeVisible({ timeout: 30_000 });
  await expect(pgfn.getByText('Estimativa', { exact: true })).toBeVisible();
  await expect(pgfn.getByText('Oficial', { exact: true })).toBeVisible();
  await semViolacoesGraves(page, 'aba Capacidade');

  const pr = page.getByRole('region', { name: 'Paraná — PGE/SEFA' });
  await expect(pr.getByLabel('Classe B')).toBeVisible();
  const simulador = pr.getByRole('region', { name: 'Simular ações nas CDAs' });
  await simulador.getByRole('group', { name: 'Ação na CDA 2023.0045678-2' }).getByRole('radio', { name: 'Quitar' }).check();
  await simulador.getByRole('button', { name: 'Simular' }).click();
  const resultado = simulador.getByLabel('Resultado da simulação');
  await expect(resultado).toContainText('Depois (simulação)');
  await expect(resultado.getByText('A', { exact: true })).toBeVisible();
  await expect(pr.getByLabel('Classe B')).toBeVisible(); // o importado não muda

  await secoes.getByRole('link', { name: 'Relatórios', exact: true }).click();
  for (const tipo of ['Capacidade de pagamento', 'Dossiê da revisão da CAPAG']) {
    await page.getByRole('radio', { name: new RegExp(`^${tipo}`) }).check();
    const previa = page.frameLocator('iframe[title="Prévia do relatório"]');
    await expect(previa.getByText('PRÉVIA — sem número, não emitida')).toBeVisible({ timeout: 30_000 });
    const emitido = page.waitForResponse((r) => r.url().endsWith('/relatorios') && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Emitir' }).click();
    const registro = await (await emitido).json();
    await expect(page.getByText(`Relatório nº ${registro.numero} emitido.`)).toBeVisible();
  }
  const lista = page.getByRole('table', { name: 'Relatórios emitidos' });
  await expect(lista).toContainText('Capacidade de pagamento');
  await expect(lista).toContainText('Dossiê da revisão da CAPAG');

  await secoes.getByRole('link', { name: 'Visão geral', exact: true }).click();
  const resumo = page.getByRole('region', { name: 'Capacidade de pagamento' });
  await expect(resumo).toContainText('CAPAG oficial');
  await expect(resumo).toContainText('Classe PR');
  await semViolacoesGraves(page, 'visão geral com a capacidade');
});
