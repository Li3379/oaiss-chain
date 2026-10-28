import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const apiLatency = new Trend('api_latency', true);

// Configuration
const BASE_URL = __ENV.API_BASE_URL || 'http://localhost:8080/api/v1';
const PASSWORD = __ENV.TEST_PASSWORD || 'admin123';

// Smoke test: minimal load to verify functionality
export const options = {
  vus: 1,
  duration: '1m',
  thresholds: {
    http_req_duration: ['p(95)<500'],
    errors: ['rate<0.1'],
    api_latency: ['p(95)<500'],
  },
};

function getToken(username) {
  const res = http.post(${BASE_URL}/auth/login, JSON.stringify({
    username,
    password: PASSWORD,
  }), { headers: { 'Content-Type': 'application/json' } });

  if (res.status === 200) {
    const body = JSON.parse(res.body);
    return body.data?.accessToken;
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
  const enterpriseToken = getToken('enterprise001');
  const adminToken = getToken('admin');
  const reviewerToken = getToken('reviewer001');
  const thirdPartyToken = getToken('thirdparty001');

  if (!enterpriseToken || !adminToken) {
    throw new Error('Failed to obtain auth tokens - ensure backend is running with test data');
  }

  return { enterpriseToken, adminToken, reviewerToken, thirdPartyToken };
}

export default function (data) {
  const scenarios = [
    // Auth endpoints
    () => {
      const res = http.post(${BASE_URL}/auth/login, JSON.stringify({
        username: 'enterprise001',
        password: PASSWORD,
      }), { headers: { 'Content-Type': 'application/json' } });
      check(res, { 'login status 200': (r) => r.status === 200 });
      errorRate.add(res.status !== 200);
      apiLatency.add(res.timings.duration);
    },

    // Carbon reports list
    () => {
      const res = http.get(${BASE_URL}/carbon/my-reports?page=1&size=10, authHeaders(data.enterpriseToken));
      check(res, { 'my-reports status 200': (r) => r.status === 200 });
      errorRate.add(res.status !== 200);
      apiLatency.add(res.timings.duration);
    },

    // Trading market orders
    () => {
      const res = http.get(${BASE_URL}/trade/auction-orders?page=1&size=10, authHeaders(data.enterpriseToken));
      check(res, { 'auction-orders status 200': (r) => r.status === 200 });
      errorRate.add(res.status !== 200);
      apiLatency.add(res.timings.duration);
    },

    // Credit score
    () => {
      const res = http.get(${BASE_URL}/credit/score, authHeaders(data.enterpriseToken));
      check(res, { 'credit-score status 200': (r) => r.status === 200 });
      errorRate.add(res.status !== 200);
      apiLatency.add(res.timings.duration);
    },

    // Admin users list
    () => {
      const res = http.get(${BASE_URL}/admin/users?page=1&size=10, authHeaders(data.adminToken));
      check(res, { 'admin-users status 200': (r) => r.status === 200 });
      errorRate.add(res.status !== 200);
      apiLatency.add(res.timings.duration);
    },

    // Search market overview
    () => {
      const res = http.get(${BASE_URL}/search/market-overview, authHeaders(data.enterpriseToken));
      check(res, { 'market-overview status 200': (r) => r.status === 200 });
      errorRate.add(res.status !== 200);
      apiLatency.add(res.timings.duration);
    },

    // Health check (no auth)
    () => {
      const res = http.get(${BASE_URL}/actuator/health);
      check(res, { 'health status 200': (r) => r.status === 200 });
      errorRate.add(res.status !== 200);
      apiLatency.add(res.timings.duration);
    },
  ];

  const scenario = scenarios[Math.floor(Math.random() * scenarios.length)];
  scenario();
  sleep(1);
}

export function handleSummary(data) {
  return {
    'tests/load/smoke-summary.json': JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}

function textSummary(data) {
  const lines = [];
  lines.push('');
  lines.push('=== OAISS CHAIN Load Test Summary ===');
  lines.push('');
  for (const [name, metric] of Object.entries(data.metrics)) {
    if (metric.values) {
      if (metric.values.avg !== undefined) {
        lines.push(  : avg=ms p95=ms max=ms);
      }
      if (metric.values.rate !== undefined) {
        lines.push(  : %);
      }
    }
  }
  lines.push('');
  return lines.join('\n');
}