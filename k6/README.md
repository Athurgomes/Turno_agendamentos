# k6 — RNF-03 (concorrência) e RNF-04 (carga de leitura)

Sem instalar nada global: roda a imagem pública `grafana/k6` (Docker Hub) em
container efêmero (`--rm`), ligado à rede do compose do projeto, chamando
`http://frontend/api/v1/...` (mesmo caminho do nginx). Sem publicar porta,
sem `--privileged`, sem montar nada fora de `k6/`.

## Pré-requisitos

1. Ambiente demo no ar: `bash scripts/demo-reset.sh --now 2026-11-10T10:00`
   (anote as senhas impressas no console — `APP_DEMO_PASSWORD`/ADMIN).
2. Confira o nome da rede: `docker network ls` (normalmente
   `projeto-condominio_default`).
3. Descubra o `id` das áreas via `GET /areas` (ADMIN) — `concurrency.js` usa
   a Churrasqueira 2 — Cobertura (gratuita); `read-load.js` aceita qualquer
   área ativa.
4. Escolha uma conta `UNIT` **sem reservas futuras ativas** (senão RN-22
   pode barrar as 50 tentativas com `422 UNIT_BOOKING_LIMIT_REACHED` em vez
   de `409 RESERVATION_OVERLAP`) e uma data/horário livres (ver
   `GET /areas/{id}/availability`).

## Concorrência (RNF-03)

```bash
docker run --rm --network projeto-condominio_default \
  -v "$PWD/k6:/scripts" -w /scripts \
  -e UNIT_LOGIN=a-101 -e UNIT_PASSWORD="$APP_DEMO_PASSWORD" \
  -e AREA_ID=<id-churrasqueira-2> -e RESERVATION_DATE=2026-11-12 \
  -e START_TIME=13:00 -e END_TIME=14:00 \
  grafana/k6 run concurrency.js
```

```powershell
docker run --rm --network projeto-condominio_default `
  -v "${PWD}/k6:/scripts" -w /scripts `
  -e UNIT_LOGIN=a-101 -e UNIT_PASSWORD=$env:APP_DEMO_PASSWORD `
  -e AREA_ID=<id-churrasqueira-2> -e RESERVATION_DATE=2026-11-12 `
  -e START_TIME=13:00 -e END_TIME=14:00 `
  grafana/k6 run concurrency.js
```

## Carga de leitura (RNF-04)

```bash
docker run --rm --network projeto-condominio_default \
  -v "$PWD/k6:/scripts" -w /scripts \
  -e UNIT_LOGIN=a-103 -e UNIT_PASSWORD="$APP_DEMO_PASSWORD" \
  -e ADMIN_LOGIN=admin@exemplo.test -e ADMIN_PASSWORD="$APP_SEED_ADMIN_PASSWORD" \
  -e AREA_ID=<id-de-uma-area-ativa> \
  grafana/k6 run read-load.js
```

Resultados registrados em `docs/10-resultados-k6.md`. Ao final, rode de novo
`bash scripts/demo-reset.sh --now ...` para limpar a reserva criada pelo
teste de concorrência.
