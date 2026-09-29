#!/usr/bin/env bash
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
# - `--now <ISO>` grava/atualiza APP_DEMO_NOW no `.env` (relogio simulado, D-32).
#   Sem o parametro, mantem o que ja estiver no `.env`.
# - `--tunnel`: liga tambem um container `cloudflare/cloudflared` publicando a
#   URL http://frontend:80 na internet enquanto o comando roda (docs/07 §4).
#   AVISO: expoe o ambiente na internet enquanto o comando roda. So o humano
#   executa esta opcao (CLAUDE.md §11); rate limit de login some atras do
#   tunel (P-12, docs/05).
#
# Uso:
#   ./scripts/demo-up.sh
#   ./scripts/demo-up.sh --now 2026-11-10T10:00
#   ./scripts/demo-up.sh --tunnel   # SO O HUMANO EXECUTA
set -euo pipefail

PROJECT="projeto-condominio"

random_secret() {
  # >=48 caracteres alfanumericos: seguro para .env, sem depender de openssl.
  # "|| true": `head` fecha o pipe assim que tem os 48 bytes e `tr` recebe
  # SIGPIPE (exit 141); com `pipefail` isso derrubaria o script sem ser um
  # erro real (a saida ja foi produzida corretamente).
  LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 48 || true
}

random_password() {
  # Senha forte só com alfanumericos: nunca quebra o parsing de `.env`.
  LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 20 || true
}

random_temp_password() {
  # RN-03: 6 caracteres, so letras maiusculas e numeros, sem os ambiguos 0 O 1 I L
  # (mesmo alfabeto de `TemporaryPasswordGenerator`), para a senha temporaria
  # fixa da unidade a-102 (APP_DEMO_TEMP_PASSWORD) parecer uma senha temporaria
  # de verdade no roteiro.
  local alphabet="ABCDEFGHJKMNPQRSTUVWXYZ23456789"
  local out=""
  local i byte idx
  for ((i = 0; i < 6; i++)); do
    byte="$(LC_ALL=C tr -dc "$alphabet" < /dev/urandom | head -c 1)"
    out="${out}${byte}"
  done
  printf '%s' "$out"
}

# Le KEY=valor de um arquivo .env, removendo comentario inline (" # ...") e espacos.
# "|| true": sob `pipefail`, uma chave ausente (grep sem match) nao pode
# derrubar o script com `set -e` — apenas devolve string vazia.
read_env_value() {
  local file="$1" key="$2"
  { grep -E "^${key}=" "$file" || true; } | head -n1 | cut -d= -f2- | sed -E 's/[[:space:]]+#.*$//; s/[[:space:]]+$//'
}

# Define KEY=VALUE em $1 (arquivo): se a chave ja existir (comentada ou nao),
# so substitui quando ainda nao tem valor (linha comentada) ou quando $4=1
# (force). Se a chave nao existe em nenhuma forma, acrescenta.
set_env_var() {
  local env_file="$1" key="$2" value="$3" force="${4:-0}"
  if grep -qE "^${key}=" "$env_file"; then
    if [[ "$force" == "1" ]]; then
      sed -i.bak -E "s|^${key}=.*|${key}=${value}|" "$env_file" && rm -f "$env_file.bak"
    fi
    return
  fi
  if grep -qE "^# ?${key}=" "$env_file"; then
    sed -i.bak -E "s|^# ?${key}=.*|${key}=${value}|" "$env_file" && rm -f "$env_file.bak"
  else
    printf '%s=%s\n' "$key" "$value" >> "$env_file"
  fi
}

# Prepara o .env (cria do zero, ou completa/avisa sem nunca copiar do
# .env.example) — isolada em funcao para poder ser testada com arquivos de
# um diretorio temporario, sem subir o docker compose.
prepare_env_file() {
  local env_file="$1" env_example="$2"
  local critical_keys=(APP_JWT_SECRET APP_SEED_ADMIN_PASSWORD APP_DEMO_PASSWORD APP_DEMO_TEMP_PASSWORD)

  if [[ ! -f "$env_file" ]]; then
    echo "Criando $env_file a partir de $env_example com segredos locais gerados agora..."
    cp "$env_example" "$env_file"
    set_env_var "$env_file" SPRING_PROFILES_ACTIVE demo 1
    set_env_var "$env_file" APP_JWT_SECRET "$(random_secret)" 1
    set_env_var "$env_file" APP_SEED_ADMIN_PASSWORD "$(random_password)" 1
    set_env_var "$env_file" APP_DEMO_PASSWORD "$(random_password)" 1
    set_env_var "$env_file" APP_DEMO_TEMP_PASSWORD "$(random_temp_password)" 1
    echo "$env_file criado. Segredos gerados localmente; nunca commitados (esta no .gitignore)."
    return
  fi

  echo "$env_file ja existe: nao foi sobrescrito. Nao copiamos valores do $env_example."

  local key
  for key in "${critical_keys[@]}"; do
    if ! grep -qE "^${key}=" "$env_file"; then
      local generated
      case "$key" in
        APP_JWT_SECRET) generated="$(random_secret)" ;;
        APP_DEMO_TEMP_PASSWORD) generated="$(random_temp_password)" ;;
        *) generated="$(random_password)" ;;
      esac
      set_env_var "$env_file" "$key" "$generated"
      echo "AVISO: $key ausente em $env_file; valor aleatorio gerado e adicionado."
    fi
  done

  for key in "${critical_keys[@]}"; do
    local example_value current_value
    example_value="$(read_env_value "$env_example" "$key")"
    [[ -z "$example_value" ]] && continue
    current_value="$(read_env_value "$env_file" "$key")"
    if [[ -n "$current_value" && "$current_value" == "$example_value" ]]; then
      echo ""
      echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
      echo "AVISO CRITICO: $key em $env_file ainda usa o valor de exemplo."
      echo "O backend recusa subir no perfil demo com este valor (D-48). Troque"
      echo "manualmente por um valor proprio."
      echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
      echo ""
    fi
  done

  local missing_keys=()
  while IFS= read -r line; do
    [[ "$line" =~ ^([A-Za-z_][A-Za-z0-9_]*)= ]] || continue
    local example_key="${BASH_REMATCH[1]}"
    case "$example_key" in
      APP_JWT_SECRET|APP_SEED_ADMIN_PASSWORD|APP_DEMO_PASSWORD|APP_DEMO_TEMP_PASSWORD) continue ;;
    esac
    grep -qE "^${example_key}=" "$env_file" && continue
    missing_keys+=("$example_key")
  done < <(grep -E '^[A-Za-z_][A-Za-z0-9_]*=' "$env_example")

  if (( ${#missing_keys[@]} > 0 )); then
    echo "AVISO: variaveis ausentes em $env_file (nao copiadas automaticamente do $env_example):"
    printf '  - %s\n' "${missing_keys[@]}"
  fi
}

main() {
  cd "$(dirname "$0")/.."

  local env_file=".env"
  local env_example=".env.example"
  local now_value=""
  local tunnel=false

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --now)
        now_value="$2"
        shift 2
        ;;
      --now=*)
        now_value="${1#--now=}"
        shift
        ;;
      --tunnel)
        tunnel=true
        shift
        ;;
      *)
        echo "Argumento desconhecido: $1" >&2
        exit 1
        ;;
    esac
  done

  prepare_env_file "$env_file" "$env_example"

  if [[ -n "$now_value" ]]; then
    set_env_var "$env_file" APP_DEMO_NOW "$now_value" 1
    echo "Relogio simulado definido: APP_DEMO_NOW=$now_value"
  fi

  echo "Subindo docker compose (projeto $PROJECT)..."
  docker compose -p "$PROJECT" up --build -d

  local services=(postgres s3 backend frontend)
  local timeout=180
  local elapsed=0
  local interval=5

  echo "Aguardando os 4 servicos ficarem healthy (timeout ${timeout}s)..."
  while true; do
    local all_healthy=true
    local svc
    for svc in "${services[@]}"; do
      local cid
      cid="$(docker compose -p "$PROJECT" ps -q "$svc")"
      if [[ -z "$cid" ]]; then
        all_healthy=false
        break
      fi
      local status
      status="$(docker inspect --format='{{.State.Health.Status}}' "$cid" 2>/dev/null || echo "unknown")"
      if [[ "$status" != "healthy" ]]; then
        all_healthy=false
        break
      fi
    done
    if [[ "$all_healthy" == "true" ]]; then
      echo "Os 4 servicos estao healthy."
      break
    fi
    if (( elapsed >= timeout )); then
      echo "ERRO: timeout de ${timeout}s esperando os servicos ficarem healthy." >&2
      echo "Verifique com: docker compose -p $PROJECT ps" >&2
      exit 1
    fi
    sleep "$interval"
    elapsed=$((elapsed + interval))
  done

  local admin_email admin_password demo_password demo_temp_password
  admin_email="$(read_env_value "$env_file" APP_SEED_ADMIN_EMAIL)"
  admin_password="$(read_env_value "$env_file" APP_SEED_ADMIN_PASSWORD)"
  demo_password="$(read_env_value "$env_file" APP_DEMO_PASSWORD)"
  demo_temp_password="$(read_env_value "$env_file" APP_DEMO_TEMP_PASSWORD)"

  echo ""
  echo "=================================================================="
  echo " Ambiente de demonstracao no ar: http://localhost"
  echo "------------------------------------------------------------------"
  echo " Contas (docs/08 §2)"
  echo ""
  echo "   ADMIN   login: ${admin_email:-<definido no .env>}   senha: ${admin_password:-<vazia>}"
  echo "   SYNDIC  login: sindico@exemplo.test                 senha: ${demo_password:-<definir APP_DEMO_PASSWORD>}"
  echo "   UNIT    login: a-101 / b-201 / b-202 / a-103 / b-203  senha: ${demo_password:-<definir APP_DEMO_PASSWORD>}"
  echo "   UNIT    login: a-102 (senha temporaria, RN-03)       senha: ${demo_temp_password:-<definir APP_DEMO_TEMP_PASSWORD>}"
  echo ""
  echo " (Senhas impressas de propósito: sao contas ficticias de demonstracao"
  echo "  para a equipe usar na apresentacao — nunca dados reais. O restante"
  echo "  dos segredos, como APP_JWT_SECRET, fica so no .env e nao e impresso.)"
  echo "=================================================================="

  if [[ "$tunnel" == "true" ]]; then
    cat <<'EOF'

AVISO: --tunnel expoe o ambiente na internet enquanto o comando roda (URL
publica temporaria via Cloudflare Tunnel). So o humano executa esta opcao
(CLAUDE.md §11) — agentes nunca chamam --tunnel nem testam com ela. Ctrl+C
encerra o tunel; os dados sao ficticios (P-12, docs/05: rate limit do login
fica compartilhado por toda a plateia atras do tunel).

EOF
    local network="${PROJECT}_default"
    docker run --rm -it \
      --name "${PROJECT}-tunnel" \
      --network "$network" \
      cloudflare/cloudflared:latest \
      tunnel --url http://frontend:80
  fi
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
