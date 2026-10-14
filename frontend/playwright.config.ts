import { defineConfig, devices } from '@playwright/test';

/**
 * Testes de ponta a ponta contra o backend local (perfil dev em http://localhost:8080, com banco e storage próprios).
 * O frontend sobe sozinho (ou é reaproveitado se já estiver rodando).
 */
export default defineConfig({
  testDir: 'e2e',
  timeout: 60_000,
  fullyParallel: false,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'npm run dev -- --port 5173 --strictPort',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } }],
});
