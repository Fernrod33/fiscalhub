import { defineConfig, devices } from '@playwright/test';

/**
 * Testes de ponta a ponta. Sem E2E_URL: contra o backend local (perfil dev em http://localhost:8080) com o Vite, que
 * sobe sozinho (ou é reaproveitado). Com E2E_URL (CI): contra o proxy do compose de desenvolvimento, já no ar, com as
 * imagens, a CSP e os cabeçalhos de produção.
 */
const URL_EXTERNA = process.env.E2E_URL;

export default defineConfig({
  testDir: 'e2e',
  timeout: 60_000,
  fullyParallel: false,
  // no CI um retry: o flaky aparece no relatório (não some), e uma falha de verdade falha duas vezes
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: URL_EXTERNA ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    // telas sem animação: o axe avalia o estado final (o contraste de um elemento ainda entrando dava falso positivo)
    contextOptions: { reducedMotion: 'reduce' },
  },
  webServer: URL_EXTERNA ? undefined : {
    command: 'npm run dev -- --port 5173 --strictPort',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } }],
});
