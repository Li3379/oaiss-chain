// scripts/extended-acceptance-patrol.mjs
// 基于 DEV-GUIDE.md / 体验验收标准的拓展性验收：
// browse 技能 QA 模式等价实现：加载验证/控制台/网络失败/状态断言/响应式/性能
import { chromium } from 'playwright';
import { mkdirSync, existsSync, writeFileSync } from 'fs';
import { join } from 'path';

const BASE_URL = 'http://localhost:5173';
const API_URL = 'http://localhost:8080/api/v1';
const SHOT_DIR = 'docs/qa-screenshots/extended-acceptance';
if (!existsSync(SHOT_DIR)) mkdirSync(SHOT_DIR, { recursive: true });

const ROLES = {
  enterprise: { user: 'enterprise001', pass: 'admin123' },
  reviewer: { user: 'reviewer001', pass: 'admin123' },
  admin: { user: 'admin', pass: 'admin123' },
  thirdParty: { user: 'thirdparty001', pass: 'admin123' },
};

const ROUTES_BY_ROLE = {
  enterprise: [
    '/enterprise/company/dashboard', '/enterprise/carbon/upload', '/enterprise/orders/manage',
    '/enterprise/trading/market', '/enterprise/trading/p2p', '/enterprise/credit/score',
    '/enterprise/carbon-coin/account', '/enterprise/blockchain/browser',
    '/enterprise/carbon-neutral/projects', '/enterprise/emission/data',
    '/enterprise/user/profile', '/enterprise/market-prediction',
    '/enterprise/enterprise-inference', '/enterprise/carbon-formula', '/enterprise/info',
  ],
  reviewer: ['/auditor/audit/list', '/auditor/review/history', '/auditor/project/review'],
  admin: ['/auditor/verify/list', '/admin/system/users', '/admin/system/carbon',
    '/admin/system/config', '/admin/data/statistics', '/admin/certificates'],
  thirdParty: ['/third-party/monitor'],
};

const report = { pages: [], perf: [], responsive: [], interactions: [], consoleErrors: [], networkFailures: [] };
let consoleErrors = [];
let networkFailures = [];

async function login(page, roleKey) {
  const c = ROLES[roleKey];
  const r = await page.request.post(`${API_URL}/auth/login`, { data: { username: c.user, password: c.pass } });
  const res = await r.json();
  if (res.code !== 200) throw new Error(`login failed ${c.user}`);
  await page.addInitScript(({ token, refresh }) => {
    localStorage.setItem('access_token', token);
    localStorage.setItem('refresh_token', refresh);
    localStorage.setItem('oaiss_guided_tour_done', '1');
    localStorage.setItem('locale', 'zh-CN');
  }, { token: res.data.accessToken, refresh: res.data.refreshToken });
}

function wireObservers(page) {
  consoleErrors = []; networkFailures = [];
  page.on('console', m => { if (m.type() === 'error') consoleErrors.push(`[${page.url()}] ${m.text()}`); });
  page.on('pageerror', e => consoleErrors.push(`[${page.url()}] Uncaught: ${e.message}`));
  page.on('response', resp => {
    if (resp.status() >= 400 && !resp.url().includes('/notifications') ) {
      networkFailures.push(`${resp.status()} ${resp.request().method()} ${resp.url().replace(BASE_URL, '')}`);
    }
  });
}

async function run() {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  wireObservers(page);

  // ===== 阶段1: 全角色全路由 — browse 模式 [goto + text + console + network] =====
  console.log('=== 阶段1: 四角色全路由加载验收 (goto/text/console/network) ===');
  for (const roleKey of Object.keys(ROUTES_BY_ROLE)) {
    await login(page, roleKey);
    for (const path of ROUTES_BY_ROLE[roleKey]) {
      const t0 = Date.now();
      await page.goto(`${BASE_URL}${path}`, { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(900);
      const loadMs = Date.now() - t0;
      const main = await page.locator('main').innerText().catch(() => '');
      const hasSkeleton = main.length < 20;
      const entry = {
        role: roleKey, path, loadMs,
        textLen: main.length, consoleErr: consoleErrors.length, netFail: networkFailures.length,
      };
      report.pages.push(entry);
      report.perf.push({ path, loadMs });
      if (consoleErrors.length) report.consoleErrors.push(...consoleErrors);
      if (networkFailures.length) report.networkFailures.push(...networkFailures);
      console.log(`${hasSkeleton || consoleErrors.length ? '✗' : '✓'} [${roleKey}] ${path} | ${loadMs}ms | 字数:${main.length} | 控制台错:${consoleErrors.length} | 网络失败:${networkFailures.length}`);
    }
  }

  // ===== 阶段2: 拓展验收-响应式布局 (browse 模式 [viewport + responsive]) =====
  console.log('\n=== 阶段2: 响应式布局验收 (移动375/平板768/桌面1440) ===');
  await login(page, 'enterprise');
  for (const vp of [{ w: 375, h: 812, tag: 'mobile' }, { w: 768, h: 1024, tag: 'tablet' }, { w: 1440, h: 900, tag: 'desktop' }]) {
    await page.setViewportSize({ width: vp.w, height: vp.h });
    for (const path of ['/enterprise/company/dashboard', '/enterprise/carbon/upload']) {
      await page.goto(`${BASE_URL}${path}`, { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(800);
      const layout = await page.evaluate(() => {
        const b = document.body;
        const hScroll = b.scrollWidth > window.innerWidth + 2;
        const sidebar = document.querySelector('aside, .el-aside');
        const sidebarVisible = sidebar ? getComputedStyle(sidebar).display !== 'none' : false;
        return { hScroll, sidebarVisible, innerWidth: window.innerWidth, scrollW: b.scrollWidth };
      });
      const shot = join(SHOT_DIR, `${vp.tag}${path.replaceAll('/', '_')}.png`);
      await page.screenshot({ path: shot });
      report.responsive.push({ path, ...vp, ...layout });
      console.log(`${layout.hScroll ? '⚠' : '✓'} [${vp.tag} ${vp.w}px] ${path} | 横向滚动:${layout.hScroll} | 侧栏可见:${layout.sidebarVisible} | 截图:${vp.tag}${path.replaceAll('/', '_')}.png`);
    }
  }
  await page.setViewportSize({ width: 1440, height: 900 });

  // ===== 阶段3: 拓展验收-交互断言 (browse 模式 [is visible/enabled/checked + snapshot diff]) =====
  console.log('\n=== 阶段3: 关键交互状态断言 ===');
  // 3.1 登出按钮可见、铃铛可见
  await page.goto(`${BASE_URL}/enterprise/company/dashboard`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(900);
  const bellVisible = await page.locator('.bell-btn, .header-right button').first().isVisible().catch(() => false);
  const logoutVisible = await page.locator('button:has-text("退出")').isVisible().catch(() => false);
  console.log(`${bellVisible ? '✓' : '✗'} 顶栏铃铛可见: ${bellVisible} | 退出按钮可见: ${logoutVisible}`);
  report.interactions.push({ check: 'header-visibility', bellVisible, logoutVisible });

  // 3.2 P2P 创建弹窗：必填校验与预估联动
  await page.goto(`${BASE_URL}/enterprise/trading/p2p`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(900);
  const createBtn = page.locator('button:has-text("创建P2P交易")');
  if (await createBtn.isVisible().catch(() => false)) {
    await createBtn.click();
    await page.waitForTimeout(600);
    const dialog = page.locator('.el-dialog:visible');
    const dialogOpen = await dialog.isVisible().catch(() => false);
    const hasBuyerSelect = dialogOpen ? await dialog.locator('.el-select').first().isVisible() : false;
    // 空表单直接提交 → 应被行内校验拦截
    const confirmBtn = dialog.locator('.el-dialog__footer button').filter({ hasText: /创建|确认/ }).first();
    if (dialogOpen && await confirmBtn.isVisible()) {
      await confirmBtn.click();
      await page.waitForTimeout(500);
    }
    const inlineErrors = await dialog.locator('.el-form-item__error').allTextContents().catch(() => []);
    console.log(`${dialogOpen && hasBuyerSelect && inlineErrors.length > 0 ? '✓' : '✗'} P2P弹窗: 开启=${dialogOpen} 企业下拉=${hasBuyerSelect} 空提交行内校验=${inlineErrors.length}条 [${inlineErrors.slice(0, 2).join('|')}]`);
    report.interactions.push({ check: 'p2p-dialog-guard', dialogOpen, hasBuyerSelect, inlineErrors: inlineErrors.length });
    // 关闭弹窗
    const cancel = dialog.locator('button:has-text("取消")');
    if (await cancel.isVisible().catch(() => false)) await cancel.click();
  }

  // 3.3 管理员批量上链禁用态断言（browse 模式 [is disabled]）
  await login(page, 'admin');
  await page.goto(`${BASE_URL}/auditor/verify/list`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(900);
  const batchBtn = page.locator('button').filter({ hasText: /批量/ }).first();
  const batchDisabled = batchBtn ? await batchBtn.isDisabled().catch(() => null) : null;
  console.log(`${batchDisabled ? '✓' : '⚠'} 管理员批量认证按钮未勾选时禁用: ${batchDisabled}`);
  report.interactions.push({ check: 'admin-batch-guard', batchDisabled });
  await page.screenshot({ path: join(SHOT_DIR, 'admin-verify.png') });

  // ===== 阶段4: 性能预算 (browse 模式 [perf]) =====
  console.log('\n=== 阶段4: 页面性能抽样 (perf) ===');
  await login(page, 'enterprise');
  const perfTarget = '/enterprise/company/dashboard';
  const t0 = Date.now();
  await page.goto(`${BASE_URL}${perfTarget}`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(600);
  const nav = await page.evaluate(() => {
    const [nav0] = performance.getEntriesByType('navigation');
    return nav0 ? { domContentLoaded: Math.round(nav0.domContentLoadedEventEnd), load: Math.round(nav0.loadEventEnd) } : {};
  });
  console.log(`✓ ${perfTarget} | 巡检耗时:${Date.now() - t0}ms | DCL:${nav.domContentLoaded}ms | Load:${nav.load}ms`);
  report.perf.push({ path: perfTarget, ...nav });

  await browser.close();

  // ===== 汇总 =====
  const total = report.pages.length;
  const bad = report.pages.filter(p => p.consoleErr > 0 || p.netFail > 0 || p.textLen < 20).length;
  const avgLoad = Math.round(report.pages.reduce((s, p) => s + p.loadMs, 0) / (total || 1));
  const summary = {
    totalRoutes: total, cleanRoutes: total - bad, avgLoadMs: avgLoad,
    consoleErrorTotal: report.consoleErrors.length,
    networkFailureTotal: report.networkFailures.length,
    responsiveChecks: report.responsive.length,
    interactionChecks: report.interactions.length,
  };
  writeFileSync('.reports/extended-acceptance.json', JSON.stringify({ summary, report }, null, 2));
  console.log('\n=============== 拓展验收总结 ===============');
  console.log(JSON.stringify(summary, null, 2));
}

run().catch(e => { console.error('fatal:', e); process.exit(1); });
