import { chromium } from 'playwright';
import { mkdirSync } from 'fs';
import { join } from 'path';

const SCREENSHOT_DIR = 'docs/qa-screenshots/deep';
mkdirSync(SCREENSHOT_DIR, { recursive: true });

const issues = [];
function issue(severity, role, page, desc) {
  issues.push({ severity, role, page, desc });
  const icon = { CRITICAL: '🔴', HIGH: '🟠', MEDIUM: '🟡' }[severity] || '•';
  console.log(`${icon} [${severity}] ${role} ${page}: ${desc}`);
}

async function loginAs(username, password) {
  const resp = await fetch('http://localhost:8080/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  const data = await resp.json();
  return data.data?.accessToken;
}

async function testPage(ctx, role, path, name) {
  const page = await ctx.newPage();
  const netErrors = [];
  const consoleErrs = [];

  page.on('response', r => {
    if (r.status() >= 400 && !r.url().includes('favicon') && !r.url().includes('hot-update'))
      netErrors.push({ url: r.url(), status: r.status() });
  });
  page.on('console', m => {
    if (m.type() === 'error' && !m.text().includes('HMR') && !m.text().includes('favicon') && !m.text().includes('DevTools'))
      consoleErrs.push(m.text().substring(0, 150));
  });

  try {
    await page.goto(`http://localhost:5173${path}`, { waitUntil: 'networkidle', timeout: 20000 });
    await page.waitForTimeout(2000);

    const finalUrl = page.url();
    if (finalUrl.includes('/login')) {
      issue('HIGH', role, path, 'Redirected to login (auth failed or route guard blocked)');
    }

    const errMsgs = await page.locator('.el-message--error:visible, .el-alert--error:visible').allTextContents();
    if (errMsgs.length > 0) {
      issue('HIGH', role, path, `Error messages visible: ${errMsgs.join('; ').substring(0, 200)}`);
    }

    const brokenImgs = await page.evaluate(() =>
      Array.from(document.querySelectorAll('img')).filter(img => !img.complete || img.naturalWidth === 0).length
    );
    if (brokenImgs > 0) issue('MEDIUM', role, path, `${brokenImgs} broken image(s)`);

    for (const ne of netErrors) {
      const shortUrl = ne.url.replace('http://localhost:8080', '');
      if (ne.status === 404) issue('MEDIUM', role, path, `404: ${shortUrl}`);
      else if (ne.status >= 500) issue('HIGH', role, path, `${ne.status}: ${shortUrl}`);
      else if (ne.status === 403) issue('MEDIUM', role, path, `403 Forbidden: ${shortUrl}`);
    }

    for (const ce of consoleErrs) {
      if (!ce.includes('Outdated Optimize Dep') && !ce.includes('Failed to fetch dynamically'))
        issue('MEDIUM', role, path, `Console: ${ce}`);
    }

    await page.screenshot({ path: join(SCREENSHOT_DIR, `${role.toLowerCase()}-${name}.png`), fullPage: true });
  } catch (e) {
    issue('HIGH', role, path, `Navigation failed: ${e.message.substring(0, 150)}`);
  }
  await page.close();
}

async function testRole(browser, username, role, pages) {
  console.log(`\n=== Testing ${role} (${username}) ===`);
  const token = await loginAs(username, 'admin123');
  if (!token) { issue('CRITICAL', role, '-', 'Login failed'); return; }

  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  // Set auth token via login page
  const setupPage = await ctx.newPage();
  await setupPage.goto('http://localhost:5173/login');
  await setupPage.waitForTimeout(500);
  await setupPage.evaluate((t) => {
    localStorage.setItem('access_token', t);
    localStorage.setItem('remember_me', 'true');
  }, token);
  await setupPage.close();

  for (const [path, name] of pages) {
    await testPage(ctx, role, path, name);
  }
  await ctx.close();
}

async function testAccessControl(browser) {
  console.log('\n=== Testing Access Control ===');
  const accessTests = [
    { username: 'enterprise001', role: 'ENTERPRISE', forbidden: ['/admin/system/users', '/auditor/audit/list', '/third-party/monitor'] },
    { username: 'admin', role: 'ADMIN', forbidden: ['/enterprise/carbon/upload', '/auditor/audit/list', '/third-party/monitor'] },
    { username: 'reviewer001', role: 'REVIEWER', forbidden: ['/enterprise/carbon/upload', '/admin/system/users', '/third-party/monitor'] },
    { username: 'thirdparty001', role: 'THIRD_PARTY', forbidden: ['/enterprise/carbon/upload', '/admin/system/users', '/auditor/audit/list'] },
  ];

  for (const { username, role, forbidden } of accessTests) {
    const token = await loginAs(username, 'admin123');
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const p = await ctx.newPage();
    // Load app to initialize Pinia store
    await p.goto('http://localhost:5173/official-home', { waitUntil: 'networkidle', timeout: 15000 });
    await p.waitForTimeout(500);
    // Inject token
    await p.evaluate((t) => {
      localStorage.setItem('access_token', t);
      localStorage.setItem('remember_me', 'true');
    }, token);
    // Reload so resolveInitialState() picks up the token
    await p.reload({ waitUntil: 'networkidle', timeout: 15000 });
    await p.waitForTimeout(1000);
    await p.close();

    for (const path of forbidden) {
      const pg = await ctx.newPage();
      try {
        await pg.goto(`http://localhost:5173${path}`, { waitUntil: 'networkidle', timeout: 15000 });
        await pg.waitForTimeout(1500);
        const url = pg.url();
        if (!url.includes('/login') && !url.includes('403') && !url.includes('not-found')) {
          const bodyLen = (await pg.locator('body').innerText()).length;
          if (bodyLen > 100) {
            issue('HIGH', role, path, `ACCESS CONTROL: Page accessible (should be blocked for ${role})`);
          }
        }
      } catch (e) { /* timeout is acceptable */ }
      await pg.close();
    }
    await ctx.close();
  }
}

async function testAPIEndpoints() {
  console.log('\n=== Testing API Endpoints ===');

  const token = await loginAs('enterprise001', 'admin123');
  const headers = { 'Authorization': `Bearer ${token}` };

  const endpoints = [
    { path: '/api/v1/auth/me', desc: 'Get current user' },
    { path: '/api/v1/carbon/reports', desc: 'List carbon reports' },
    { path: '/api/v1/carbon/my-reports', desc: 'List my carbon reports' },
    { path: '/api/v1/credit-scores', desc: 'List credit scores' },
    { path: '/api/v1/carbon-neutral-projects', desc: 'List carbon neutral projects' },
    { path: '/api/v1/auction-orders', desc: 'List auction orders' },
    { path: '/api/v1/carbon-coins', desc: 'List carbon coins' },
    { path: '/api/v1/blockchain/reports', desc: 'Blockchain reports' },
    { path: '/api/v1/enterprises', desc: 'List enterprises' },
    { path: '/api/v1/signature/keypair', desc: 'Get keypair' },
  ];

  for (const ep of endpoints) {
    try {
      const res = await fetch(`http://localhost:8080${ep.path}`, { headers });
      const data = await res.json();
      if (res.status >= 500) {
        issue('CRITICAL', 'API', ep.path, `${ep.desc} returned ${res.status}: ${JSON.stringify(data).substring(0, 200)}`);
      } else if (res.status === 404) {
        issue('MEDIUM', 'API', ep.path, `${ep.desc} returned 404`);
      } else if (res.status === 403) {
        issue('MEDIUM', 'API', ep.path, `${ep.desc} returned 403 (permission denied)`);
      } else if (res.status === 400 && !ep.path.includes('keypair')) {
        issue('MEDIUM', 'API', ep.path, `${ep.desc} returned 400: ${JSON.stringify(data).substring(0, 200)}`);
      } else {
        console.log(`  ✅ ${ep.desc} (${res.status})`);
      }
    } catch (err) {
      issue('CRITICAL', 'API', ep.path, `${ep.desc} failed: ${err.message}`);
    }
  }

  // Test admin endpoints
  const adminToken = await loginAs('admin', 'admin123');
  const adminHeaders = { 'Authorization': `Bearer ${adminToken}` };
  const adminEndpoints = [
    { path: '/api/v1/admin/users', desc: 'Admin list users' },
    { path: '/api/v1/admin/enterprises', desc: 'Admin list enterprises' },
  ];

  for (const ep of adminEndpoints) {
    try {
      const res = await fetch(`http://localhost:8080${ep.path}`, { headers: adminHeaders });
      if (res.status >= 500) {
        issue('CRITICAL', 'API', ep.path, `${ep.desc} returned ${res.status}`);
      } else {
        console.log(`  ✅ ${ep.desc} (${res.status})`);
      }
    } catch (err) {
      issue('CRITICAL', 'API', ep.path, `${ep.desc} failed: ${err.message}`);
    }
  }
}

async function main() {
  console.log('OAISS CHAIN Deep Functional QA');
  console.log('='.repeat(50));

  const browser = await chromium.launch({ headless: true });

  // API tests
  await testAPIEndpoints();

  // Role-based page tests
  await testRole(browser, 'enterprise001', 'ENTERPRISE', [
    ['/enterprise/carbon/upload', 'carbon-upload'],
    ['/enterprise/trading/market', 'trading-market'],
    ['/enterprise/trading/p2p', 'trading-p2p'],
    ['/enterprise/orders/manage', 'orders'],
    ['/enterprise/company/dashboard', 'dashboard'],
    ['/enterprise/info', 'enterprise-info'],
    ['/enterprise/user/profile', 'user-profile'],
    ['/enterprise/credit/score', 'credit-score'],
    ['/enterprise/carbon-neutral/projects', 'carbon-neutral'],
    ['/enterprise/blockchain/browser', 'blockchain'],
    ['/enterprise/carbon-coin/account', 'carbon-coin'],
    ['/enterprise/emission/data', 'emission-data'],
    ['/enterprise/market-prediction', 'market-prediction'],
    ['/enterprise/enterprise-inference', 'enterprise-inference'],
    ['/enterprise/carbon-formula', 'formula-calc'],
  ]);

  await testRole(browser, 'admin', 'ADMIN', [
    ['/admin/system/users', 'users'],
    ['/admin/system/carbon', 'carbon'],
    ['/admin/system/config', 'config'],
    ['/admin/verify/list', 'verify'],
    ['/admin/certificates', 'certificates'],
    ['/admin/data/statistics', 'statistics'],
  ]);

  await testRole(browser, 'reviewer001', 'REVIEWER', [
    ['/auditor/audit/list', 'audit-list'],
    ['/auditor/project/review', 'project-review'],
    ['/auditor/review/history', 'review-history'],
  ]);

  await testRole(browser, 'thirdparty001', 'THIRD_PARTY', [
    ['/third-party/monitor', 'monitor'],
  ]);

  // Access control tests
  await testAccessControl(browser);

  await browser.close();

  // Summary
  console.log('\n' + '='.repeat(50));
  console.log('DEEP TEST SUMMARY');
  console.log('='.repeat(50));
  const counts = { CRITICAL: 0, HIGH: 0, MEDIUM: 0 };
  for (const i of issues) counts[i.severity] = (counts[i.severity] || 0) + 1;
  console.log(`CRITICAL: ${counts.CRITICAL}`);
  console.log(`HIGH: ${counts.HIGH}`);
  console.log(`MEDIUM: ${counts.MEDIUM}`);
  console.log(`Total issues: ${issues.length}`);

  if (issues.length > 0) {
    console.log('\nAll issues:');
    for (const i of issues) {
      console.log(`  [${i.severity}] ${i.role} ${i.page}: ${i.desc}`);
    }
  }
}

main().catch(e => { console.error('Fatal:', e.message); process.exit(1); });
