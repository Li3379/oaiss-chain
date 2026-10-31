// scripts/full-suite-patrol.mjs
import { chromium } from 'playwright';

const BASE_URL = 'http://localhost:5173';
const API_URL = 'http://localhost:8080/api/v1';

const ROLES = {
  enterprise: { user: 'enterprise001', pass: 'admin123' },
  reviewer: { user: 'reviewer001', pass: 'admin123' },
  admin: { user: 'admin', pass: 'admin123' },
  thirdParty: { user: 'thirdparty001', pass: 'admin123' }
};

const ROUTES_BY_ROLE = {
  public: [
    { path: '/official', name: '官网首页' },
    { path: '/login', name: '登录页' },
    { path: '/non-existent-page-404', name: '404缺省页' }
  ],
  enterprise: [
    { path: '/enterprise/company/dashboard', name: '数据可视化' },
    { path: '/enterprise/carbon/upload', name: '上传审核' },
    { path: '/enterprise/orders/manage', name: '订单管理' },
    { path: '/enterprise/trading/market', name: '双向拍卖' },
    { path: '/enterprise/trading/p2p', name: 'P2P交易' },
    { path: '/enterprise/credit/score', name: '信誉评分' },
    { path: '/enterprise/carbon-coin/account', name: '碳币账户' },
    { path: '/enterprise/blockchain/browser', name: '区块链浏览器' },
    { path: '/enterprise/carbon-neutral/projects', name: '碳中和项目' },
    { path: '/enterprise/emission/data', name: '排放数据' },
    { path: '/enterprise/user/profile', name: '个人中心' },
    { path: '/enterprise/market-prediction', name: 'AI市场预测' },
    { path: '/enterprise/enterprise-inference', name: 'AI企业推理' },
    { path: '/enterprise/carbon-formula', name: '碳核算公式' },
    { path: '/enterprise/info', name: '企业信息' }
  ],
  reviewer: [
    { path: '/auditor/audit/list', name: '碳排放数据审核' },
    { path: '/auditor/review/history', name: '审核历史' },
    { path: '/auditor/project/review', name: '项目审核' }
  ],
  admin: [
    { path: '/auditor/verify/list', name: '上链补救' },
    { path: '/admin/system/users', name: '用户管理' },
    { path: '/admin/system/carbon', name: '碳核算管理' },
    { path: '/admin/system/config', name: '系统配置' },
    { path: '/admin/data/statistics', name: '统计数据' },
    { path: '/admin/certificates', name: '证书管理' },
    { path: '/admin/blockchain', name: '链上管理' }
  ],
  thirdParty: [
    { path: '/third-party/monitor', name: '第三方监管面板' }
  ]
};

async function loginUser(roleKey) {
  const creds = ROLES[roleKey];
  const res = await fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: creds.user, password: creds.pass })
  });
  const data = await res.json();
  if (data.code !== 200) throw new Error(`Login failed for ${creds.user}: ${data.message}`);
  return data.data;
}

async function run() {
  console.log('🚀 开始全量泛式与微式系统巡检...');
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  const consoleErrors = [];
  page.on('console', msg => {
    if (msg.type() === 'error') consoleErrors.push(`[${page.url()}] ${msg.text()}`);
  });
  page.on('pageerror', err => {
    consoleErrors.push(`[${page.url()}] Uncaught: ${err.message}`);
  });

  const results = {
    passedRoutes: 0,
    failedRoutes: 0,
    componentsInspected: 0,
    routes: []
  };

  // 1. 公共路由
  console.log('\n--- [阶段 1: 公共页面巡检] ---');
  for (const r of ROUTES_BY_ROLE.public) {
    try {
      await page.goto(`${BASE_URL}${r.path}`, { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(600);
      const title = await page.title();
      const hasContent = await page.locator('body').innerText();
      console.log(`✓ [公共] ${r.name} (${r.path}) -> 标题: "${title}", 渲染字数: ${hasContent.length}`);
      results.passedRoutes++;
      results.routes.push({ role: 'public', path: r.path, status: 'pass' });
    } catch (err) {
      console.error(`✗ [公共] ${r.name} (${r.path}) -> 失败: ${err.message}`);
      results.failedRoutes++;
      results.routes.push({ role: 'public', path: r.path, status: 'fail', error: err.message });
    }
  }

  // 2. 四大业务角色逐一登录并遍历受保护路由
  for (const roleKey of ['enterprise', 'reviewer', 'admin', 'thirdParty']) {
    console.log(`\n--- [阶段 2: ${roleKey} 角色巡检与内嵌组件深摸] ---`);
    const authData = await loginUser(roleKey);
    await page.goto(`${BASE_URL}/login`);
    await page.evaluate(tokenData => {
      localStorage.setItem('access_token', tokenData.accessToken);
      localStorage.setItem('refresh_token', tokenData.refreshToken);
      localStorage.setItem('oaiss_guided_tour_done', '1');
    }, authData);

    for (const r of ROUTES_BY_ROLE[roleKey]) {
      try {
        await page.goto(`${BASE_URL}${r.path}`, { waitUntil: 'domcontentloaded' });
        await page.waitForTimeout(1000);

        // 微式组件检查
        const inspection = await page.evaluate(() => {
          const main = document.querySelector('main') || document.body;
          const pageHint = main.querySelector('.page-hint');
          const termTips = main.querySelectorAll('.term-tip');
          const statusTags = main.querySelectorAll('.status-tag');
          const emptyState = main.querySelector('.el-empty');
          const tables = main.querySelectorAll('.el-table');
          const forms = main.querySelectorAll('.el-form');
          const buttons = main.querySelectorAll('.el-button');
          const numbers = (main.innerText.match(/[\d,]+(\.\d+)?/g) || []).filter(n => n.includes(','));

          return {
            title: document.querySelector('h1, .page-title')?.textContent?.trim(),
            hasPageHint: !!pageHint,
            termTipCount: termTips.length,
            statusTagCount: statusTags.length,
            hasEmptyState: !!emptyState,
            tableCount: tables.length,
            formCount: forms.length,
            buttonCount: buttons.length,
            formattedNumberSamples: [...new Set(numbers)].slice(0, 3)
          };
        });

        console.log(`✓ [${roleKey}] ${r.name} (${r.path}) | 提示条: ${inspection.hasPageHint ? '有' : '无'} | 术语提示: ${inspection.termTipCount} | 状态标: ${inspection.statusTagCount} | 表格: ${inspection.tableCount} | 表单: ${inspection.formCount} | 千分位样例: [${inspection.formattedNumberSamples.join(', ')}]`);
        results.passedRoutes++;
        results.componentsInspected += inspection.termTipCount + inspection.statusTagCount + inspection.tableCount + inspection.formCount;
        results.routes.push({ role: roleKey, path: r.path, status: 'pass', inspection });
      } catch (err) {
        console.error(`✗ [${roleKey}] ${r.name} (${r.path}) -> 失败: ${err.message}`);
        results.failedRoutes++;
        results.routes.push({ role: roleKey, path: r.path, status: 'fail', error: err.message });
      }
    }
  }

  await browser.close();

  console.log('\n================ 巡检总结 ================');
  console.log(`总共巡检路由数: ${results.passedRoutes + results.failedRoutes}`);
  console.log(`成功通过路由数: ${results.passedRoutes}`);
  console.log(`失败异常路由数: ${results.failedRoutes}`);
  console.log(`巡检内嵌组件累计数: ${results.componentsInspected}`);
  console.log(`控制台未捕获错误数: ${consoleErrors.length}`);
  if (consoleErrors.length > 0) {
    console.log('未捕获错误详情:');
    consoleErrors.forEach(e => console.log('  -', e));
  }
}

run().catch(err => {
  console.error('Fatal patrol runner error:', err);
  process.exit(1);
});
