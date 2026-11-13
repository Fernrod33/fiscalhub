import { expect, test as base } from '@playwright/test';

/**
 * O `test` de todos os specs: falha se a página (ou um iframe, como a prévia do relatório) registrar violação de CSP.
 * Contra o proxy (E2E_URL) vale a política de produção do deploy/Caddyfile, sem 'unsafe-inline'.
 */
export const test = base.extend<{ semViolacaoCsp: void }>({
  semViolacaoCsp: [async ({ page }, usar) => {
    const violacoes: string[] = [];
    page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violacoes.push(m.text()); });
    await usar();
    expect(violacoes, 'violações de CSP').toEqual([]);
  }, { auto: true }],
});

export { expect };
