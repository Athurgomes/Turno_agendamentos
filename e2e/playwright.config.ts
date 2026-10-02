import { defineConfig, devices } from "@playwright/test";

/**
 * Ensaio do roteiro de demonstração (docs/08 §4, FD-5/F9-3). Roda dentro do
 * container oficial `mcr.microsoft.com/playwright` na rede do docker compose
 * do projeto (`projeto-condominio_default`), contra o serviço `frontend`
 * (nginx). Nunca acessa a internet nem hosts fora do compose (CLAUDE.md §11).
 */
export default defineConfig({
  testDir: "./",
  testMatch: "roteiro.spec.ts",
  timeout: 25 * 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: "http://frontend",
    trace: "retain-on-failure",
    video: "off",
    actionTimeout: 15_000,
    navigationTimeout: 20_000,
  },
  projects: [
    {
      name: "mobile-360",
      use: {
        ...devices["Desktop Chrome"],
        viewport: { width: 360, height: 740 },
      },
    },
    {
      name: "desktop",
      use: {
        ...devices["Desktop Chrome"],
        viewport: { width: 1366, height: 768 },
      },
    },
  ],
});
