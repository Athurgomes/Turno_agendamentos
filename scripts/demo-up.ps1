# FD-4: sobe o ambiente de demonstracao (perfil `demo`) com um comando.
#
# - Se `.env` nao existir na raiz, cria a partir de `.env.example` com segredos
#   aleatorios fortes (D-48) e SPRING_PROFILES_ACTIVE=demo. NUNCA sobrescreve um
#   `.env` existente.
# - Se `.env` ja existir: NUNCA copia valores do `.env.example` (evita colar o
#   comentario inline no `.env` e evita gravar segredos de exemplo — D-48).
#   Chaves criticas ausentes (APP_JWT_SECRET, APP_SEED_ADMIN_PASSWORD,
#   APP_DEMO_PASSWORD, APP_DEMO_TEMP_PASSWORD) ganham um valor aleatorio novo,
#   acrescentado ao `.env`.
#   Se alguma delas estiver igual ao valor de exemplo, avisa em destaque (nao
#   sobrescreve sozinho). Outras chaves ausentes: so aviso, nada e gravado.
# - `-Now <ISO>` grava/atualiza APP_DEMO_NOW no `.env` (relogio simulado, D-32).
#   Sem o parametro, mantem o que ja estiver no `.env`.
# - `-Tunnel`: liga tambem um container `cloudflare/cloudflared` publicando a
#   URL http://frontend:80 na internet enquanto o comando roda (docs/07 §4).
#   AVISO: expoe o ambiente na internet enquanto o comando roda. So o humano
#   executa esta opcao (CLAUDE.md §11); rate limit de login some atras do
#   tunel (P-12, docs/05).
#
# Uso:
#   .\scripts\demo-up.ps1
#   .\scripts\demo-up.ps1 -Now 2026-11-10T10:00
#   .\scripts\demo-up.ps1 -Tunnel   # SO O HUMANO EXECUTA

param(
  [string]$Now = "",
  [switch]$Tunnel
)

$ErrorActionPreference = "Stop"

$Project = "projeto-condominio"
$CriticalKeys = @('APP_JWT_SECRET', 'APP_SEED_ADMIN_PASSWORD', 'APP_DEMO_PASSWORD', 'APP_DEMO_TEMP_PASSWORD')

function Get-UnbiasedIndex {
  # Indice aleatorio uniforme em [0, Max) por rejection sampling sobre um byte
  # criptografico. `GetInt32` (a forma direta) exige .NET 6+; Windows
  # PowerShell 5.1 (a que os scripts do projeto tambem precisam rodar, ja que
  # so ela esta instalada aqui) so tem `GetBytes`, entao a rejeicao manual e
  # o jeito compativel com os dois runtimes.
  param([int]$Max)
  $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  $limit = [byte]255 - (256 % $Max)
  $buf = New-Object byte[] 1
  while ($true) {
    $rng.GetBytes($buf)
    if ($buf[0] -le $limit) { return $buf[0] % $Max }
  }
}

function New-RandomPassword {
  param([int]$Length = 20)
  $chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
  -join (1..$Length | ForEach-Object {
    $chars[(Get-UnbiasedIndex -Max $chars.Length)]
  })
}

function New-RandomSecret {
  # >=48 caracteres alfanumericos: seguro para .env.
  param([int]$Length = 48)
  New-RandomPassword -Length $Length
}

function New-RandomTempPassword {
  # RN-03: 6 caracteres, so letras maiusculas e numeros, sem os ambiguos 0 O 1 I L
  # (mesmo alfabeto de `TemporaryPasswordGenerator`), para a senha temporaria
  # fixa da unidade a-102 (APP_DEMO_TEMP_PASSWORD) parecer uma senha temporaria
  # de verdade no roteiro.
  $chars = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'
  -join (1..6 | ForEach-Object {
    $chars[(Get-UnbiasedIndex -Max $chars.Length)]
  })
}

function Set-EnvVar {
  # Define Key=Value em $EnvFile: se a chave ja existir (comentada ou nao), so
  # substitui quando ainda nao tem valor (linha comentada) ou quando $Force;
  # se nao existe em nenhuma forma, acrescenta.
  param([string]$EnvFile, [string]$Key, [string]$Value, [bool]$Force = $false)
  $lines = Get-Content $EnvFile
  $definedIndex = -1
  $commentedIndex = -1
  for ($i = 0; $i -lt $lines.Length; $i++) {
    if ($lines[$i] -match "^$Key=") { $definedIndex = $i }
    elseif ($lines[$i] -match "^# ?$Key=") { $commentedIndex = $i }
  }
  if ($definedIndex -ge 0) {
    if ($Force) {
      $lines[$definedIndex] = "$Key=$Value"
      Set-Content -Path $EnvFile -Value $lines
    }
    return
  }
  if ($commentedIndex -ge 0) {
    $lines[$commentedIndex] = "$Key=$Value"
    Set-Content -Path $EnvFile -Value $lines
  } else {
    Add-Content -Path $EnvFile -Value "$Key=$Value"
  }
}

function Get-EnvValue {
  # Le Key=valor de um arquivo .env, removendo comentario inline (" # ...") e espacos.
  param([string]$EnvFile, [string]$Key)
  foreach ($l in (Get-Content $EnvFile)) {
    if ($l -match "^$Key=(.*)$") {
      return ($Matches[1] -replace '\s+#.*$', '').Trim()
    }
  }
  return $null
}

function Initialize-DemoEnvFile {
  # Prepara o .env (cria do zero, ou completa/avisa sem nunca copiar do
  # .env.example) — isolada em funcao para poder ser testada com arquivos de
  # um diretorio temporario, sem subir o docker compose.
  param([string]$EnvFile, [string]$EnvExample)

  if (-not (Test-Path $EnvFile)) {
    Write-Host "Criando $EnvFile a partir de $EnvExample com segredos locais gerados agora..."
    Copy-Item $EnvExample $EnvFile
    Set-EnvVar -EnvFile $EnvFile -Key "SPRING_PROFILES_ACTIVE" -Value "demo" -Force $true
    Set-EnvVar -EnvFile $EnvFile -Key "APP_JWT_SECRET" -Value (New-RandomSecret) -Force $true
    Set-EnvVar -EnvFile $EnvFile -Key "APP_SEED_ADMIN_PASSWORD" -Value (New-RandomPassword) -Force $true
    Set-EnvVar -EnvFile $EnvFile -Key "APP_DEMO_PASSWORD" -Value (New-RandomPassword) -Force $true
    Set-EnvVar -EnvFile $EnvFile -Key "APP_DEMO_TEMP_PASSWORD" -Value (New-RandomTempPassword) -Force $true
    Write-Host "$EnvFile criado. Segredos gerados localmente; nunca commitados (esta no .gitignore)."
    return
  }

  Write-Host "$EnvFile ja existe: nao foi sobrescrito. Nao copiamos valores do $EnvExample."

  foreach ($key in $CriticalKeys) {
    $existing = Get-Content $EnvFile
    if (-not ($existing -match "^$key=")) {
      $generated = switch ($key) {
        'APP_JWT_SECRET' { New-RandomSecret }
        'APP_DEMO_TEMP_PASSWORD' { New-RandomTempPassword }
        default { New-RandomPassword }
      }
      Set-EnvVar -EnvFile $EnvFile -Key $key -Value $generated
      Write-Host "AVISO: $key ausente em $EnvFile; valor aleatorio gerado e adicionado."
    }
  }

  foreach ($key in $CriticalKeys) {
    $exampleValue = Get-EnvValue -EnvFile $EnvExample -Key $key
    if ([string]::IsNullOrEmpty($exampleValue)) { continue }
    $currentValue = Get-EnvValue -EnvFile $EnvFile -Key $key
    if (-not [string]::IsNullOrEmpty($currentValue) -and $currentValue -eq $exampleValue) {
      Write-Host ""
      Write-Host "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
      Write-Host "AVISO CRITICO: $key em $EnvFile ainda usa o valor de exemplo."
      Write-Host "O backend recusa subir no perfil demo com este valor (D-48). Troque"
      Write-Host "manualmente por um valor proprio."
      Write-Host "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
      Write-Host ""
    }
  }

  $missingKeys = @()
  foreach ($line in (Get-Content $EnvExample)) {
    if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=') {
      $exampleKey = $Matches[1]
      if ($CriticalKeys -contains $exampleKey) { continue }
      if (-not ((Get-Content $EnvFile) -match "^$exampleKey=")) {
        $missingKeys += $exampleKey
      }
    }
  }
  if ($missingKeys.Count -gt 0) {
    Write-Host "AVISO: variaveis ausentes em $EnvFile (nao copiadas automaticamente do $EnvExample):"
    foreach ($k in $missingKeys) { Write-Host "  - $k" }
  }
}

function Main {
  $RepoRoot = Split-Path -Parent $PSScriptRoot
  Set-Location $RepoRoot

  $EnvFile = Join-Path $RepoRoot ".env"
  $EnvExample = Join-Path $RepoRoot ".env.example"

  Initialize-DemoEnvFile -EnvFile $EnvFile -EnvExample $EnvExample

  if ($Now -ne "") {
    Set-EnvVar -EnvFile $EnvFile -Key "APP_DEMO_NOW" -Value $Now -Force $true
    Write-Host "Relogio simulado definido: APP_DEMO_NOW=$Now"
  }

  Write-Host "Subindo docker compose (projeto $Project)..."
  docker compose -p $Project up --build -d
  if ($LASTEXITCODE -ne 0) { throw "docker compose up falhou." }

  $Services = @("postgres", "s3", "backend", "frontend")
  $TimeoutSeconds = 180
  $Elapsed = 0
  $Interval = 5

  Write-Host "Aguardando os 4 servicos ficarem healthy (timeout ${TimeoutSeconds}s)..."
  while ($true) {
    $allHealthy = $true
    foreach ($svc in $Services) {
      $cid = (docker compose -p $Project ps -q $svc)
      if (-not $cid) { $allHealthy = $false; break }
      $status = (docker inspect --format='{{.State.Health.Status}}' $cid 2>$null)
      if ($status -ne "healthy") { $allHealthy = $false; break }
    }
    if ($allHealthy) {
      Write-Host "Os 4 servicos estao healthy."
      break
    }
    if ($Elapsed -ge $TimeoutSeconds) {
      Write-Error "ERRO: timeout de ${TimeoutSeconds}s esperando os servicos ficarem healthy. Verifique com: docker compose -p $Project ps"
      exit 1
    }
    Start-Sleep -Seconds $Interval
    $Elapsed += $Interval
  }

  $AdminEmail = Get-EnvValue -EnvFile $EnvFile -Key "APP_SEED_ADMIN_EMAIL"
  $AdminPassword = Get-EnvValue -EnvFile $EnvFile -Key "APP_SEED_ADMIN_PASSWORD"
  $DemoPassword = Get-EnvValue -EnvFile $EnvFile -Key "APP_DEMO_PASSWORD"
  $DemoTempPassword = Get-EnvValue -EnvFile $EnvFile -Key "APP_DEMO_TEMP_PASSWORD"

  Write-Host ""
  Write-Host "=================================================================="
  Write-Host " Ambiente de demonstracao no ar: http://localhost"
  Write-Host "------------------------------------------------------------------"
  Write-Host " Contas (docs/08 §2)"
  Write-Host ""
  Write-Host "   ADMIN   login: $AdminEmail   senha: $AdminPassword"
  Write-Host "   SYNDIC  login: sindico@exemplo.test   senha: $DemoPassword"
  Write-Host "   UNIT    login: a-101 / b-201 / b-202 / a-103 / b-203   senha: $DemoPassword"
  Write-Host "   UNIT    login: a-102 (senha temporaria, RN-03)   senha: $DemoTempPassword"
  Write-Host ""
  Write-Host " (Senhas impressas de proposito: sao contas ficticias de demonstracao"
  Write-Host "  para a equipe usar na apresentacao - nunca dados reais. O restante"
  Write-Host "  dos segredos, como APP_JWT_SECRET, fica so no .env e nao e impresso.)"
  Write-Host "=================================================================="

  if ($Tunnel) {
    Write-Host ""
    Write-Host "AVISO: -Tunnel expoe o ambiente na internet enquanto o comando roda (URL"
    Write-Host "publica temporaria via Cloudflare Tunnel). So o humano executa esta opcao"
    Write-Host "(CLAUDE.md §11) - agentes nunca chamam -Tunnel nem testam com ela. Ctrl+C"
    Write-Host "encerra o tunel; os dados sao ficticios (P-12, docs/05: rate limit do login"
    Write-Host "fica compartilhado por toda a plateia atras do tunel)."
    Write-Host ""
    $Network = "${Project}_default"
    docker run --rm -it --name "$Project-tunnel" --network $Network cloudflare/cloudflared:latest tunnel --url http://frontend:80
  }
}

if ($MyInvocation.InvocationName -ne '.') {
  Main
}
