// F9-1 / RNF-04
//
// Mede p95 dos endpoints de leitura mais usados na navegacao (catalogo de
// areas, disponibilidade, "minhas reservas", agenda do ADMIN e dashboard)
// com 100 usuarios simultaneos, no ambiente de referencia (Docker local).
//
// Tokens obtidos 1x no setup() (1 login UNIT + 1 login ADMIN) para nao
// esbarrar no rate limit de 10/min de /auth/login (RNF-02) com 100 VUs.
//
// Como rodar: ver k6/README.md.

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://frontend/api/v1';
const UNIT_LOGIN = __ENV.UNIT_LOGIN || 'a-103';
const UNIT_PASSWORD = __ENV.UNIT_PASSWORD;
const ADMIN_LOGIN = __ENV.ADMIN_LOGIN || 'admin@exemplo.test';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD;
const AREA_ID = __ENV.AREA_ID; // id de uma area ativa (GET /areas)

export const options = {
  scenarios: {
    read_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 100 }, // rampa curta
        { duration: '40s', target: 100 }, // sustentado
        { duration: '10s', target: 0 },
      ],
    },
  },
  thresholds: {
    'http_req_duration{endpoint:areas_list}': ['p(95)<500'],
    'http_req_duration{endpoint:areas_detail}': ['p(95)<500'],
    'http_req_duration{endpoint:areas_availability}': ['p(95)<500'],
    'http_req_duration{endpoint:me_reservations}': ['p(95)<500'],
    'http_req_duration{endpoint:admin_reservations}': ['p(95)<500'],
    'http_req_duration{endpoint:dashboard_summary}': ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  if (!UNIT_PASSWORD || !ADMIN_PASSWORD) {
    throw new Error('Defina UNIT_PASSWORD e ADMIN_PASSWORD (-e).');
  }
  if (!AREA_ID) {
    throw new Error('Defina AREA_ID (id de uma area ativa, via GET /areas).');
  }

  const unitLogin = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ login: UNIT_LOGIN, password: UNIT_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (unitLogin.status !== 200) {
    throw new Error(`Login UNIT falhou (${unitLogin.status}): ${unitLogin.body}`);
  }

  const adminLogin = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ login: ADMIN_LOGIN, password: ADMIN_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (adminLogin.status !== 200) {
    throw new Error(`Login ADMIN falhou (${adminLogin.status}): ${adminLogin.body}`);
  }

  return {
    unitToken: unitLogin.json('accessToken'),
    adminToken: adminLogin.json('accessToken'),
  };
}

export default function (data) {
  const unitHeaders = { headers: { Authorization: `Bearer ${data.unitToken}` } };
  const adminHeaders = { headers: { Authorization: `Bearer ${data.adminToken}` } };

  const from = '2026-11-01';
  const to = '2026-11-30';
  const availFrom = '2026-11-10';
  const availTo = '2026-11-17'; // 7 dias

  const requests = [
    () =>
      http.get(`${BASE_URL}/areas`, {
        ...unitHeaders,
        tags: { endpoint: 'areas_list' },
      }),
    () =>
      http.get(`${BASE_URL}/areas/${AREA_ID}`, {
        ...unitHeaders,
        tags: { endpoint: 'areas_detail' },
      }),
    () =>
      http.get(
        `${BASE_URL}/areas/${AREA_ID}/availability?from=${availFrom}&to=${availTo}`,
        { ...unitHeaders, tags: { endpoint: 'areas_availability' } }
      ),
    () =>
      http.get(`${BASE_URL}/me/reservations?scope=upcoming`, {
        ...unitHeaders,
        tags: { endpoint: 'me_reservations' },
      }),
    () =>
      http.get(`${BASE_URL}/reservations?from=${from}&to=${to}`, {
        ...adminHeaders,
        tags: { endpoint: 'admin_reservations' },
      }),
    () =>
      http.get(`${BASE_URL}/dashboard/summary`, {
        ...adminHeaders,
        tags: { endpoint: 'dashboard_summary' },
      }),
  ];

  // Mistura as leituras: cada iteracao de VU dispara um endpoint diferente
  // (round-robin pelo __ITER), para todos ficarem sob carga ao longo do teste.
  const pick = requests[__ITER % requests.length];
  const res = pick();

  check(res, { 'status 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
  const endpoints = [
    'areas_list',
    'areas_detail',
    'areas_availability',
    'me_reservations',
    'admin_reservations',
    'dashboard_summary',
  ];
  let lines = '\nRESULTADO RNF-04 (p95 por endpoint, alvo < 500ms):\n';
  for (const ep of endpoints) {
    const metric = data.metrics[`http_req_duration{endpoint:${ep}}`];
    const p95 = metric ? metric.values['p(95)'].toFixed(1) : 'sem amostras';
    lines += `  ${ep}: p95=${p95}ms\n`;
  }
  console.log(lines);

  return {
    stdout: lines,
    'read-load-summary.json': JSON.stringify(data, null, 2),
  };
}
