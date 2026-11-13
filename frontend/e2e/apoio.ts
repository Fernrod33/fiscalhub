import AxeBuilder from '@axe-core/playwright';
import { createHmac } from 'node:crypto';
import path from 'node:path';
import { expect, type Page } from '@playwright/test';

/**
 * Base da API para o page.request: com E2E_URL (proxy, CI) a mesma origem da página; sem ela, o backend local do Vite.
 * Nunca a porta 8080 fixa: atrás do proxy ela nem existe.
 */
export const API = process.env.E2E_URL ?? process.env.VITE_API_URL ?? 'http://localhost:8080';

/** TOTP da RFC 6238 (SHA1, 6 dígitos, 30 s), como o aplicativo autenticador calcula. */
export function totp(base32: string, passo = Math.floor(Date.now() / 30_000)) {
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

/**
 * Código ainda não usado: o servidor recusa repetir o passo de 30 s já aceito e aceita o vizinho (folga de relógio),
 * então cada chamada usa o passo seguinte ao último, esperando quando já está um à frente.
 */
const ultimoPasso = new Map<string, number>();
export async function codigoNovo(chave: string) {
  const passo = Math.max(Math.floor(Date.now() / 30_000), (ultimoPasso.get(chave) ?? 0) + 1);
  while (passo > Math.floor(Date.now() / 30_000) + 1) await new Promise((r) => setTimeout(r, 1000));
  ultimoPasso.set(chave, passo);
  return totp(chave, passo);
}

export type Conta = { email: string; senha: string; chave: string; recuperacao: string[] };

/** Login pela tela: senha e, na etapa seguinte, o código do aplicativo. */
export async function entrar(page: Page, conta: Conta) {
  await page.getByLabel('E-mail').fill(conta.email);
  await page.getByLabel('Senha').fill(conta.senha);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Verificação em duas etapas' })).toBeVisible({ timeout: 60_000 });
  await page.getByLabel('Código').fill(await codigoNovo(conta.chave));
  await page.getByRole('button', { name: 'Entrar' }).click();
}

/** O diálogo de reautenticação das ações destrutivas (senha e código). */
export async function confirmarIdentidade(page: Page, conta: Conta) {
  const dialogo = page.getByRole('dialog', { name: 'Confirme que é você' });
  await dialogo.getByLabel('Senha').fill(conta.senha);
  await dialogo.getByLabel('Código').fill(await codigoNovo(conta.chave));
  await dialogo.getByRole('button', { name: 'Confirmar' }).click();
  await expect(dialogo).toBeHidden();
}

/** Ativa o 2º fator na tela de ativação ou de segurança; devolve a chave e os códigos de recuperação. */
export async function ativarSegundoFator(page: Page) {
  await page.getByRole('button', { name: 'Configurar aplicativo' }).click();
  await expect(page.getByRole('img', { name: 'QR code para o aplicativo autenticador' })).toBeVisible();
  const chave = (await page.getByLabel('Chave para digitar no aplicativo').textContent())!.replace(/\s/g, '');
  await page.getByLabel(/Digite o código de 6 dígitos/).fill(await codigoNovo(chave));
  await page.getByRole('button', { name: 'Ativar' }).click();
  const itens = page.getByRole('list', { name: 'Códigos de recuperação' }).getByRole('listitem');
  await expect(itens).toHaveCount(10);
  const recuperacao = await itens.allTextContents();
  await page.getByRole('button', { name: 'Guardei os códigos' }).click();
  return { chave, recuperacao };
}

/**
 * Cadastra um escritório novo (dados únicos por execução) e entra com o ADMIN dele: no primeiro acesso, a ativação
 * obrigatória do 2º fator e o aceite dos termos. Devolve a conta (com a chave TOTP) para logins e reautenticações.
 */
export async function criarEscritorioEEntrar(page: Page) {
  const sufixo = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  const email = `e2e-${sufixo}@teste.com`;
  const senha = 'senha-e2e-forte-1';

  await page.goto('/cadastro');
  await page.getByLabel('Nome do escritório').fill(`Escritório E2E ${sufixo}`);
  await page.getByLabel('Seu nome').fill('Pessoa Teste');
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Senha').fill(senha);
  // espera a resposta da API (o hash da senha é lento com a suíte em paralelo), não um tempo fixo
  const cadastro = page.waitForResponse((r) => r.url().endsWith('/api/auth/signup') && r.request().method() === 'POST', { timeout: 60_000 });
  await page.getByRole('button', { name: 'Cadastrar' }).click();
  expect((await cadastro).status(), 'cadastro do escritório').toBe(200);
  await expect(page).toHaveURL(/\/login$/);

  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Senha').fill(senha);
  const login = page.waitForResponse((r) => r.url().includes('/api/auth/') && r.request().method() === 'POST', { timeout: 60_000 });
  await page.getByRole('button', { name: 'Entrar' }).click();
  expect((await login).ok(), 'login').toBe(true);
  // ADMIN sem o 2º fator: só a ativação até ativar
  await expect(page.getByRole('heading', { name: 'Ative a verificação em duas etapas' })).toBeVisible();
  const { chave, recuperacao } = await ativarSegundoFator(page);
  // primeiro acesso: aceite dos Termos de Uso e da Política de Privacidade
  await expect(page.getByRole('heading', { level: 1, name: 'Termos de Uso e Política de Privacidade' })).toBeVisible();
  await page.getByLabel('Li e aceito os Termos de Uso e a Política de Privacidade.').check();
  await page.getByRole('button', { name: 'Aceitar e continuar' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Olá, Pessoa' })).toBeVisible();
  return { email, senha, chave, recuperacao } satisfies Conta;
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
    `efd-icms-ipi/EFD_ICMS_IPI_${m}2025_11222333000181.txt`, `efd-contribuicoes/EFD_CONTRIBUICOES_${m}2025_11222333000181.txt`,
    `dctfweb/DCTFWeb_${m}2025_MIT_11222333000181.pdf`]),
  ...Array.from({ length: 7 }, (_, i) => `efd-icms-ipi/EFD_ICMS_IPI_0${i + 1}2026_11222333000181.txt`),
];

export async function semViolacoesGraves(page: Page, tela: string) {
  // iframes: false — a prévia do relatório é um iframe com sandbox (sem script), onde o axe não roda
  const { violations } = await new AxeBuilder({ page }).setLegacyMode().options({ iframes: false }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`), tela).toEqual([]);
}
