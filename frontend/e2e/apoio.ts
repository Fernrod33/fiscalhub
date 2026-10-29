import AxeBuilder from '@axe-core/playwright';
import path from 'node:path';
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

/** Massa fictícia versionada no backend (a mesma do MassaFicticiaAceiteTest). */
export const massa = (arquivo: string) => path.resolve(process.cwd(), '..', 'backend', 'src', 'test', 'resources', 'massa-ficticia', arquivo);
export const ARQUIVOS_MASSA = [
  'efd-contribuicoes/EFD_CONTRIBUICOES_082026_11222333000181.txt', 'efd-icms-ipi/EFD_ICMS_IPI_082026_11222333000181.txt',
  'situacao-fiscal/Relatorio_Situacao_Fiscal_11222333000181_20261014.pdf', 'pgfn/Extrato_Inscricoes_Regularize_11222333000181.pdf',
  'pgfn/CAPAG_11222333000181.pdf', 'dctfweb/DCTFWeb_082026_Declaracao_Completa_11222333000181.pdf',
  'pagamentos/DARF_DCTFWeb_082026_PIS_COFINS_IPI.pdf', 'pagamentos/Relatorio_Consolidado_Pagamentos_11222333000181.pdf',
  'parana/Conta_Corrente_e_Divida_Ativa_ICMS_9012345663.pdf', 'parana/Pagamentos_ICMS_GR-PR_9012345663.pdf',
];

/** A massa completa da Metalúrgica: ECD 2022-2025, ECF, EFD de 2025 e 2026, PGFN, PR (a mesma do MassaFicticiaAceiteTest). */
export const ARQUIVOS_MASSA_COMPLETA = [
  ...ARQUIVOS_MASSA,
  'ecd/ECD_2022_11222333000181_ORIGINAL.txt', 'ecd/ECD_2023_11222333000181_ORIGINAL.txt', 'ecd/ECD_2024_11222333000181_ORIGINAL.txt',
  'ecd/ECD_2024_11222333000181_SUBSTITUTA.txt', 'ecd/ECD_2025_11222333000181_ORIGINAL.txt', 'ecf/ECF_2025_LUCRO_REAL_11222333000181.txt',
  'parana/RTI_9012345663.pdf', 'parana/GR-PR_Parcelamento_parcela_12-36.pdf',
  ...Array.from({ length: 12 }, (_, i) => String(i + 1).padStart(2, '0')).flatMap((m) => [
    `efd-icms-ipi/EFD_ICMS_IPI_${m}2025_11222333000181.txt`, `efd-contribuicoes/EFD_CONTRIBUICOES_${m}2025_11222333000181.txt`]),
  ...Array.from({ length: 7 }, (_, i) => `efd-icms-ipi/EFD_ICMS_IPI_0${i + 1}2026_11222333000181.txt`),
];

export async function semViolacoesGraves(page: Page, tela: string) {
  await page.waitForTimeout(600); // fim das animações de entrada
  // iframes: false — a prévia do relatório é um iframe com sandbox (sem script), onde o axe não roda
  const { violations } = await new AxeBuilder({ page }).setLegacyMode().options({ iframes: false }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`), tela).toEqual([]);
}
