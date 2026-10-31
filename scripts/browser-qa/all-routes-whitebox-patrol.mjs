// scripts/browser-qa/all-routes-whitebox-patrol.mjs
// OAISS CHAIN - 全系统 29 路由全角色白盒自动化探针深摸测试套件

import { chromium } from 'playwright';
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';

const BASE_URL = 'http://localhost:5173';
const API_BASE = 'http://localhost:8080/api/v1';
const REPORT_DIR = path.resolve('.playwright-mcp/whitebox-full-patrol');

if (!fs.existsSync(REPORT_DIR)) {
  fs.mkdirSync(REPORT_DIR, { recursive: true });
}

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
  await page.addInitScript(({ t, r, userType, username, userId }) => {
    localStorage.setItem('access_token', t);
    localStorage.setItem('refresh_token', r);
    localStorage.setItem('oaiss_guided_tour_done', '1');
    localStorage.setItem(`oaiss_guided_tour_done:${userId}`, '1');
    localStorage.setItem('locale', 'zh-CN');
    localStorage.setItem('remember_me', 'true');
    sessionStorage.setItem('access_token', t);
  }, {
    t: tokens.accessToken,
    r: tokens.refreshToken,
    userType: tokens.userType,
    username: tokens.username,
    userId: tokens.userId,
  });
}

const TEST_PLAN = {
  public: [
    { path: '/official-home', name: '官网首页' },
    { path: '/login', name: '登录页' },
    { path: '/non-existent-404-test', name: '404缺省页' },
  ],
  ENTERPRISE: [
    { path: '/enterprise/carbon/upload', name: '上传审核' },
    { path: '/enterprise/orders/manage', name: '订单管理' },
    { path: '/enterprise/trading/market', name: '双向拍卖' },
    { path: '/enterprise/trading/p2p', name: 'P2P交易' },
    { path: '/enterprise/company/dashboard', name: '数据可视化' },
    { path: '/enterprise/credit/score', name: '信誉评分' },
    { path: '/enterprise/carbon-coin/account', name: '碳币账户' },
    { path: '/enterprise/blockchain/browser', name: '区块链浏览器' },
    { path: '/enterprise/carbon-neutral/projects', name: '碳中和项目' },
    { path: '/enterprise/emission/data', name: '排放数据' },
    { path: '/enterprise/user/profile', name: '个人中心' },
    { path: '/enterprise/market-prediction', name: 'AI市场预测' },
    { path: '/enterprise/enterprise-inference', name: 'AI企业推理' },
    { path: '/enterprise/carbon-formula', name: '碳核算公式' },
    { path: '/enterprise/info', name: '企业信息' },
  ],
  REVIEWER: [
    { path: '/auditor/audit/list', name: '待审报告列表' },
    { path: '/auditor/review/history', name: '审核历史' },
    { path: '/auditor/project/review', name: '项目核证' },
    { path: '/auditor/verify/list', name: '上链补救' },
  ],
  THIRD_PARTY: [
    { path: '/third-party/monitor', name: '监管监控大屏' },
  ],
  ADMIN: [
    { path: '/admin/system/users', name: '用户管理' },
    { path: '/admin/system/carbon', name: '碳核算管理' },
    { path: '/admin/system/config', name: '系统配置' },
    { path: '/admin/data/statistics', name: '统计数据' },
    { path: '/admin/certificates', name: '证书管理' },
    { path: '/admin/blockchain', name: '链上管理' },
    { path: '/auditor/verify/list', name: '上链补救' },
  ],
};

// 越权负向探测用例 (跨角色非法越权访问)
const NEGATIVE_SECURITY_TESTS = [
  { role: 'ENTERPRISE', forbiddenPath: '/admin/system/users', targetDesc: '管理员用户管理' },
  { role: 'ENTERPRISE', forbiddenPath: '/auditor/audit/list', targetDesc: '审核员待审列表' },
  { role: 'REVIEWER', forbiddenPath: '/admin/system/config', targetDesc: '管理员系统配置' },
  { role: 'THIRD_PARTY', forbiddenPath: '/admin/certificates', targetDesc: '管理员证书管理' },
  { role: 'THIRD_PARTY', forbiddenPath: '/auditor/verify/list', targetDesc: '审核员上链补救' },
];

async function main() {
  console.log('====================================================');
  console.log('  OAISS CHAIN 全系统白盒自动化全路由探针巡检测试   ');
  console.log('====================================================');

  const report = {
    startedAt: new Date().toISOString(),
    routesSummary: { total: 0, passed: 0, failed: 0 },
    securitySummary: { total: 0, blocked: 0, leaked: 0 },
    consoleErrors: [],
    pageErrors: [],
    networkErrors: [],
    routeResults: [],
    securityResults: [],
  };

  const browser = await chromium.launch({ channel: 'chrome', headless: true });

  // 1. 公共页面校验
  console.log('\n--- [阶段 1: 公共可访问路由巡检] ---');
  {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await ctx.newPage();

    page.on('console', (msg) => {
      if (msg.type() === 'error' && !msg.text().includes('favicon') && !msg.text().includes('ResizeObserver')) {
        report.consoleErrors.push({ role: 'PUBLIC', url: page.url(), text: msg.text() });
      }
    });
    page.on('pageerror', (err) => {
      report.pageErrors.push({ role: 'PUBLIC', url: page.url(), message: err.message });
    });
    page.on('response', (resp) => {
      if (resp.status() >= 400 && !resp.url().includes('favicon') && !resp.url().includes('non-existent')) {
        report.networkErrors.push({ role: 'PUBLIC', url: resp.url(), status: resp.status() });
      }
    });

    for (const item of TEST_PLAN.public) {
      report.routesSummary.total++;
      try {
        await page.goto(`${BASE_URL}${item.path}`, { waitUntil: 'networkidle', timeout: 15000 });
        await page.waitForTimeout(600);
        const title = await page.title();
        const textLen = (await page.locator('body').innerText()).length;
        console.log(`✓ [公共] ${item.name} (${item.path}) -> 标题: "${title}", 文本长度: ${textLen}`);
        report.routeResults.push({ role: 'PUBLIC', ...item, title, textLen, status: 'PASS' });
        report.routesSummary.passed++;
      } catch (err) {
        console.error(`✗ [公共] ${item.name} (${item.path}) 访问失败:`, err.message);
        report.routeResults.push({ role: 'PUBLIC', ...item, error: err.message, status: 'FAIL' });
        report.routesSummary.failed++;
      }
    }
    await ctx.close();
  }

  // 2. 角色专有页面巡检
  const ROLE_USERS = [
    { role: 'ENTERPRISE', user: 'enterprise001' },
    { role: 'REVIEWER', user: 'reviewer001' },
    { role: 'THIRD_PARTY', user: 'thirdparty001' },
    { role: 'ADMIN', user: 'admin' },
  ];

  for (const { role, user } of ROLE_USERS) {
    console.log(`\n--- [阶段 2: ${role} (${user}) 正向授权路由巡检] ---`);
    const routes = TEST_PLAN[role] || [];
    try {
      const tokens = await getAuthTokens(user);
      const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
      const page = await ctx.newPage();

      page.on('console', (msg) => {
        if (msg.type() === 'error' && !msg.text().includes('favicon') && !msg.text().includes('ResizeObserver')) {
          report.consoleErrors.push({ role, url: page.url(), text: msg.text() });
        }
      });
      page.on('pageerror', (err) => {
        report.pageErrors.push({ role, url: page.url(), message: err.message });
      });
      page.on('response', (resp) => {
        if (resp.status() >= 400 && !resp.url().includes('favicon')) {
          report.networkErrors.push({ role, url: resp.url(), status: resp.status() });
        }
      });

      await injectAuth(page, tokens);

      for (const item of routes) {
        report.routesSummary.total++;
        try {
          await page.goto(`${BASE_URL}${item.path}`, { waitUntil: 'networkidle', timeout: 15000 });
          await page.waitForTimeout(600);
          const currentUrl = page.url();
          const title = await page.title();
          const textLen = (await page.locator('body').innerText()).length;
          const hasContent = textLen > 50;

          if (!currentUrl.includes(item.path.split('?')[0]) && !currentUrl.includes('/enterprise/')) {
            throw new Error(`Unexpected redirection to: ${currentUrl}`);
          }

          console.log(`✓ [${role}] ${item.name} (${item.path}) -> 文本长度: ${textLen}, 状态: 正常渲染`);
          report.routeResults.push({ role, ...item, title, textLen, hasContent, status: 'PASS' });
          report.routesSummary.passed++;
        } catch (err) {
          console.error(`✗ [${role}] ${item.name} (${item.path}) 巡检异常:`, err.message);
          report.routeResults.push({ role, ...item, error: err.message, status: 'FAIL' });
          report.routesSummary.failed++;
        }
      }

      await ctx.close();
    } catch (authErr) {
      console.error(`[Fatal] 角色 ${role} 认证或巡检整体失败:`, authErr.message);
    }
  }

  // 3. 跨角色越权负向探测
  console.log('\n--- [阶段 3: RBAC 跨角色越权负向防护探测] ---');
  for (const test of NEGATIVE_SECURITY_TESTS) {
    report.securitySummary.total++;
    const user = ROLE_USERS.find((u) => u.role === test.role).user;
    try {
      const tokens = await getAuthTokens(user);
      const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
      const page = await ctx.newPage();
      await injectAuth(page, tokens);

      await page.goto(`${BASE_URL}${test.forbiddenPath}`, { waitUntil: 'networkidle', timeout: 10000 });
      await page.waitForTimeout(500);
      const finalUrl = page.url();

      const isBlocked = !finalUrl.includes(test.forbiddenPath) || finalUrl.includes('/login');
      if (isBlocked) {
        console.log(`🛡️ [RBAC防护] ${test.role} 越权访问 ${test.targetDesc} (${test.forbiddenPath}) -> 成功阻断重定向至: ${finalUrl}`);
        report.securitySummary.blocked++;
        report.securityResults.push({ ...test, finalUrl, verdict: 'BLOCKED_SAFELY' });
      } else {
        console.error(`🚨 [安全越权] ${test.role} 越权访问 ${test.targetDesc} (${test.forbiddenPath}) 未被阻断！当前URL: ${finalUrl}`);
        report.securitySummary.leaked++;
        report.securityResults.push({ ...test, finalUrl, verdict: 'SECURITY_LEAK_FAILED' });
      }
      await ctx.close();
    } catch (secErr) {
      console.error(`[Security Test Error] ${test.role} -> ${test.forbiddenPath}:`, secErr.message);
    }
  }

  await browser.close();

  report.finishedAt = new Date().toISOString();
  const summaryFile = path.join(REPORT_DIR, 'full-patrol-summary.json');
  fs.writeFileSync(summaryFile, JSON.stringify(report, null, 2), 'utf-8');

  console.log('\n====================================================');
  console.log(`  全路由巡检完成！`);
  console.log(`- 路由总数: ${report.routesSummary.total} (通过: ${report.routesSummary.passed}, 失败: ${report.routesSummary.failed})`);
  console.log(`- RBAC越权探测: ${report.securitySummary.total} (安全阻断: ${report.securitySummary.blocked}, 越权泄漏: ${report.securitySummary.leaked})`);
  console.log(`- 控制台异常: ${report.consoleErrors.length} 条`);
  console.log(`- 网络异常: ${report.networkErrors.length} 条`);
  console.log(`- 报告路径: ${summaryFile}`);
  console.log('====================================================');
}

main().catch((err) => {
  console.error('Fatal execution error:', err);
  process.exit(1);
});
