// Drives the real application through a browser: the demo Compose stack, which already
// brings its own PostgreSQL. Nothing here stubs the server, so a page that only works
// with JavaScript, or an accessibility rule ReleaseFlow breaks, is caught for real.
import { defineConfig, devices } from '@playwright/test';

const baseURL = process.env.RELEASEFLOW_E2E_BASE_URL ?? 'http://127.0.0.1:8080';

export default defineConfig({
  testDir: 'tests/e2e',
  // A page is server-rendered, so a failure is a failure; a retry would only hide a race.
  retries: 0,
  // One worker: the suite registers Organizations in one shared database.
  workers: 1,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI ? [['github'], ['list']] : [['list']],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    // The two shapes the accessibility pass is held to: a narrow phone in the light
    // theme, and a wide desktop in the dark one.
    {
      name: 'phone-light',
      use: { ...devices['Desktop Chrome'], viewport: { width: 360, height: 780 }, colorScheme: 'light' },
    },
    {
      name: 'desktop-dark',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 }, colorScheme: 'dark' },
    },
  ],
  webServer: process.env.RELEASEFLOW_E2E_BASE_URL
    ? undefined
    : {
        command: 'docker compose -f docker-compose.demo.yml up --build app db',
        url: `${baseURL}/api/status`,
        reuseExistingServer: !process.env.CI,
        timeout: 300_000,
        stdout: 'pipe',
        stderr: 'pipe',
      },
});
