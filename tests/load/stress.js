import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const apiLatency = new Trend('api_latency', true);

const BASE_URL = __ENV.API_BASE_URL || 'http://localhost:8080/api/v1';
const PASSWORD = __ENV.TEST_PASSWORD || 'admin123';

// Stress test: ramp up to find breaking point
export const options = {
  stages: [
    { duration: '30s', target: 10 },   // Ramp up to 10 VUs
    { duration: '1m', target: 10 },     // Stay at 10 VUs
    { duration: '30s', target: 25 },    // Ramp up to 25 VUs
    { duration: '1m', target: 25 },     // Stay at 25 VUs
    { duration: '30s', target: 50 },    // Ramp up to 50 VUs
    { duration: '1m', target: 50 },     // Stay at 50 VUs (stress)
    { duration: '30s', target: 0 },     // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    errors: ['rate<0.15'],
    api_latency: ['p(95)<1000'],
  },
};

function getToken(username) {
  const res = http.post(${BASE_URL}/auth/login, JSON.stringify({
    username,
    password: PASSWORD,
  }), { headers: { 'Content-Type': 'application/json' } });

  if (res.status === 200) {
    return JSON.parse(res.body).data?.accessToken;
  }
  return null;
}

function authHeaders(token) {
  return {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': Bearer ,
    },
  };
}

export function setup() {
  const tokens = {
    enterprise: getToken('enterprise001'),
    admin: getToken('admin'),
    reviewer: getToken('reviewer001'),
  };

  if (!tokens.enterprise || !tokens.admin) {
    throw new Error('Auth failed - ensure backend is running with test data');
  }

  return tokens;
}

// Weighted scenario selection reflecting real usage patterns
const SCENARIOS = [
  { weight: 30, name: 'my-reports', fn: (t) => http.get(${BASE_URL}/carbon/my-reports?page=1&size=10, authHeaders(t.enterprise)) },
  { weight: 20, name: 'auction-orders', fn: (t) => http.get(${BASE_URL}/trade/auction-orders?page=1&size=10, authHeaders(t.enterprise)) },
  { weight: 15, name: 'credit-score', fn: (t) => http.get(${BASE_URL}/credit/score, authHeaders(t.enterprise)) },
  { weight: 10, name: 'market-overview', fn: (t) => http.get(${BASE_URL}/search/market-overview, authHeaders(t.enterprise)) },
  { weight: 10, name: 'admin-users', fn: (t) => http.get(${BASE_URL}/admin/users?page=1&size=10, authHeaders(t.admin)) },
  { weight: 10, name: 'health', fn: () => http.get(${BASE_URL}/actuator/health) },
  { weight: 5, name: 'login', fn: () => http.post(${BASE_URL}/auth/login, JSON.stringify({ username: 'enterprise001', password: PASSWORD }), { headers: { 'Content-Type': 'application/json' } }) },
];

const totalWeight = SCENARIOS.reduce((sum, s) => sum + s.weight, 0);

function selectScenario() {
  let rand = Math.random() * totalWeight;
  for (const s of SCENARIOS) {
    rand -= s.weight;
    if (rand <= 0) return s;
  }
  return SCENARIOS[0];
}

export default function (tokens) {
  const scenario = selectScenario();
  const res = scenario.fn(tokens);

  const success = res.status >= 200 && res.status < 400;
  check(res, { [${scenario.name} success]: () => success });
  errorRate.add(!success);
  apiLatency.add(res.timings.duration);

  sleep(Math.random() * 2 + 0.5); // Random think time 0.5-2.5s
}

export function handleSummary(data) {
  return {
    'tests/load/stress-summary.json': JSON.stringify(data, null, 2),
  };
}