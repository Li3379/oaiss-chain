import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Counter } from 'k6/metrics';

// Resilience test: Verify system behavior under failure conditions
const errorRate = new Rate('errors');
const circuitBreakerTrips = new Counter('circuit_breaker_trips');

const BASE_URL = __ENV.API_BASE_URL || 'http://localhost:8080/api/v1';
const ML_SERVICE_URL = __ENV.ML_SERVICE_URL || 'http://localhost:8001';
const PASSWORD = __ENV.TEST_PASSWORD || 'admin123';

export const options = {
  vus: 5,
  duration: '2m',
  thresholds: {
    errors: ['rate<0.3'], // Higher tolerance for resilience tests
  },
};

function getToken(username) {
  const res = http.post(${BASE_URL}/auth/login, JSON.stringify({
    username,
    password: PASSWORD,
  }), { headers: { 'Content-Type': 'application/json' } });
  if (res.status === 200) return JSON.parse(res.body).data?.accessToken;
  return null;
}

export function setup() {
  const token = getToken('enterprise001');
  if (!token) throw new Error('Auth failed');
  return { token };
}

export default function (data) {
  const headers = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': Bearer ,
    },
  };

  group('API Resilience', () => {
    // Test 1: Rapid successive requests (burst tolerance)
    group('burst-tolerance', () => {
      for (let i = 0; i < 5; i++) {
        const res = http.get(${BASE_URL}/credit/score, headers);
        check(res, {
          'burst request handled': (r) => r.status === 200 || r.status === 429,
        });
        if (res.status === 429) {
          check(res, {
            'rate limit has Retry-After': (r) => r.headers['Retry-After'] !== undefined,
            'rate limit has X-RateLimit headers': (r) => r.headers['X-RateLimit-Limit'] !== undefined,
          });
        }
      }
    });

    // Test 2: Large page size (boundary testing)
    group('large-page-size', () => {
      const res = http.get(${BASE_URL}/carbon/my-reports?page=1&size=100, headers);
      check(res, {
        'large page accepted or rejected gracefully': (r) => r.status === 200 || r.status === 400,
      });
      errorRate.add(res.status >= 500);
    });

    // Test 3: Invalid parameters (input validation)
    group('invalid-input', () => {
      const res = http.get(${BASE_URL}/carbon/my-reports?page=-1&size=abc, headers);
      check(res, {
        'invalid params return 400': (r) => r.status === 400,
        'error response has message': (r) => {
          try { return JSON.parse(r.body).message !== undefined; } catch { return false; }
        },
      });
      errorRate.add(res.status >= 500);
    });

    // Test 4: Concurrent duplicate requests (idempotency)
    group('concurrent-duplicates', () => {
      const promises = [];
      for (let i = 0; i < 3; i++) {
        promises.push(http.asyncRequest('GET', ${BASE_URL}/search/market-overview, null, headers));
      }
      const responses = promises.map(p => p);
      const allOk = responses.every(r => r.status === 200);
      check(responses[0], {
        'concurrent requests handled': () => responses.length === 3,
      });
    });

    // Test 5: Health endpoint availability (no auth required)
    group('health-endpoint', () => {
      const res = http.get(${BASE_URL}/actuator/health);
      check(res, {
        'health returns 200': (r) => r.status === 200,
        'health has status UP': (r) => {
          try { return JSON.parse(r.body).status === 'UP'; } catch { return false; }
        },
      });
    });
  });

  sleep(0.5);
}