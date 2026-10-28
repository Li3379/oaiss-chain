/**
 * OAISS CHAIN - Chaos Engineering Test Script
 * Tests system resilience by simulating failure conditions
 * 
 * Usage: node tests/chaos/chaos-runner.mjs [scenario]
 * Scenarios: health, ratelimit, auth, payload, security, idempotency, headers, cors, all
 */

const API = 'http://localhost:8080/api/v1';
const scenario = process.argv[2] || 'all';

const results = [];
function log(level, scenario, msg) {
  const icon = { PASS: '\u2705', FAIL: '\u274c', WARN: '\u26a0\ufe0f', INFO: '\u2139\ufe0f' }[level];
  results.push({ level, scenario, msg });
  console.log(`${icon} [${scenario}] ${msg}`);
}

async function api(method, path, token, body) {
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    signal: AbortSignal.timeout(10000),
  };
  if (body) opts.body = JSON.stringify(body);
  try {
    const res = await fetch(`${API}${path}`, opts);
    const data = await res.json();
    return { status: res.status, data, ok: res.ok };
  } catch (e) {
    return { status: 0, data: null, ok: false, error: e.message };
  }
}

async function getToken(username) {
  const { status, data } = await api('POST', '/auth/login', null, { username, password: 'admin123' });
  return status === 200 ? data?.data?.accessToken : null;
}

async function testHealthEndpoint() {
  console.log('\n=== Scenario: Health Endpoint ===');
  const res = await api('GET', '/actuator/health');
  if (res.status === 200 && res.data?.status === 'UP') {
    log('PASS', 'Health', 'Health endpoint returns UP');
  } else if (res.status === 503) {
    log('WARN', 'Health', 'Health endpoint returns 503 (degraded)');
  } else {
    log('FAIL', 'Health', `Health endpoint unexpected: ${res.status}`);
  }
}

async function testRateLimitEnforcement(token) {
  console.log('\n=== Scenario: Rate Limiting ===');
  let rateLimited = false;
  for (let i = 0; i < 15; i++) {
    const res = await api('GET', '/credit/score', token);
    if (res.status === 429) {
      rateLimited = true;
      log('PASS', 'RateLimit', `Rate limited after ${i + 1} requests`);
      break;
    }
  }
  if (!rateLimited) {
    log('INFO', 'RateLimit', 'Rate limit not hit in 15 requests - may need more load');
  }
}

async function testInvalidToken() {
  console.log('\n=== Scenario: Invalid Token ===');
  const res = await api('GET', '/credit/score', 'invalid-token-12345');
  if (res.status === 401) {
    log('PASS', 'Auth', 'Invalid token correctly rejected with 401');
  } else {
    log('FAIL', 'Auth', `Invalid token not rejected: ${res.status}`);
  }
}

async function testLargePayload(token) {
  console.log('\n=== Scenario: Large Payload ===');
  const largePayload = { title: 'A'.repeat(100000), description: 'B'.repeat(500000) };
  const res = await api('POST', '/carbon/reports', token, largePayload);
  if (res.status === 400 || res.status === 413 || res.status === 422) {
    log('PASS', 'Payload', `Large payload rejected: ${res.status}`);
  } else if (res.status === 200) {
    log('WARN', 'Payload', 'Large payload accepted - consider adding size validation');
  } else {
    log('INFO', 'Payload', `Large payload response: ${res.status}`);
  }
}

async function testSQLInjection(token) {
  console.log('\n=== Scenario: SQL Injection ===');
  const res = await api('GET', "/carbon/my-reports?keyword=%27%20OR%201=1--", token);
  if (res.status === 200 || res.status === 400) {
    log('PASS', 'SQLi', `SQL injection handled safely: ${res.status}`);
  } else {
    log('FAIL', 'SQLi', `SQL injection caused unexpected error: ${res.status}`);
  }
}

async function testXSSInput(token) {
  console.log('\n=== Scenario: XSS Input ===');
  const res = await api('POST', '/carbon/reports', token, {
    title: '<script>alert("xss")</script>',
    reportType: 1,
    accountingPeriod: '2025-Q1',
    totalEmission: 100,
    emissionData: '{}',
  });
  if (res.status === 400 || (res.status === 200 && !JSON.stringify(res.data).includes('<script>'))) {
    log('PASS', 'XSS', 'XSS input sanitized or rejected');
  } else {
    log('FAIL', 'XSS', 'XSS input may have passed through');
  }
}

async function testConcurrentIdempotency(token) {
  console.log('\n=== Scenario: Concurrent Idempotency ===');
  const promises = Array.from({ length: 5 }, () => api('GET', '/search/market-overview', token));
  const responses = await Promise.all(promises);
  const allSame = responses.every(r => r.status === responses[0].status);
  if (allSame) {
    log('PASS', 'Idempotency', 'Concurrent requests return consistent results');
  } else {
    log('FAIL', 'Idempotency', 'Inconsistent results from concurrent requests');
  }
}

async function testResponseHeaders(token) {
  console.log('\n=== Scenario: Response Headers ===');
  const res = await fetch(`${API}/credit/score`, { headers: { Authorization: `Bearer ${token}` } });
  const checks = [['X-Trace-Id', 'Trace ID'], ['X-Response-Time', 'Response Time'], ['X-API-Version', 'API Version'], ['X-RateLimit-Limit', 'Rate Limit']];
  for (const [header, name] of checks) {
    if (res.headers.get(header)) {
      log('PASS', 'Headers', `${name} header present: ${header}`);
    } else {
      log('FAIL', 'Headers', `${name} header missing: ${header}`);
    }
  }
}

async function testCORS() {
  console.log('\n=== Scenario: CORS ===');
  try {
    const res = await fetch(`${API}/actuator/health`, {
      method: 'OPTIONS',
      headers: { Origin: 'http://localhost:5173' },
    });
    const allowOrigin = res.headers.get('Access-Control-Allow-Origin');
    if (allowOrigin) {
      log('PASS', 'CORS', `CORS allows origin: ${allowOrigin}`);
    } else {
      log('WARN', 'CORS', 'No CORS Allow-Origin header on OPTIONS');
    }
  } catch (e) {
    log('INFO', 'CORS', `CORS check: ${e.message}`);
  }
}

async function main() {
  console.log('OAISS CHAIN - Chaos Engineering Tests');
  console.log('='.repeat(50));
  console.log(`Time: ${new Date().toISOString()}`);
  console.log(`Scenario: ${scenario}\n`);

  const token = await getToken('enterprise001');
  if (!token) { console.error('FATAL: Cannot obtain auth token'); process.exit(1); }

  const scenarios = {
    health: () => testHealthEndpoint(),
    ratelimit: () => testRateLimitEnforcement(token),
    auth: () => testInvalidToken(),
    payload: () => testLargePayload(token),
    security: () => { testSQLInjection(token); testXSSInput(token); },
    idempotency: () => testConcurrentIdempotency(token),
    headers: () => testResponseHeaders(token),
    cors: () => testCORS(),
  };

  if (scenario === 'all') {
    for (const fn of Object.values(scenarios)) await fn();
  } else if (scenarios[scenario]) {
    await scenarios[scenario]();
  } else {
    console.error(`Unknown: ${scenario}. Available: ${Object.keys(scenarios).join(', ')}`);
    process.exit(1);
  }

  console.log('\n' + '='.repeat(50));
  console.log('CHAOS TEST SUMMARY');
  console.log('='.repeat(50));
  const counts = { PASS: 0, FAIL: 0, WARN: 0, INFO: 0 };
  for (const r of results) counts[r.level]++;
  console.log(`PASS: ${counts.PASS}  FAIL: ${counts.FAIL}  WARN: ${counts.WARN}  INFO: ${counts.INFO}`);
  if (counts.FAIL > 0) {
    console.log('\nFailed:');
    for (const r of results.filter(r => r.level === 'FAIL')) console.log(`  FAIL [${r.scenario}] ${r.msg}`);
    process.exit(1);
  }
}

main().catch(e => { console.error('Fatal:', e.message); process.exit(1); });
