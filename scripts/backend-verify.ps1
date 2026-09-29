# D-37: builda e testa o backend num container Maven efemero, sem exigir
# Java instalado no host. Monta so backend/ (codigo), um volume nomeado de
# cache do Maven (projeto-condominio_m2) e o socket do Docker (o
# Testcontainers precisa dele para subir o PostgreSQL real dos testes).
#
# Uso:
#   .\scripts\backend-verify.ps1                  # mvn -B verify
#   .\scripts\backend-verify.ps1 -Dtest=Foo#bar    # args extras repassados ao mvn

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$BackendDir = Join-Path $RepoRoot "backend"
$Image = "maven:3-eclipse-temurin-21"
$ContainerName = "projeto-condominio-verify"
$M2Volume = "projeto-condominio_m2"

docker rm -f $ContainerName 2>$null | Out-Null

docker run --rm `
  --name $ContainerName `
  --label "com.docker.compose.project=projeto-condominio" `
  -v "${BackendDir}:/workspace" `
  -v "${M2Volume}:/root/.m2" `
  -v "//var/run/docker.sock:/var/run/docker.sock" `
  -w /workspace `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal `
  -e TESTCONTAINERS_RYUK_DISABLED=false `
  $Image `
  mvn -B verify @args
