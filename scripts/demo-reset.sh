#!/usr/bin/env bash
# FD-4: recria o cenario de demonstracao do zero antes de cada apresentacao.
#
# ATENCAO: apaga tambem os dados do perfil `demo`/`local` (volumes `pgdata` e
# `s3data` do projeto `projeto-condominio`) — banco e fotos voltam ao zero.
# So remove volumes deste projeto (`docker compose ... down -v`); nunca faz
# `docker system prune` nem mexe em containers de outros projetos (ex.:
# Testcontainers do `backend-verify.sh`, que rodam fora deste compose).
#
# Uso: identico ao demo-up.sh (repassa todos os argumentos).
#   ./scripts/demo-reset.sh
#   ./scripts/demo-reset.sh --now 2026-11-10T10:00
set -euo pipefail

cd "$(dirname "$0")/.."

PROJECT="projeto-condominio"

echo "Apagando os containers e volumes do projeto $PROJECT (banco e fotos deste compose)..."
docker compose -p "$PROJECT" down -v

echo "Recriando o ambiente..."
exec "$(dirname "$0")/demo-up.sh" "$@"
