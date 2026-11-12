import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { expect, test, type Page } from '@playwright/test';
import { ARQUIVOS_MASSA_COMPLETA as ARQUIVOS, criarCliente, criarEscritorioEEntrar, massa } from './apoio';

/**
 * Teste de restauração (deploy/teste-restauracao.sh), em duas fases contra o mesmo endereço:
 * - carga: escritório novo, a massa completa, um relatório emitido; grava as credenciais e o estado (respostas da API e
 *   SHA-256 dos arquivos) em RESTAURACAO_ARQUIVO;
 * - conferencia (depois do backup, da destruição dos volumes e da restauração): entra com as mesmas credenciais e confere
 *   que tudo é igual e que o aceite da massa continua valendo.
 * Fora do script, o teste é pulado.
 */
const FASE = process.env.RESTAURACAO_FASE;
const ARQUIVO = process.env.RESTAURACAO_ARQUIVO ?? '';
const SHA = (b: Buffer) => createHash('sha256').update(b).digest('hex');

/** O que precisa voltar igual (respostas que não dependem da hora, mais os arquivos do storage). */
async function estado(page: Page, clienteId: number) {
  const json = async (url: string) => (await page.request.get(url)).json();
  const pontos = await json(`/api/clientes/${clienteId}/pontos-atencao`);
  const documentos: { id: number; nomeOriginalArquivo: string }[] = await json(`/api/clientes/${clienteId}/documentos`);
  const relatorios = await json(`/api/clientes/${clienteId}/relatorios`);
  const arquivos: Record<string, string> = {};
  for (const d of documentos) arquivos[d.nomeOriginalArquivo] = SHA(await (await page.request.get(`/api/documentos/${d.id}/arquivo`)).body());
  for (const r of relatorios) arquivos[`relatorio-${r.numero}.pdf`] = SHA(await (await page.request.get(`/api/clientes/${clienteId}/relatorios/${r.id}/pdf`)).body());
  return {
    debitos: await json(`/api/clientes/${clienteId}/debitos`),
    parcelamentos: await json(`/api/clientes/${clienteId}/parcelamentos`),
    capacidadeOficial: await json(`/api/clientes/${clienteId}/capacidade-oficial`),
    pontos: pontos.map((p: { regra: string; titulo: string; severidade: string; estado: string }) => [p.regra, p.titulo, p.severidade, p.estado]).sort(),
    relatorios: relatorios.map((r: { numero: number; sha256Pdf: string }) => [r.numero, r.sha256Pdf]),
    arquivos,
  };
}

test('restauração do backup: a massa volta igual', async ({ page }) => {
  test.skip(!FASE, 'só pelo deploy/teste-restauracao.sh');
  test.setTimeout(600_000);

  if (FASE === 'carga') {
    const { email, senha } = await criarEscritorioEEntrar(page);
    const razao = await criarCliente(page, 'Metalurgica Pinheiral');
    await page.getByRole('link', { name: razao }).click();
    const clienteId = Number(/clientes\/(\d+)/.exec(page.url())![1]);
    await page.getByRole('navigation', { name: 'Seções do cliente' }).getByRole('link', { name: 'Importações', exact: true }).click();
    await page.locator('input[type=file]').setInputFiles(ARQUIVOS.map(massa));
    await expect.poll(async () => {
      const r = await (await page.request.get('/api/documentos/resumo')).json();
      return (r.PROCESSADO ?? 0) + (r.PROCESSADO_COM_ALERTAS ?? 0);
    }, { timeout: 400_000, intervals: [3_000] }).toBe(ARQUIVOS.length);
    // as reavaliações automáticas no meio da importação abrem R03 de passagem; a sob demanda deixa o estado final
    const xsrf = (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? '';
    const reavaliado = await page.request.post(`/api/clientes/${clienteId}/pontos-atencao/reavaliar`, { headers: { 'X-XSRF-TOKEN': xsrf } });
    expect(reavaliado.ok(), `reavaliar: ${reavaliado.status()}`).toBe(true);

    const emitido = await page.request.post(`/api/clientes/${clienteId}/relatorios`, { data: { tipo: 'SITUACAO_FISCAL' }, headers: { 'X-XSRF-TOKEN': xsrf } });
    expect(emitido.status()).toBe(201);

    writeFileSync(ARQUIVO, JSON.stringify({ email, senha, clienteId, estado: await estado(page, clienteId) }, null, 1));
    return;
  }

  const salvo = JSON.parse(readFileSync(ARQUIVO, 'utf-8'));
  await page.goto('/login');
  await page.getByLabel('E-mail').fill(salvo.email);
  await page.getByLabel('Senha').fill(salvo.senha);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Olá, Pessoa' })).toBeVisible();

  const depois = await estado(page, salvo.clienteId);
  expect(depois).toEqual(salvo.estado);
  // o aceite da massa (gabarito em backend/src/test/resources/massa-ficticia): CAPAG oficial C, R07 crítico e R04 alto,
  // e os arquivos originais com os mesmos bytes da massa versionada
  expect(depois.capacidadeOficial.map((c: { classe: string }) => c.classe)).toContain('C');
  const abertos = depois.pontos.filter((p: string[]) => ['NOVO', 'EM_ANALISE'].includes(p[3]));
  // R07 crítico (débitos que impedem a certidão) e R04 alto (ICMS-ST não recolhido com saldo credor), como no gabarito
  expect(abertos.filter((p: string[]) => p[2] === 'CRITICA').map((p: string[]) => p[0])).toContain('R07');
  expect(abertos.filter((p: string[]) => p[2] === 'ALTA').map((p: string[]) => p[0])).toContain('R04');
  expect(abertos.map((p: string[]) => p[0])).not.toContain('R03'); // tudo declarado (DCTFWeb de 2025 na massa)
  for (const a of ARQUIVOS) {
    const nome = a.split('/').pop()!;
    expect(depois.arquivos[nome], nome).toBe(SHA(readFileSync(massa(a))));
  }
});
