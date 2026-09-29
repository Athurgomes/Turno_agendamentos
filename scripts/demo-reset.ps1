# FD-4: recria o cenario de demonstracao do zero antes de cada apresentacao.
#
# ATENCAO: apaga tambem os dados do perfil `demo`/`local` (volumes `pgdata` e
# `s3data` do projeto `projeto-condominio`) - banco e fotos voltam ao zero.
# So remove volumes deste projeto (`docker compose ... down -v`); nunca faz
# `docker system prune` nem mexe em containers de outros projetos (ex.:
# Testcontainers do `backend-verify.ps1`, que rodam fora deste compose).
#
# Uso: identico ao demo-up.ps1 (repassa todos os parametros).
#   .\scripts\demo-reset.ps1
#   .\scripts\demo-reset.ps1 -Now 2026-11-10T10:00

param(
  [string]$Now = "",
  [switch]$Tunnel
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

$Project = "projeto-condominio"

Write-Host "Apagando os containers e volumes do projeto $Project (banco e fotos deste compose)..."
docker compose -p $Project down -v
if ($LASTEXITCODE -ne 0) { throw "docker compose down -v falhou." }

Write-Host "Recriando o ambiente..."
$upScript = Join-Path $PSScriptRoot "demo-up.ps1"
& $upScript -Now $Now -Tunnel:$Tunnel
