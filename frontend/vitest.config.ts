import { defineConfig, mergeConfig } from 'vitest/config';
import viteConfig from './vite.config';

export default mergeConfig(viteConfig, defineConfig({
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
    // telas inteiras no jsdom com a suíte em paralelo (e no runner do CI) passam de 5 s às vezes
    testTimeout: 15_000,
  },
}));
