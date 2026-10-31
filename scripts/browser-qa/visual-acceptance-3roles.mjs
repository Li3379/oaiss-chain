// scripts/visual-acceptance-3roles.mjs — 三角色关键界面真实渲染取证 + 人工路径交互
import { chromium } from 'playwright';
import { mkdirSync, existsSync } from 'fs';
import { join } from 'path';

const BASE = 'http://localhost:5173';
const API = 'http://localhost:8080/api/v1';
const DIR = 'docs/qa-screenshots/computer-use-round';
if (!existsSync(DIR)) mkdirSync(DIR, { recursive: true });

async function login(page, user) {
  const r = await page.request.post(`${API}/auth/login`, { data: { username: user, password: 'admin123' } });
  const res = await r.json();
  await page.addInitScript(({ t, ref }) => {
    localStorage.setItem('access_token', t);
    localStorage.setItem('refresh_token', ref);
    localStorage.setItem('oaiss_guided_tour_done', '1');
    localStorage.setItem('locale', 'zh-CN');
  }, { t: res.data.accessToken, ref: res.data.refreshToken });
}

const log = [];
const shot = async (page, name) => {
  const p = join(DIR, name);
  await page.screenshot({ path: p, fullPage: false });
  log.push(`📸 ${name}`);
};

const run = async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });

  // ===== 企业角色 =====
  await login(page, 'enterprise001');
  await page.goto(`${BASE}/enterprise/company/dashboard`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1200);
  await shot(page, '01-enterprise-dashboard.png');

  await page.goto(`${BASE}/enterprise/carbon/upload`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1000);
  await shot(page, '02-enterprise-carbon-upload.png');
  // 操作性：打开创建报告弹窗（人工路径）
  await page.locator('button:has-text("创建")').first().click();
  await page.waitForTimeout(700);
  await shot(page, '03-enterprise-create-report-dialog.png');
  const dialogVisible = await page.locator('.el-dialog:visible').isVisible().catch(() => false);
  log.push(`企业创建报告弹窗开启: ${dialogVisible}`);
  const cancel = page.locator('.el-dialog:visible button:has-text("取消")');
  if (await cancel.count()) await cancel.first().click();
  await page.waitForTimeout(400);

  await page.goto(`${BASE}/enterprise/trading/p2p`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(900);
  await shot(page, '04-enterprise-p2p.png');

  // ===== 核查员角色 =====
  await login(page, 'reviewer001');
  await page.goto(`${BASE}/auditor/audit/list`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1000);
  await shot(page, '05-reviewer-audit-list.png');
  // 操作性：打开审核弹窗（含 EmissionDataPanel 决策依据）
  const auditBtn = page.locator('.el-table__body tr').first().locator('button:has-text("审核")');
  if (await auditBtn.count()) {
    await auditBtn.first().click();
    await page.waitForTimeout(800);
    await shot(page, '06-reviewer-audit-dialog.png');
    const hasPanel = await page.locator('.el-dialog:visible .emission-panel').isVisible().catch(() => false);
    log.push(`审核弹窗结构化排放面板: ${hasPanel}`);
    const c = page.locator('.el-dialog:visible button:has-text("取消")');
    if (await c.count()) await c.first().click();
    await page.waitForTimeout(300);
  }

  // ===== 管理员角色 =====
  await login(page, 'admin');
  await page.goto(`${BASE}/auditor/verify/list`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1000);
  await shot(page, '07-admin-verify-list.png');
  const viewBtn = page.locator('.el-table__body tr').first().locator('button:has-text("查看")');
  if (await viewBtn.count()) {
    await viewBtn.first().click();
    await page.waitForTimeout(800);
    await shot(page, '08-admin-detail-dialog.png');
    const hasCert = await page.locator('.el-dialog:visible .cert-detail-block').isVisible().catch(() => false);
    log.push(`管理员详情弹窗排放明细块: ${hasCert}`);
    const c = page.locator('.el-dialog:visible button:has-text("取消"), .el-dialog:visible button:has-text("关 闭"), .el-dialog__headerbtn');
    if (await c.count()) await c.first().click();
    await page.waitForTimeout(300);
  }
  await page.goto(`${BASE}/admin/certificates`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(900);
  await shot(page, '09-admin-certificates.png');

  await browser.close();
  console.log(log.join('\n'));
  console.log(`控制台错误数: ${errors.length}`);
  errors.slice(0, 3).forEach(e => console.log('ERR:', e));
};

run().catch(e => { console.error('fatal:', e); process.exit(1); });
