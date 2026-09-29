#!/usr/bin/env bash
# D-37: builda e testa o backend num container Maven efemero, sem exigir
# Java instalado no host. Monta so backend/ (codigo), um volume nomeado de
# cache do Maven (projeto-condominio_m2) e o socket do Docker (o
# Testcontainers precisa dele para subir o PostgreSQL real dos testes).
#
# Uso:
#   ./scripts/backend-verify.sh                # mvn -B verify
#   ./scripts/backend-verify.sh -Dtest=Foo#bar  # args extras repassados ao mvn
set -euo pipefail

cd "$(dirname "$0")/.."

BACKEND_DIR="$(pwd)/backend"
IMAGE="maven:3-eclipse-temurin-21"
CONTAINER_NAME="projeto-condominio-verify"
M2_VOLUME="projeto-condominio_m2"

# Evita a conversao automatica de path do Git Bash para Windows nos argumentos do `docker run`.
export MSYS_NO_PATHCONV=1

docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true

docker run --rm \
  --name "$CONTAINER_NAME" \
  --label "com.docker.compose.project=projeto-condominio" \
  -v "$BACKEND_DIR:/workspace" \
  -v "$M2_VOLUME:/root/.m2" \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -w /workspace \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -e TESTCONTAINERS_RYUK_DISABLED=false \
  "$IMAGE" \
  mvn -B verify "$@"
