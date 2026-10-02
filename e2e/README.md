# Ensaio do roteiro de demonstração (FD-5 / F9-3)

Playwright rodando **só dentro do container oficial** `mcr.microsoft.com/playwright`
(D-65) — nada é instalado no PC do host. Cobre `docs/08-roteiro-demo.md` §4,
passos 1 a 12, em dois viewports (`mobile-360` = 360×740, `desktop` = 1366×768).

## Pré-requisito

Ambiente `demo` já no ar (`docker compose -p projeto-condominio ps` com os 4
serviços healthy) e o `.env` da raiz do projeto com `APP_SEED_ADMIN_PASSWORD`,
`APP_DEMO_PASSWORD` e `APP_DEMO_TEMP_PASSWORD` definidos.

## Como rodar (um viewport por vez — o roteiro altera dados)

```bash
# 1) reseta o cenário (cria a-104, zera reservas etc.) — sempre antes de cada viewport
bash scripts/demo-reset.sh --now 2026-11-10T10:00

# 2) roda só o projeto mobile-360, lendo as senhas do .env
docker run --rm \
  --network projeto-condominio_default \
  -v "$(pwd)/e2e:/e2e" \
  -w /e2e \
  --env-file .env \
  mcr.microsoft.com/playwright:v1.48.0-noble \
  sh -c "npm ci && npx playwright test --project=mobile-360"

# 3) reseta de novo antes do desktop
bash scripts/demo-reset.sh --now 2026-11-10T10:00

docker run --rm \
  --network projeto-condominio_default \
  -v "$(pwd)/e2e:/e2e" \
  -w /e2e \
  --env-file .env \
  mcr.microsoft.com/playwright:v1.48.0-noble \
  sh -c "npm ci && npx playwright test --project=desktop"
```

Na primeira execução, `npm ci` falha se `package-lock.json` ainda não existe —
gere-o uma vez com `npm install` dentro do mesmo container.

## Saída

- `e2e/screenshots/<projeto>/<nn>-<passo>.png` — uma captura por passo (e
  `-erro` quando o passo falha).
- `e2e/test-results/` — XLSX baixado no passo 12 e artefatos de falha
  (trace) do Playwright.
- `e2e/playwright-report/` — relatório HTML (`npx playwright show-report`
  dentro do mesmo container, se quiser abrir).
- Console do `docker run`: resumo passo × status, e linhas `[console.error]`
  / `[pageerror]` coletadas do navegador.

Nenhuma dessas pastas é versionada (`.gitignore` da raiz).

## Fora do escopo deste ensaio

Celular real e túnel público (D-35, D-65) — só o vídeo de backup da equipe
cobre isso. Ver `docs/12-ensaio-roteiro.md` para o relatório da rodada mais
recente.
