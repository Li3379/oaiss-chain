/**
 * Edge case & interaction QA: tests boundary conditions, error handling,
 * concurrent operations, token expiry, and frontend form interactions.
 */
import { chromium } from 'playwright';
import { mkdirSync } from 'fs';
import { join } from 'path';

const API = 'http://localhost:8080/api/v1';
const WEB = 'http://localhost:5173';
const DIR = 'docs/qa-screenshots/edge';
mkdirSync(DIR, { recursive: true });

const issues = [];
function log(level, cat, desc) {
  issues.push({ level, cat, desc });
  const icon = { CRITICAL: '🔴', HIGH: '🟠', MEDIUM: '🟡', INFO: 'ℹ️' }[level];
  console.log(`${icon} [${cat}] ${desc}`);
}

async function api(method, path, token, body) {
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  };
  if (body) opts.body = JSON.stringify(body);
  const res = await fetch(`${API}${path}`, opts);
  const data = await res.json();
  return { status: res.status, data };
}

async function getToken(username) {
  const { status, data } = await api('POST', '/auth/login', null, { username, password: 'admin123' });
  if (status === 200 && data.data?.accessToken) return data.data.accessToken;
  log('CRITICAL', 'Auth', `Login failed for ${username}: ${status}`);
  return null;
}

// === Test 1: Edge Case - Invalid Inputs ===
async function testInvalidInputs(token) {
  console.log('\n=== Test 1: Invalid Inputs ===');

  // 1.1 Create report with empty body
  const emptyRes = await api('POST', '/carbon/reports', token, {});
  if (emptyRes.status !== 400) {
    log('HIGH', 'Edge', `Empty report body should return 400, got ${emptyRes.status}`);
  } else {
    log('INFO', 'Edge', `Empty report body correctly returns 400 with validation errors`);
  }

  // 1.2 Create report with XSS payload
  const xssRes = await api('POST', '/carbon/reports', token, {
    title: '<script>alert("xss")</script>',
    reportType: 1,
    accountingPeriod: '2025-Q1',
    emissionData: '{"source":"test"}',
  });
  if (xssRes.status === 200) {
    // Check if the title was sanitized
    const title = xssRes.data.data?.title;
    if (title && title.includes('<script>')) {
      log('CRITICAL', 'Security', `XSS payload stored unsanitized: ${title}`);
    } else {
      log('INFO', 'Security', `XSS payload sanitized: ${title}`);
    }
  } else if (xssRes.status === 400) {
    log('INFO', 'Security', `XSS payload rejected with 400: ${xssRes.data.message}`);
  }

  // 1.3 Get non-existent report
  const notFoundRes = await api('GET', '/carbon/reports/99999', token);
  if (notFoundRes.status !== 404 && notFoundRes.status !== 400) {
    log('MEDIUM', 'Edge', `Non-existent report should return 404/400, got ${notFoundRes.status}`);
  } else {
    log('INFO', 'Edge', `Non-existent report returns ${notFoundRes.status} (expected)`);
  }

  // 1.4 Submit non-existent report
  const submitBadRes = await api('POST', '/carbon/reports/99999/submit', token);
  if (submitBadRes.status === 200) {
    log('HIGH', 'Edge', `Submitting non-existent report returned 200 (should fail)`);
  } else {
    log('INFO', 'Edge', `Submitting non-existent report returns ${submitBadRes.status}`);
  }

  // 1.5 Negative pagination (400 is correct — rejects invalid params)
  const negPageRes = await api('GET', '/carbon/my-reports?page=-1&size=-5', token);
  if (negPageRes.status === 400) {
    log('INFO', 'Edge', `Negative pagination correctly returns 400`);
  } else if (negPageRes.status === 200) {
    log('INFO', 'Edge', `Negative pagination clamped to valid range`);
  } else {
    log('MEDIUM', 'Edge', `Negative pagination returns ${negPageRes.status} (expected 400 or 200)`);
  }

  // 1.6 Oversized page size
  const bigPageRes = await api('GET', '/carbon/my-reports?page=1&size=10000', token);
  if (bigPageRes.status !== 200) {
    log('MEDIUM', 'Edge', `Oversized page size returns ${bigPageRes.status}`);
  } else {
    log('INFO', 'Edge', `Oversized page size handled gracefully`);
  }

  // 1.7 SQL injection in search
  const sqlRes = await api('GET', "/carbon/my-reports?title=' OR 1=1 --", token);
  if (sqlRes.status !== 200) {
    log('MEDIUM', 'Edge', `SQL injection attempt returns ${sqlRes.status}`);
  } else {
    log('INFO', 'Security', `SQL injection attempt handled safely`);
  }

  // 1.8 Very long input
  const longTitle = 'A'.repeat(500);
  const longRes = await api('POST', '/carbon/reports', token, {
    title: longTitle,
    reportType: 1,
    accountingPeriod: '2025-Q1',
    emissionData: '{}',
  });
  if (longRes.status === 400) {
    log('INFO', 'Edge', `Oversized title correctly rejected with 400`);
  } else if (longRes.status === 200) {
    log('MEDIUM', 'Edge', `Oversized title accepted (500 chars) - check @Size validation`);
  }
}

// === Test 2: Authentication Edge Cases ===
async function testAuthEdgeCases() {
  console.log('\n=== Test 2: Auth Edge Cases ===');

  // 2.1 No token
  const noTokenRes = await api('GET', '/auth/me');
  if (noTokenRes.status !== 401 && noTokenRes.status !== 403) {
    log('HIGH', 'Auth', `No token should return 401/403, got ${noTokenRes.status}`);
  } else {
    log('INFO', 'Auth', `No token returns ${noTokenRes.status} (expected)`);
  }

  // 2.2 Invalid token
  const badTokenRes = await api('GET', '/auth/me', 'invalid.jwt.token');
  if (badTokenRes.status !== 401 && badTokenRes.status !== 403) {
    log('HIGH', 'Auth', `Invalid token should return 401/403, got ${badTokenRes.status}`);
  } else {
    log('INFO', 'Auth', `Invalid token returns ${badTokenRes.status} (expected)`);
  }

  // 2.3 Wrong password
  const wrongPwdRes = await api('POST', '/auth/login', null, { username: 'admin', password: 'wrong' });
  if (wrongPwdRes.status !== 400 && wrongPwdRes.status !== 401) {
    log('HIGH', 'Auth', `Wrong password should return 400/401, got ${wrongPwdRes.status}`);
  } else {
    log('INFO', 'Auth', `Wrong password returns ${wrongPwdRes.status} (expected)`);
  }

  // 2.4 Non-existent user
  const noUserRes = await api('POST', '/auth/login', null, { username: 'nonexistent', password: 'admin123' });
  if (noUserRes.status !== 400 && noUserRes.status !== 401) {
    log('HIGH', 'Auth', `Non-existent user should return 400/401, got ${noUserRes.status}`);
  } else {
    log('INFO', 'Auth', `Non-existent user returns ${noUserRes.status} (expected)`);
  }

  // 2.5 Refresh token (backend reads from Refresh-Token header, not body)
  const loginRes = await api('POST', '/auth/login', null, { username: 'enterprise001', password: 'admin123' });
  if (loginRes.status === 200 && loginRes.data.data?.refreshToken) {
    const refreshToken = loginRes.data.data.refreshToken;
    const refreshRes = await fetch(`${API}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Refresh-Token': refreshToken },
    });
    const refreshData = await refreshRes.json();
    if (refreshRes.status === 200 && refreshData.data?.accessToken) {
      log('INFO', 'Auth', `Token refresh works`);
    } else {
      log('HIGH', 'Auth', `Token refresh failed: ${refreshRes.status} ${JSON.stringify(refreshData).substring(0, 100)}`);
    }
  }
}

// === Test 3: Concurrent Operations ===
async function testConcurrentOperations(token) {
  console.log('\n=== Test 3: Concurrent Operations ===');

  // 3.1 Parallel report creation
  const startTime = Date.now();
  const promises = [];
  for (let i = 0; i < 5; i++) {
    promises.push(api('POST', '/carbon/reports', token, {
      title: `CONCURRENT-TEST-${Date.now()}-${i}`,
      reportType: 1,
      accountingPeriod: '2025-Q1',
      emissionData: `{"source":"concurrent-test-${i}"}`,
    }));
  }
  const results = await Promise.all(promises);
  const successCount = results.filter(r => r.status === 200).length;
  const failCount = results.filter(r => r.status !== 200).length;
  const elapsed = Date.now() - startTime;
  log('INFO', 'Concurrent', `5 parallel creates: ${successCount} success, ${failCount} fail in ${elapsed}ms`);

  if (failCount > 0) {
    for (const r of results.filter(r => r.status !== 200)) {
      log('MEDIUM', 'Concurrent', `  Fail: ${r.status} ${JSON.stringify(r.data).substring(0, 100)}`);
    }
  }

  // 3.2 Concurrent credit score read
  const creditPromises = [];
  for (let i = 0; i < 10; i++) {
    creditPromises.push(api('GET', '/credit/my-score', token));
  }
  const creditResults = await Promise.all(creditPromises);
  const creditOk = creditResults.filter(r => r.status === 200).length;
  log('INFO', 'Concurrent', `10 parallel credit reads: ${creditOk}/10 success`);

  // 3.3 Concurrent keypair operations (should be serialized by lock)
  const keyPromises = [
    api('POST', '/signature/keypair/generate', token),
    api('GET', '/signature/keypair', token),
  ];
  const keyResults = await Promise.all(keyPromises);
  const keyOk = keyResults.filter(r => r.status === 200).length;
  log('INFO', 'Concurrent', `Concurrent keypair gen+read: ${keyOk}/2 success`);
}

// === Test 4: Role-Based API Access ===
async function testRoleBasedAPIAccess() {
  console.log('\n=== Test 4: Role-Based API Access ===');

  const roles = [
    { user: 'enterprise001', role: 'ENTERPRISE', tests: [
      { path: '/admin/users', method: 'GET', expect: [403] },
      { path: '/credit/my-score', method: 'GET', expect: [200] },
      { path: '/carbon/my-reports', method: 'GET', expect: [200] },
      { path: '/carbon-coin/account', method: 'GET', expect: [200] },
      { path: '/carbon-neutral/search', method: 'GET', expect: [200] },
    ]},
    { user: 'admin', role: 'ADMIN', tests: [
      { path: '/credit/my-score', method: 'GET', expect: [403, 500] },
      { path: '/admin/users', method: 'GET', expect: [200] },
      { path: '/admin/statistics', method: 'GET', expect: [200] },
      { path: '/carbon/my-reports', method: 'GET', expect: [403, 500] },
    ]},
    { user: 'reviewer001', role: 'REVIEWER', tests: [
      { path: '/admin/users', method: 'GET', expect: [403] },
      { path: '/carbon/my-reports', method: 'GET', expect: [403, 500] },
    ]},
    { user: 'thirdparty001', role: 'THIRD_PARTY', tests: [
      { path: '/admin/users', method: 'GET', expect: [403] },
      { path: '/carbon/my-reports', method: 'GET', expect: [403, 500] },
      { path: '/third-party/carbon-reports', method: 'GET', expect: [200] },
    ]},
  ];

  for (const { user, role, tests } of roles) {
    const token = await getToken(user);
    if (!token) continue;
    for (const { path, method, expect } of tests) {
      const res = await api(method, path, token);
      if (expect.includes(res.status)) {
        log('INFO', 'RoleAccess', `${role} ${method} ${path} → ${res.status} (expected)`);
      } else {
        log('HIGH', 'RoleAccess', `${role} ${method} ${path} → ${res.status} (expected ${expect.join('/')})`);
      }
    }
  }
}

// === Test 5: Browser Interaction Flows ===
async function testBrowserInteractions(browser) {
  console.log('\n=== Test 5: Browser Interaction Flows ===');

  const token = await getToken('enterprise001');
  if (!token) return;

  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();

  // Set up auth
  await page.goto(`${WEB}/official-home`, { waitUntil: 'networkidle', timeout: 15000 });
  await page.evaluate((t) => {
    localStorage.setItem('access_token', t);
    localStorage.setItem('remember_me', 'true');
  }, token);
  await page.reload({ waitUntil: 'networkidle', timeout: 15000 });
  await page.waitForTimeout(1000);

  // 5.1 Navigate through all enterprise pages
  const pages = [
    '/enterprise/carbon/upload',
    '/enterprise/trading/market',
    '/enterprise/credit/score',
    '/enterprise/user/profile',
    '/enterprise/carbon-neutral/projects',
    '/enterprise/emission/data',
  ];

  for (const path of pages) {
    try {
      await page.goto(`${WEB}${path}`, { waitUntil: 'networkidle', timeout: 20000 });
      await page.waitForTimeout(2000);

      // Check for error messages
      const errors = await page.locator('.el-message--error:visible').allTextContents();
      if (errors.length > 0) {
        log('HIGH', 'Browser', `${path}: error messages: ${errors.join('; ').substring(0, 200)}`);
      }

      // Check for loading spinners stuck
      const spinners = await page.locator('.el-loading-mask:visible').count();
      if (spinners > 0) {
        await page.waitForTimeout(3000);
        const stillSpinning = await page.locator('.el-loading-mask:visible').count();
        if (stillSpinning > 0) {
          log('MEDIUM', 'Browser', `${path}: loading spinner stuck`);
        }
      }

      // Check for empty tables (where data should exist)
      const tables = await page.locator('.el-table').count();
      if (tables > 0) {
        const emptyBlocks = await page.locator('.el-table__empty-text').count();
        if (emptyBlocks > 0) {
          const emptyText = await page.locator('.el-table__empty-text').first().innerText();
          log('INFO', 'Browser', `${path}: table shows: "${emptyText}"`);
        }
      }

      await page.screenshot({ path: join(DIR, `interaction-${path.replace(/\//g, '_')}.png`), fullPage: true });
      log('INFO', 'Browser', `${path}: OK`);
    } catch (e) {
      log('MEDIUM', 'Browser', `${path}: ${e.message.substring(0, 100)}`);
    }
  }

  // 5.2 Test form validation on carbon upload page
  await page.goto(`${WEB}/enterprise/carbon/upload`, { waitUntil: 'networkidle', timeout: 20000 });
  await page.waitForTimeout(2000);

  // First click "创建" button to open the dialog
  const createBtn = page.locator('button').filter({ hasText: /创建|Create/ }).first();
  if (await createBtn.count() > 0) {
    await createBtn.click();
    await page.waitForTimeout(1000);

    // Now click the submit button inside the dialog without filling form
    const submitBtn = page.locator('.el-dialog button').filter({ hasText: /创建|Create|提交|Submit/ }).first();
    if (await submitBtn.count() > 0) {
      await submitBtn.click();
      await page.waitForTimeout(1000);
      const validationErrors = await page.locator('.el-form-item__error:visible').allTextContents();
      if (validationErrors.length > 0) {
        log('INFO', 'Browser', `Form validation works: ${validationErrors.join(', ').substring(0, 200)}`);
      } else {
        log('MEDIUM', 'Browser', `No form validation errors shown after empty submit`);
      }
    }
  }

  await ctx.close();
}

// === Test 6: API Response Consistency ===
async function testAPIResponseConsistency(token) {
  console.log('\n=== Test 6: API Response Consistency ===');

  // 6.1 Check response envelope format
  const endpoints = [
    { path: '/auth/me', desc: 'auth/me' },
    { path: '/credit/my-score', desc: 'credit/my-score' },
    { path: '/carbon-coin/account', desc: 'carbon-coin/account' },
    { path: '/carbon-neutral/search', desc: 'carbon-neutral/search' },
    { path: '/blockchain/status', desc: 'blockchain/status' },
    { path: '/signature/keypair', desc: 'signature/keypair' },
  ];

  for (const { path, desc } of endpoints) {
    const res = await api('GET', path, token);
    if (res.status !== 200) continue;

    const envelope = res.data;
    if (!('code' in envelope)) {
      log('MEDIUM', 'Consistency', `${desc}: missing 'code' in response envelope`);
    }
    if (!('message' in envelope)) {
      log('MEDIUM', 'Consistency', `${desc}: missing 'message' in response envelope`);
    }
    if (!('meta' in envelope)) {
      log('MEDIUM', 'Consistency', `${desc}: missing 'meta' in response envelope`);
    }
    if (envelope.code !== 200) {
      log('MEDIUM', 'Consistency', `${desc}: code=${envelope.code} (expected 200)`);
    }
  }

  // 6.2 Check pagination format
  const pagedRes = await api('GET', '/carbon/my-reports', token);
  if (pagedRes.status === 200 && pagedRes.data.data) {
    const page = pagedRes.data.data;
    const expectedFields = ['content', 'totalElements', 'totalPages', 'number', 'size'];
    for (const field of expectedFields) {
      if (!(field in page)) {
        log('MEDIUM', 'Consistency', `Pagination missing field: ${field}`);
      }
    }
    log('INFO', 'Consistency', `Pagination format OK: ${page.content?.length} items, page ${page.number}/${page.totalPages}`);
  }
}

async function main() {
  console.log('OAISS CHAIN — Edge Case & Interaction QA');
  console.log('='.repeat(50));
  console.log(`Time: ${new Date().toISOString()}\n`);

  const token = await getToken('enterprise001');
  const adminToken = await getToken('admin');

  await testInvalidInputs(token);
  await testAuthEdgeCases();
  await testConcurrentOperations(token);
  await testRoleBasedAPIAccess();

  const browser = await chromium.launch({ headless: true });
  await testBrowserInteractions(browser);
  await browser.close();

  await testAPIResponseConsistency(token);

  // Summary
  console.log('\n' + '='.repeat(50));
  console.log('EDGE CASE QA SUMMARY');
  console.log('='.repeat(50));
  const counts = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, INFO: 0 };
  for (const i of issues) counts[i.level] = (counts[i.level] || 0) + 1;
  console.log(`🔴 CRITICAL: ${counts.CRITICAL}`);
  console.log(`🟠 HIGH:     ${counts.HIGH}`);
  console.log(`🟡 MEDIUM:   ${counts.MEDIUM}`);
  console.log(`ℹ️  INFO:     ${counts.INFO}`);

  const realIssues = issues.filter(i => i.level !== 'INFO');
  if (realIssues.length > 0) {
    console.log('\nIssues requiring attention:');
    for (const i of realIssues) {
      console.log(`  [${i.level}] ${i.cat}: ${i.desc}`);
    }
  } else {
    console.log('\n✅ No issues found — all edge case tests passed.');
  }
}

main().catch(e => { console.error('Fatal:', e.message); process.exit(1); });
