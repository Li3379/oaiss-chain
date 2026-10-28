import { chromium } from 'playwright';
import fs from 'node:fs';

async function testAllAiViews() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  const consoleLogs = [];
  page.on('console', msg => consoleLogs.push({ type: msg.type(), text: msg.text() }));
  page.on('pageerror', err => consoleLogs.push({ type: 'pageerror', text: err.message }));

  console.log('1. Logging in as enterprise001...');
  await page.goto('http://localhost:5173/login');
  await page.evaluate(async () => {
    const res = await fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'enterprise001', password: 'admin123' })
    }).then(r => r.json());
    if (res.code === 200 && res.data?.accessToken) {
      localStorage.setItem('access_token', res.data.accessToken);
      localStorage.setItem('refresh_token', res.data.refreshToken);
      localStorage.setItem('oaiss_guided_tour_done', 'true');
    }
  });

  if (!fs.existsSync('reports')) fs.mkdirSync('reports', { recursive: true });

  console.log('2. Testing /enterprise/carbon-formula (Design Point 1: Formula Audit & Sync)...');
  await page.goto('http://localhost:5173/enterprise/carbon-formula');
  await page.waitForTimeout(2000);

  // Load power gen template
  const loadBtn = await page.$('button:has-text("载入火电机组典型样本")');
  if (loadBtn) await loadBtn.click();
  await page.waitForTimeout(500);

  // Click calculate button
  const calcBtn = await page.$('button.btn-calc-pg');
  if (calcBtn) await calcBtn.click();
  await page.waitForTimeout(1000);

  // Verify AI Audit card presence
  const auditCardTitle = await page.textContent('strong:has-text("AI 国标合规诊断")').catch(() => null);
  console.log('Formula AI Audit Card Title:', auditCardTitle);

  // Test 1-click sync to upload draft button
  const syncBtn = await page.$('button:has-text("一键将测算数据带入申报草稿")');
  if (syncBtn) {
    console.log('Clicking 1-Click Sync to Carbon Upload Draft...');
    await syncBtn.click();
    await page.waitForTimeout(2000);
    console.log('Current URL after sync click:', page.url());
  }

  await page.screenshot({ path: 'reports/formula-sync-verified.png', fullPage: true });

  console.log('3. Testing /enterprise/market-prediction (Design Point 2: Scenario Sandbox)...');
  await page.goto('http://localhost:5173/enterprise/market-prediction');
  await page.waitForTimeout(2000);

  const sandboxTitle = await page.textContent('.sandbox-card strong').catch(() => null);
  const simPriceNum = await page.textContent('.sim-price-num').catch(() => null);
  console.log('Market Sandbox Title:', sandboxTitle);
  console.log('Simulated Target Price:', simPriceNum);

  await page.screenshot({ path: 'reports/market-sandbox-verified.png', fullPage: true });

  console.log('4. Testing /enterprise/enterprise-inference (Design Point 3: Radar & Dossier)...');
  await page.goto('http://localhost:5173/enterprise/enterprise-inference');
  await page.waitForTimeout(2000);

  const entTitle = await page.textContent('.ent-title').catch(() => null);
  console.log('Enterprise Title:', entTitle);

  // Click "生成企业低碳体检白皮书"
  const dossierBtn = await page.$('button:has-text("生成企业低碳体检白皮书")');
  if (dossierBtn) {
    console.log('Clicking Generate Dossier button...');
    await dossierBtn.click();
    await page.waitForTimeout(1500);
  }

  const dossierModalHeader = await page.textContent('.el-dialog__title:has-text("白皮书")').catch(() => null);
  console.log('Dossier Modal Header:', dossierModalHeader);

  await page.screenshot({ path: 'reports/enterprise-dossier-verified.png', fullPage: true });

  console.log('5. Testing /enterprise/company/dashboard (AI Executive Briefing Ribbon)...');
  await page.goto('http://localhost:5173/enterprise/company/dashboard');
  await page.waitForTimeout(2000);

  const briefingHeadline = await page.textContent('.briefing-headline').catch(() => null);
  console.log('Executive Briefing Headline:', briefingHeadline);

  await page.screenshot({ path: 'reports/dashboard-briefing-verified.png', fullPage: true });

  console.log('6. Testing /auditor/audit/list (Reviewer AI Pre-Audit Diagnostic)...');
  // Re-login as reviewer001
  await page.evaluate(async () => {
    const res = await fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'reviewer001', password: 'admin123' })
    }).then(r => r.json());
    if (res.code === 200 && res.data?.accessToken) {
      localStorage.setItem('access_token', res.data.accessToken);
      localStorage.setItem('refresh_token', res.data.refreshToken);
      localStorage.setItem('oaiss_guided_tour_done', 'true');
    }
  });

  await page.goto('http://localhost:5173/auditor/audit/list');
  await page.waitForTimeout(2000);

  const reviewActionBtn = await page.$('button:has-text("审核")');
  if (reviewActionBtn) {
    console.log('Opening review dialog...');
    await reviewActionBtn.click();
    await page.waitForTimeout(1000);

    const preAuditTitle = await page.textContent('.ai-preaudit-card strong').catch(() => null);
    console.log('Reviewer AI Pre-Audit Card:', preAuditTitle);
  }

  await page.screenshot({ path: 'reports/reviewer-preaudit-verified.png', fullPage: true });

  console.log('\n--- Final Verification Summary ---');
  const errors = consoleLogs.filter(l => l.type === 'error' || l.type === 'pageerror');
  console.log('Total console logs recorded:', consoleLogs.length);
  console.log('Total console errors recorded:', errors.length);
  if (errors.length) console.log('Errors:', errors);

  await browser.close();
  console.log('Browser session closed cleanly.');
}

testAllAiViews().catch(err => {
  console.error('Test execution failed:', err);
  process.exit(1);
});
