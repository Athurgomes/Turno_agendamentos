// F9-1 / RNF-03 / RN-24 / D-57 / D-58
//
// Prova, com 50 requisicoes HTTP reais concorrentes (nao MockMvc, que
// serializa), que a exclusion constraint do Postgres (D-07) garante no
// maximo 1 reserva ativa por horario/area mesmo sob concorrencia real: as
// demais devem virar 409 RESERVATION_OVERLAP (D-57/D-58), nunca 500.
//
// Cenario de referencia (mesma logica, em JUnit/MockMvc real):
// backend/src/test/java/br/com/reservas/flow/ConcurrentReservationFlowTest.java
//
// Uma unica conta UNIT (login feito 1x no setup(), fora do rate limit de
// 10/min do /auth/login) dispara 50 tentativas de reserva simultaneas para o
// MESMO horario da MESMA area (Churrasqueira 2 - Cobertura, gratuita, sem
// fluxo de pagamento no caminho). RN-22 (limite de 3 reservas ativas por
// unidade) nao entra em jogo: no maximo 1 das 50 tentativas e aceita.
//
// Como rodar: ver k6/README.md.

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://frontend/api/v1';
const UNIT_LOGIN = __ENV.UNIT_LOGIN || 'a-103';
const UNIT_PASSWORD = __ENV.UNIT_PASSWORD; // obrigatorio, nunca hardcoded (APP_DEMO_PASSWORD)
const AREA_ID = __ENV.AREA_ID; // Churrasqueira 2 - Cobertura (gratuita), id do GET /areas
const RESERVATION_DATE = __ENV.RESERVATION_DATE || '2026-11-12';
const START_TIME = __ENV.START_TIME || '10:00';
const END_TIME = __ENV.END_TIME || '11:00';

const CONCURRENT_REQUESTS = 50;

export const options = {
  scenarios: {
    concurrency: {
      executor: 'shared-iterations',
      vus: CONCURRENT_REQUESTS,
      iterations: CONCURRENT_REQUESTS,
      maxDuration: '30s',
    },
  },
  thresholds: {
    // so documentamos os numeros; a verificacao de exatidao (1x201/49x409)
    // acontece em handleSummary, que falha o processo (exit code != 0) se
    // os numeros nao baterem.
    http_req_failed: ['rate<1'],
  },
};

const created = new Counter('reservation_created_201');
const conflicts = new Counter('reservation_conflict_409');
const unexpected = new Counter('reservation_unexpected_status');

export function setup() {
  if (!UNIT_PASSWORD) {
    throw new Error('Defina UNIT_PASSWORD (ex.: -e UNIT_PASSWORD=$APP_DEMO_PASSWORD).');
  }
  if (!AREA_ID) {
    throw new Error('Defina AREA_ID (id de "Churrasqueira 2 - Cobertura", via GET /areas).');
  }

  const loginRes = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ login: UNIT_LOGIN, password: UNIT_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (loginRes.status !== 200) {
    throw new Error(`Login falhou (${loginRes.status}): ${loginRes.body}`);
  }
  const body = loginRes.json();
  const token = body.accessToken;

  // Busca o morador principal da unidade logada em vez de depender de um
  // RESIDENT_ID fixo por variavel de ambiente (o id do morador e um UUID
  // gerado pelo seed, nao previsivel de fora).
  const unitRes = http.get(`${BASE_URL}/me/unit`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (unitRes.status !== 200) {
    throw new Error(`GET /me/unit falhou (${unitRes.status}): ${unitRes.body}`);
  }
  const residents = unitRes.json('residents');
  const primary = residents.find((r) => r.primary) || residents[0];
  if (!primary) {
    throw new Error('Unidade sem morador ativo.');
  }

  return { token, residentId: primary.id };
}

export default function (data) {
  const payload = JSON.stringify({
    areaId: AREA_ID,
    date: RESERVATION_DATE,
    startTime: START_TIME,
    endTime: END_TIME,
    residentId: data.residentId,
    guests: 2,
  });

  const res = http.post(`${BASE_URL}/reservations`, payload, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.token}`,
    },
  });

  if (res.status === 201) {
    created.add(1);
  } else if (res.status === 409) {
    conflicts.add(1);
    check(res, {
      '409 tem code RESERVATION_OVERLAP': (r) => {
        try {
          return r.json('code') === 'RESERVATION_OVERLAP';
        } catch (e) {
          return false;
        }
      },
    });
  } else {
    unexpected.add(1);
  }

  check(res, {
    'nao e 5xx': (r) => r.status < 500,
  });
}

export function handleSummary(data) {
  const createdCount = data.metrics.reservation_created_201
    ? data.metrics.reservation_created_201.values.count
    : 0;
  const conflictCount = data.metrics.reservation_conflict_409
    ? data.metrics.reservation_conflict_409.values.count
    : 0;
  const unexpectedCount = data.metrics.reservation_unexpected_status
    ? data.metrics.reservation_unexpected_status.values.count
    : 0;

  console.log(
    `\nRESULTADO RNF-03: ${createdCount}x201 / ${conflictCount}x409 / ${unexpectedCount}x outros (esperado: 1/49/0)\n`
  );

  const ok = createdCount === 1 && conflictCount === 49 && unexpectedCount === 0;

  return {
    stdout: `\n${ok ? 'PASSOU' : 'FALHOU'}: RNF-03 (1x201, 49x409, 0x outros)\n`,
    'concurrency-summary.json': JSON.stringify(data, null, 2),
  };
}
