/**
 * Workflow-driven QA: tests actual business operations, not just page loads.
 * Tests CRUD flows, state transitions, and cross-role interactions.
 */
import { chromium } from 'playwright';
import { mkdirSync } from 'fs';
import { join } from 'path';

const API = 'http://localhost:8080/api/v1';
const WEB = 'http://localhost:5173';
const DIR = 'docs/qa-screenshots/workflow';
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

// === Test 1: Carbon Report Lifecycle ===
async function testCarbonReportLifecycle(entToken, revToken, adminToken) {
  console.log('\n=== Test 1: Carbon Report Lifecycle ===');

  // 1.1 Create report
  const createRes = await api('POST', '/carbon/reports', entToken, {
    title: `QA-TEST-${Date.now()}`,
    reportType: 1,
    accountingPeriod: '2025-Q1',
    totalEmission: 1500.5,
    scope1Emission: 800.0,
    scope2Emission: 500.0,
    scope3Emission: 200.5,
    emissionData: '{"source":"QA test","method":"direct"}',
    description: 'QA workflow test report',
  });

  if (createRes.status !== 200) {
    log('CRITICAL', 'CarbonReport', `Create failed: ${createRes.status} ${JSON.stringify(createRes.data).substring(0, 200)}`);
    return;
  }
  const reportId = createRes.data.data?.id;
  log('INFO', 'CarbonReport', `Created report ID=${reportId}`);

  // 1.2 List my reports
  const listRes = await api('GET', '/carbon/my-reports', entToken);
  if (listRes.status !== 200) {
    log('HIGH', 'CarbonReport', `List my-reports failed: ${listRes.status}`);
  } else {
    const count = listRes.data.data?.content?.length ?? 0;
    log('INFO', 'CarbonReport', `My reports count: ${count}`);
  }

  // 1.3 Submit report
  if (reportId) {
    const submitRes = await api('POST', `/carbon/reports/${reportId}/submit`, entToken);
    if (submitRes.status !== 200) {
      log('HIGH', 'CarbonReport', `Submit failed: ${submitRes.status} ${JSON.stringify(submitRes.data).substring(0, 200)}`);
    } else {
      log('INFO', 'CarbonReport', `Submitted report ID=${reportId}, status=${submitRes.data.data?.status}`);
    }

    // 1.4 Get report detail
    const detailRes = await api('GET', `/carbon/reports/${reportId}`, entToken);
    if (detailRes.status !== 200) {
      log('MEDIUM', 'CarbonReport', `Detail fetch failed: ${detailRes.status}`);
    } else {
      log('INFO', 'CarbonReport', `Detail OK: status=${detailRes.data.data?.status}, emission=${detailRes.data.data?.totalEmission}`);
    }

    // 1.5 Review (approve) as reviewer
    const reviewRes = await api('POST', '/carbon/review', revToken, {
      reportId,
      reviewResult: 3, // APPROVED
      reviewComment: 'QA test approval',
    });
    if (reviewRes.status !== 200) {
      log('HIGH', 'CarbonReport', `Review failed: ${reviewRes.status} ${JSON.stringify(reviewRes.data).substring(0, 200)}`);
    } else {
      log('INFO', 'CarbonReport', `Reviewed: status=${reviewRes.data.data?.status}`);
    }

    // 1.6 Certify as admin (5=ON_CHAIN to certify and push on-chain)
    const certifyRes = await api('POST', '/carbon/certify', adminToken, {
      reportId,
      reviewResult: 5, // ON_CHAIN
      reviewComment: 'QA test certification',
    });
    if (certifyRes.status !== 200) {
      log('HIGH', 'CarbonReport', `Certify failed: ${certifyRes.status} ${JSON.stringify(certifyRes.data).substring(0, 200)}`);
    } else {
      log('INFO', 'CarbonReport', `Certified: status=${certifyRes.data.data?.status}`);
    }

    // 1.7 Delete draft (cleanup)
    const delRes = await api('DELETE', `/carbon/reports/${reportId}`, entToken);
    if (delRes.status === 200) {
      log('INFO', 'CarbonReport', `Deleted report ID=${reportId}`);
    }
  }
}

// === Test 2: Credit Score ===
async function testCreditScore(entToken) {
  console.log('\n=== Test 2: Credit Score ===');

  const res = await api('GET', '/credit/my-score', entToken);
  if (res.status !== 200) {
    log('HIGH', 'CreditScore', `My score failed: ${res.status} ${JSON.stringify(res.data).substring(0, 200)}`);
    return;
  }
  log('INFO', 'CreditScore', `My score OK: ${JSON.stringify(res.data.data).substring(0, 100)}`);
}

// === Test 3: Carbon Coin ===
async function testCarbonCoin(entToken) {
  console.log('\n=== Test 3: Carbon Coin ===');

  const res = await api('GET', '/carbon-coin/account', entToken);
  if (res.status !== 200) {
    log('HIGH', 'CarbonCoin', `Account failed: ${res.status} ${JSON.stringify(res.data).substring(0, 200)}`);
    return;
  }
  log('INFO', 'CarbonCoin', `Account OK: balance=${res.data.data?.balance}`);
}

// === Test 4: Carbon Neutral Projects ===
async function testCarbonNeutral(entToken) {
  console.log('\n=== Test 4: Carbon Neutral Projects ===');

  const res = await api('GET', '/carbon-neutral/search', entToken);
  if (res.status !== 200) {
    log('HIGH', 'CarbonNeutral', `Search failed: ${res.status} ${JSON.stringify(res.data).substring(0, 200)}`);
    return;
  }
  log('INFO', 'CarbonNeutral', `Search OK`);
}

// === Test 5: Blockchain ===
async function testBlockchain(entToken) {
  console.log('\n=== Test 5: Blockchain ===');

  const statusRes = await api('GET', '/blockchain/status', entToken);
  if (statusRes.status !== 200) {
    log('HIGH', 'Blockchain', `Status failed: ${statusRes.status} ${JSON.stringify(statusRes.data).substring(0, 200)}`);
  } else {
    log('INFO', 'Blockchain', `Status OK`);
  }

  const txRes = await api('GET', '/blockchain/transactions', entToken);
  if (txRes.status !== 200) {
    log('HIGH', 'Blockchain', `Transactions failed: ${txRes.status} ${JSON.stringify(txRes.data).substring(0, 200)}`);
  } else {
    log('INFO', 'Blockchain', `Transactions OK`);
  }
}

// === Test 6: Digital Signature ===
async function testDigitalSignature(entToken) {
  console.log('\n=== Test 6: Digital Signature ===');

  // 6.1 Get keypair (should be null initially)
  const getRes = await api('GET', '/signature/keypair', entToken);
  if (getRes.status !== 200) {
    log('HIGH', 'Signature', `Get keypair failed: ${getRes.status}`);
    return;
  }
  log('INFO', 'Signature', `Get keypair: ${getRes.data.data ? 'exists' : 'null (expected)'}`);

  // 6.2 Generate keypair
  const genRes = await api('POST', '/signature/keypair/generate', entToken);
  if (genRes.status !== 200) {
    log('HIGH', 'Signature', `Generate failed: ${genRes.status} ${JSON.stringify(genRes.data).substring(0, 200)}`);
    return;
  }
  log('INFO', 'Signature', `Generated keypair: status=${genRes.data.data?.keyStatus}`);

  // 6.3 Get keypair again (should exist now)
  const getRes2 = await api('GET', '/signature/keypair', entToken);
  if (getRes2.status !== 200 || !getRes2.data.data) {
    log('HIGH', 'Signature', `Get after generate failed: ${getRes2.status}`);
    return;
  }
  log('INFO', 'Signature', `Keypair confirmed: version=${getRes2.data.data.keyVersion}, status=${getRes2.data.data.keyStatus}`);

  // 6.4 Sign data
  const signRes = await api('POST', '/signature/sign', entToken, '{"reportId":1,"totalEmission":1500.5}');
  if (signRes.status !== 200) {
    log('HIGH', 'Signature', `Sign failed: ${signRes.status} ${JSON.stringify(signRes.data).substring(0, 200)}`);
  } else {
    const sig = signRes.data.data?.signature;
    log('INFO', 'Signature', `Sign OK: signature=${sig ? sig.substring(0, 40) + '...' : 'null'}`);
  }

  // 6.5 Revoke keypair
  const delRes = await api('DELETE', '/signature/keypair', entToken);
  if (delRes.status !== 200) {
    log('HIGH', 'Signature', `Revoke failed: ${delRes.status}`);
  } else {
    log('INFO', 'Signature', `Revoked keypair`);
  }
}

// === Test 7: Trading ===
async function testTrading(entToken) {
  console.log('\n=== Test 7: Trading ===');

  const ordersRes = await api('GET', '/auction/orders', entToken);
  if (ordersRes.status !== 200) {
    log('HIGH', 'Trading', `Auction orders failed: ${ordersRes.status} ${JSON.stringify(ordersRes.data).substring(0, 200)}`);
  } else {
    log('INFO', 'Trading', `Auction orders OK`);
  }

  const txRes = await api('GET', '/trade/my-trades', entToken);
  if (txRes.status !== 200) {
    log('HIGH', 'Trading', `My trades failed: ${txRes.status} ${JSON.stringify(txRes.data).substring(0, 200)}`);
  } else {
    log('INFO', 'Trading', `My trades OK`);
  }
}

// === Test 8: Emission Ratings ===
async function testEmission(entToken) {
  console.log('\n=== Test 8: Emission Ratings ===');

  const res = await api('GET', '/emission/my-rating', entToken);
  if (res.status !== 200) {
    log('HIGH', 'Emission', `My rating failed: ${res.status} ${JSON.stringify(res.data).substring(0, 200)}`);
    return;
  }
  log('INFO', 'Emission', `My rating OK: ${JSON.stringify(res.data.data).substring(0, 100)}`);
}

// === Test 9: Admin Operations ===
async function testAdmin(adminToken) {
  console.log('\n=== Test 9: Admin Operations ===');

  const usersRes = await api('GET', '/admin/users', adminToken);
  if (usersRes.status !== 200) {
    log('HIGH', 'Admin', `List users failed: ${usersRes.status}`);
  } else {
    log('INFO', 'Admin', `Users list OK`);
  }

  const entRes = await api('GET', '/admin/enterprise-admission', adminToken);
  if (entRes.status !== 200) {
    log('HIGH', 'Admin', `Enterprise admission failed: ${entRes.status} ${JSON.stringify(entRes.data).substring(0, 200)}`);
  } else {
    log('INFO', 'Admin', `Enterprise admission OK`);
  }

  const statsRes = await api('GET', '/admin/statistics', adminToken);
  if (statsRes.status !== 200) {
    log('HIGH', 'Admin', `Statistics failed: ${statsRes.status} ${JSON.stringify(statsRes.data).substring(0, 200)}`);
  } else {
    log('INFO', 'Admin', `Statistics OK`);
  }
}

// === Test 10: Third Party ===
async function testThirdParty(tpToken) {
  console.log('\n=== Test 10: Third Party ===');

  const res = await api('GET', '/third-party/carbon-reports', tpToken);
  if (res.status !== 200) {
    log('HIGH', 'ThirdParty', `Reports failed: ${res.status} ${JSON.stringify(res.data).substring(0, 200)}`);
    return;
  }
  log('INFO', 'ThirdParty', `Reports OK`);
}

// === Test 11: Browser Workflow Screenshots ===
async function testBrowserWorkflows(browser) {
  console.log('\n=== Test 11: Browser Workflow Screenshots ===');

  const roles = [
    { user: 'enterprise001', role: 'ENTERPRISE', pages: [
      ['/enterprise/carbon/upload', 'upload'],
      ['/enterprise/trading/market', 'market'],
      ['/enterprise/credit/score', 'credit'],
      ['/enterprise/user/profile', 'profile'],
    ]},
    { user: 'admin', role: 'ADMIN', pages: [
      ['/admin/system/users', 'users'],
      ['/admin/data/statistics', 'stats'],
    ]},
    { user: 'reviewer001', role: 'REVIEWER', pages: [
      ['/auditor/audit/list', 'audit'],
    ]},
  ];

  for (const { user, role, pages } of roles) {
    const token = await getToken(user);
    if (!token) continue;

    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const p = await ctx.newPage();
    await p.goto(`${WEB}/official-home`, { waitUntil: 'networkidle', timeout: 15000 });
    await p.waitForTimeout(500);
    await p.evaluate((t) => {
      localStorage.setItem('access_token', t);
      localStorage.setItem('remember_me', 'true');
    }, token);
    await p.reload({ waitUntil: 'networkidle', timeout: 15000 });
    await p.waitForTimeout(1000);

    for (const [path, name] of pages) {
      try {
        await p.goto(`${WEB}${path}`, { waitUntil: 'networkidle', timeout: 20000 });
        await p.waitForTimeout(2000);
        await p.screenshot({ path: join(DIR, `${role.toLowerCase()}-${name}.png`), fullPage: true });
        log('INFO', 'Browser', `${role} ${path}: screenshot captured`);
      } catch (e) {
        log('MEDIUM', 'Browser', `${role} ${path}: ${e.message.substring(0, 100)}`);
      }
    }
    await ctx.close();
  }
}

async function main() {
  console.log('OAISS CHAIN — Workflow-Driven QA');
  console.log('='.repeat(50));
  console.log(`Time: ${new Date().toISOString()}\n`);

  // Get tokens
  const entToken = await getToken('enterprise001');
  const revToken = await getToken('reviewer001');
  const adminToken = await getToken('admin');
  const tpToken = await getToken('thirdparty001');

  // Run all tests
  await testCarbonReportLifecycle(entToken, revToken, adminToken);
  await testCreditScore(entToken);
  await testCarbonCoin(entToken);
  await testCarbonNeutral(entToken);
  await testBlockchain(entToken);
  await testDigitalSignature(entToken);
  await testTrading(entToken);
  await testEmission(entToken);
  await testAdmin(adminToken);
  await testThirdParty(tpToken);

  // Browser screenshots
  const browser = await chromium.launch({ headless: true });
  await testBrowserWorkflows(browser);
  await browser.close();

  // Summary
  console.log('\n' + '='.repeat(50));
  console.log('WORKFLOW QA SUMMARY');
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
    console.log('\n✅ No issues found — all workflow tests passed.');
  }
}

main().catch(e => { console.error('Fatal:', e.message); process.exit(1); });
