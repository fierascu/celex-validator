import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for celex-validator e2e / accessibility tests.
 *
 * Backend:
 *   - If a server is already listening on http://localhost:8080 it is reused;
 *     otherwise Playwright starts `mvn quarkus:dev` from the project root.
 *
 * Run tests:
 *   cd e2e && npm test
 */
export default defineConfig({
  testDir: './tests',
  /* Run tests within a file sequentially */
  fullyParallel: false,
  /* Retry once on CI to absorb flakiness */
  retries: process.env.CI ? 1 : 0,
  /* Single worker keeps test order deterministic */
  workers: 1,
  reporter: [['html', { open: 'never' }], ['list']],

  use: {
    baseURL: 'http://localhost:8080',
    /* Record traces on first retry so failures are easy to debug */
    trace: 'on-first-retry',
    /* Every test waits up to 5 s for the Quarkus backend to respond */
    actionTimeout: 5_000,
  },

  /* Start Quarkus if it is not already up; reuse a running instance otherwise */
  webServer: {
    command: 'mvn quarkus:dev -Dquarkus.console.enabled=false',
    cwd: '..',
    url: 'http://localhost:8080/index.html',
    reuseExistingServer: true,
    timeout: 180_000,
    stdout: 'ignore',
    stderr: 'pipe',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
  ],
});
