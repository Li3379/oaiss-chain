// scripts/browser-qa/whitebox-functional-audit.mjs
// OAISS CHAIN - 软件测试全角色白盒功能实现与预期差异自动化探针

import { chromium } from 'playwright';
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';

const BASE_URL = 'http://localhost:5173';
const API_BASE = 'http://localhost:8080/api/v1';
const SCREENSHOT_DIR = path.resolve('.playwright-mcp/whitebox-audit');

if (!fs.existsSync(SCREENSHOT_DIR)) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

// 登录工具：获取 Captcha 并从 Redis 提取验证码，完成可靠真实登录
async function getAuthTokens(username, password = 'admin123') {
  try {
    const capRes = await fetch(`${API_BASE}/auth/captcha`, { method: 'POST' });
    const capData = await capRes.json();
    const key = capData.data.captchaKey;

    const raw = execSync(`docker exec oaiss-redis redis-cli -a oaiss_redis_dev_2026 get "captcha:${key}"`)
      .toString()
      .trim();
    const code = raw.split('\n').pop().trim();

    const loginRes = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username,
        password,
        captchaKey: key,
        captcha: code,
      }),
    });
    const data = await loginRes.json();
    if (data.code !== 200) {
      throw new Error(`Login failed for ${username}: ${JSON.stringify(data)}`);
    }
    return data.data;
  } catch (err) {
    console.error(`[Auth] Failed to authenticate user: ${username}`, err.message);
    throw err;
  }
}

async function injectAuth(page, tokens) {
  await page.addInitScript(({ t, r, userType, username }) => {
    localStorage.setItem('access_token', t);
    localStorage.setItem('refresh_token', r);
    localStorage.setItem('oaiss_guided_tour_done', '1');
    localStorage.setItem('locale', 'zh-CN');
    localStorage.setItem('remember_me', 'true');
    sessionStorage.setItem('access_token', t);
  }, {
    t: tokens.accessToken,
    r: tokens.refreshToken,
    userType: tokens.userType,
    username: tokens.username,
  });
}

async function main() {
  console.log('=== 开始 OAISS CHAIN 全系统白盒功能探针巡检 ===');
  const results = {
    timestamp: new Date().toISOString(),
    rolesTested: [],
    pagesChecked: [],
    consoleErrors: [],
    pageErrors: [],
    networkErrors: [],
    functionalFindings: [],
    screenshots: [],
  };

  const browser = await chromium.launch({ channel: 'chrome', headless: true });

  // 1. 测试企业端 (ENTERPRISE - enterprise001)
  try {
    console.log('\n[Role 1] 测试控排企业端 (enterprise001)...');
    const entTokens = await getAuthTokens('enterprise001');
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await ctx.newPage();

    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        const text = msg.text();
        if (!text.includes('favicon') && !text.includes('ResizeObserver')) {
          results.consoleErrors.push({ role: 'ENTERPRISE', url: page.url(), text });
        }
      }
    });
    page.on('pageerror', (err) => {
      results.pageErrors.push({ role: 'ENTERPRISE', url: page.url(), message: err.message });
    });
    page.on('response', (resp) => {
      if (resp.status() >= 400 && !resp.url().includes('favicon')) {
        results.networkErrors.push({ role: 'ENTERPRISE', url: resp.url(), status: resp.status() });
      }
    });

    await injectAuth(page, entTokens);

    // 1.1 大屏仪表盘
    await page.goto(`${BASE_URL}/enterprise/company/dashboard`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    const dashShot = path.join(SCREENSHOT_DIR, '01-enterprise-dashboard.png');
    await page.screenshot({ path: dashShot });
    results.screenshots.push('01-enterprise-dashboard.png');
    results.pagesChecked.push('/enterprise/company/dashboard');

    // 1.2 碳核算公式计算器
    await page.goto(`${BASE_URL}/enterprise/carbon-formula`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    const formulaShot = path.join(SCREENSHOT_DIR, '02-enterprise-formula.png');
    await page.screenshot({ path: formulaShot });
    results.screenshots.push('02-enterprise-formula.png');
    results.pagesChecked.push('/enterprise/carbon-formula');

    // 检查是否有碳氧化率反推相关表单元素
    const oxidationBtn = await page.locator('button, .el-button').filter({ hasText: /反推|计算|测算/ }).count();
    console.log(`[Enterprise] 碳核算计算按钮数: ${oxidationBtn}`);

    // 1.3 排放申报与草稿列表
    await page.goto(`${BASE_URL}/enterprise/carbon/upload`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    const uploadShot = path.join(SCREENSHOT_DIR, '03-enterprise-carbon-upload.png');
    await page.screenshot({ path: uploadShot });
    results.screenshots.push('03-enterprise-carbon-upload.png');
    results.pagesChecked.push('/enterprise/carbon/upload');

    // 检查“创建报告”弹窗交互
    const createBtn = page.locator('button').filter({ hasText: /创建报告|新建|新增/ }).first();
    if (await createBtn.isVisible()) {
      await createBtn.click();
      await page.waitForTimeout(800);
      const dialogVisible = await page.locator('.el-dialog:visible').isVisible();
      const createDialogShot = path.join(SCREENSHOT_DIR, '04-enterprise-create-dialog.png');
      await page.screenshot({ path: createDialogShot });
      results.screenshots.push('04-enterprise-create-dialog.png');

      // 关闭弹窗
      const cancelBtn = page.locator('.el-dialog:visible button').filter({ hasText: /取消|关闭/ }).first();
      if (await cancelBtn.isVisible()) {
        await cancelBtn.click();
        await page.waitForTimeout(400);
      }
    }

    // 1.4 交易市场挂单
    await page.goto(`${BASE_URL}/enterprise/trading/market`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    const marketShot = path.join(SCREENSHOT_DIR, '05-enterprise-market.png');
    await page.screenshot({ path: marketShot });
    results.screenshots.push('05-enterprise-market.png');
    results.pagesChecked.push('/enterprise/trading/market');

    // 1.5 碳币账户中心
    await page.goto(`${BASE_URL}/enterprise/carbon/coin`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    const coinShot = path.join(SCREENSHOT_DIR, '06-enterprise-carbon-coin.png');
    await page.screenshot({ path: coinShot });
    results.screenshots.push('06-enterprise-carbon-coin.png');
    results.pagesChecked.push('/enterprise/carbon/coin');

    await ctx.close();
    results.rolesTested.push('ENTERPRISE');
  } catch (err) {
    console.error('[Error] ENTERPRISE phase error:', err);
    results.functionalFindings.push({
      role: 'ENTERPRISE',
      type: 'EXECUTION_ERROR',
      detail: err.message,
    });
  }

  // 2. 测试审核员端 (REVIEWER - reviewer001)
  try {
    console.log('\n[Role 2] 测试数据审核员端 (reviewer001)...');
    const revTokens = await getAuthTokens('reviewer001');
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await ctx.newPage();

    page.on('console', (msg) => {
      if (msg.type() === 'error' && !msg.text().includes('favicon') && !msg.text().includes('ResizeObserver')) {
        results.consoleErrors.push({ role: 'REVIEWER', url: page.url(), text: msg.text() });
      }
    });

    await injectAuth(page, revTokens);

    // 2.1 待审报告列表
    await page.goto(`${BASE_URL}/auditor/audit/list`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    const auditShot = path.join(SCREENSHOT_DIR, '07-reviewer-audit-list.png');
    await page.screenshot({ path: auditShot });
    results.screenshots.push('07-reviewer-audit-list.png');
    results.pagesChecked.push('/auditor/audit/list');

    // 检查页面上是否有待审数据表格
    const tableRows = await page.locator('.el-table__body-wrapper tr').count();
    console.log(`[Reviewer] 待审核表格行数: ${tableRows}`);

    // 2.2 上链补救入口（方案 A 关键迁移）
    await page.goto(`${BASE_URL}/auditor/verify/list`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    const verifyShot = path.join(SCREENSHOT_DIR, '08-reviewer-verify-list.png');
    await page.screenshot({ path: verifyShot });
    results.screenshots.push('08-reviewer-verify-list.png');
    results.pagesChecked.push('/auditor/verify/list');

    // 验证页面标题与功能按钮
    const pageTitle = await page.locator('.page-header h2, .page-header, .el-page-header__content').textContent().catch(() => '');
    console.log(`[Reviewer] 上链补救页面标题: ${pageTitle.trim()}`);

    const batchBtn = await page.locator('button').filter({ hasText: /批量上链|上链补救|批量认证/ }).count();
    console.log(`[Reviewer] 上链补救按钮可用数: ${batchBtn}`);

    await ctx.close();
    results.rolesTested.push('REVIEWER');
  } catch (err) {
    console.error('[Error] REVIEWER phase error:', err);
    results.functionalFindings.push({
      role: 'REVIEWER',
      type: 'EXECUTION_ERROR',
      detail: err.message,
    });
  }

  // 3. 测试第三方监管端 (THIRD_PARTY - thirdparty001)
  try {
    console.log('\n[Role 3] 测试第三方监管端 (thirdparty001)...');
    const tpTokens = await getAuthTokens('thirdparty001');
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await ctx.newPage();

    page.on('console', (msg) => {
      if (msg.type() === 'error' && !msg.text().includes('favicon') && !msg.text().includes('ResizeObserver')) {
        results.consoleErrors.push({ role: 'THIRD_PARTY', url: page.url(), text: msg.text() });
      }
    });

    await injectAuth(page, tpTokens);

    // 3.1 监管监控大屏
    await page.goto(`${BASE_URL}/third-party/monitor`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    const monitorShot = path.join(SCREENSHOT_DIR, '09-third-party-monitor.png');
    await page.screenshot({ path: monitorShot });
    results.screenshots.push('09-third-party-monitor.png');
    results.pagesChecked.push('/third-party/monitor');

    // 关键方案 A 白盒约束验证：页面中严禁包含任何业务写/审批按钮
    const writeButtons = await page.locator('button').filter({ hasText: /提交|审核|批准|签发|上链|创建/ }).allTextContents();
    console.log(`[ThirdParty] 监管端操作按钮列表: ${JSON.stringify(writeButtons)}`);

    // 3.2 越权探测：第三方监管机构直接尝试访问管理端或审核端页面
    await page.goto(`${BASE_URL}/auditor/verify/list`, { waitUntil: 'networkidle', timeout: 10000 });
    await page.waitForTimeout(800);
    const currentUrl = page.url();
    const isForbiddenOrRedirected = !currentUrl.includes('/auditor/verify/list') || currentUrl.includes('/login') || currentUrl.includes('/third-party');
    console.log(`[ThirdParty] 尝试越权访问审核端页面结果: URL=${currentUrl}, 被守卫拦截=${isForbiddenOrRedirected}`);

    await ctx.close();
    results.rolesTested.push('THIRD_PARTY');
  } catch (err) {
    console.error('[Error] THIRD_PARTY phase error:', err);
    results.functionalFindings.push({
      role: 'THIRD_PARTY',
      type: 'EXECUTION_ERROR',
      detail: err.message,
    });
  }

  // 4. 测试超级管理员端 (ADMIN - admin)
  try {
    console.log('\n[Role 4] 测试超级管理员端 (admin)...');
    const adminTokens = await getAuthTokens('admin');
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await ctx.newPage();

    page.on('console', (msg) => {
      if (msg.type() === 'error' && !msg.text().includes('favicon') && !msg.text().includes('ResizeObserver')) {
        results.consoleErrors.push({ role: 'ADMIN', url: page.url(), text: msg.text() });
      }
    });

    await injectAuth(page, adminTokens);

    // 4.1 证书管理
    await page.goto(`${BASE_URL}/admin/certificates`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    const certShot = path.join(SCREENSHOT_DIR, '10-admin-certificates.png');
    await page.screenshot({ path: certShot });
    results.screenshots.push('10-admin-certificates.png');
    results.pagesChecked.push('/admin/certificates');

    // 4.2 用户管理
    await page.goto(`${BASE_URL}/admin/system/users`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    const usersShot = path.join(SCREENSHOT_DIR, '11-admin-users.png');
    await page.screenshot({ path: usersShot });
    results.screenshots.push('11-admin-users.png');
    results.pagesChecked.push('/admin/system/users');

    // 4.3 认证列表运维入口
    await page.goto(`${BASE_URL}/admin/verify/list`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    const adminVerifyShot = path.join(SCREENSHOT_DIR, '12-admin-verify-list.png');
    await page.screenshot({ path: adminVerifyShot });
    results.screenshots.push('12-admin-verify-list.png');
    results.pagesChecked.push('/admin/verify/list');

    await ctx.close();
    results.rolesTested.push('ADMIN');
  } catch (err) {
    console.error('[Error] ADMIN phase error:', err);
    results.functionalFindings.push({
      role: 'ADMIN',
      type: 'EXECUTION_ERROR',
      detail: err.message,
    });
  }

  await browser.close();

  // 写入探针结果摘要
  const outputSummaryPath = path.resolve('.playwright-mcp/whitebox-audit/summary.json');
  fs.writeFileSync(outputSummaryPath, JSON.stringify(results, null, 2), 'utf-8');
  console.log(`\n=== 探针巡检完成，结果已保存至: ${outputSummaryPath} ===`);
  console.log(`- 测试角色: ${results.rolesTested.join(', ')}`);
  console.log(`- 校验页面: ${results.pagesChecked.length} 个`);
  console.log(`- 截图数量: ${results.screenshots.length} 张`);
  console.log(`- 控制台错误: ${results.consoleErrors.length} 条`);
  console.log(`- 网络异常: ${results.networkErrors.length} 条`);
}

main().catch((e) => {
  console.error('Fatal probe execution error:', e);
  process.exit(1);
});
