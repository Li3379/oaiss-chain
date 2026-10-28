#!/usr/bin/env node
/**
 * AC2 证据采集：登录后批量截图 28 个内页，输出到 .omc/reviews/ac2-after/
 *
 * 登录方式：走真实 UI 登录（验证码由人工视觉读取后经环境变量传入），
 * 拿到 token 后注入 localStorage，再逐页导航截图。
 */
import { chromium } from 'playwright';
import { mkdirSync, writeFileSync, existsSync } from 'node:fs';

const BASE = process.env.BASE_URL || 'http://localhost:5174';
const OUT = '../.omc/reviews/ac2-after';
const VIEWPORT = { width: 1440, height: 900 };

/** 页面清单：路由 → pen 参考图名（与实际 png 文件名对应） */
const PAGES = [
  // 企业端（用 ENTERPRISE token）
  ['/enterprise/carbon/upload', '01 碳核算填报', 'ENT'],
  ['/official-home', '02 官方网站首页', null],
  ['/login', '03 登录', null],
  ['/enterprise/emission/data', '04 排放数据', 'ENT'],
  ['/enterprise/carbon-neutral/projects', '05 碳中和项目', 'ENT'],
  ['/enterprise/carbon-neutral/projects/1', '06 项目详情', 'ENT'],
  ['/enterprise/carbon-formula', '07 碳核算公式', 'ENT'],
  ['/enterprise/company/dashboard', '08 数据可视化', 'ENT'],
  ['/enterprise/trading/market', '09 双向拍卖', 'ENT'],
  ['/enterprise/trading/p2p', '10 P2P交易', 'ENT'],
  ['/enterprise/orders/manage', '11 订单管理', 'ENT'],
  ['/enterprise/carbon-coin/account', '12 碳币账户', 'ENT'],
  ['/enterprise/credit/score', '13 信誉评分', 'ENT'],
  ['/enterprise/blockchain/browser', '14 区块链浏览器', 'ENT'],
  ['/enterprise/market-prediction', '15 AI市场预测', 'ENT'],
  ['/enterprise/enterprise-inference', '16 AI企业推理', 'ENT'],
  ['/enterprise/info', '17 企业信息', 'ENT'],
  ['/enterprise/user/profile', '18 个人中心', 'ENT'],
  // 审核端
  ['/auditor/audit/list', '19 待审报告', 'REV'],
  ['/auditor/project/review', '20 项目核证', 'REV'],
  ['/auditor/review/history', '21 审核历史', 'REV'],
  ['/auditor/verify/list', '22 上链补救', 'REV'],
  // 监管端
  ['/third-party/monitor', '23 监管面板', 'GOV'],
  // 管理端
  ['/admin/system/users', '24 用户管理', 'ADM'],
  ['/admin/system/carbon', '25 碳核算管理', 'ADM'],
  ['/admin/system/config', '26 系统配置', 'ADM'],
  ['/admin/data/statistics', '27 统计数据', 'ADM'],
  ['/admin/certificates', '28 证书管理', 'ADM'],
];

const ROLE_USERS = {
  ENT: process.env.ENT_USER || 'enterprise001',
  REV: process.env.REV_USER || 'reviewer001',
  GOV: process.env.GOV_USER || 'thirdparty001',
  ADM: process.env.ADM_USER || 'admin',
};

mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: 1 });
const page = await ctx.newPage();

// ---- 登录：按角色取 token（缓存到 .tokens.json） ----
import { readFileSync } from 'node:fs';

const PASS = process.env.LOGIN_PASS;
const CAPTCHA = process.env.LOGIN_CAPTCHA;

let tokenCache = {};
try {
  tokenCache = JSON.parse(readFileSync('.tokens.json', 'utf8'));
} catch { /* 首次运行无缓存 */ }

async function loginAs(page, roleKey, username) {
  if (tokenCache[roleKey]) return tokenCache[roleKey];
  if (!PASS || !CAPTCHA) return null;

  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
  await page.evaluate(() => localStorage.clear());
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(800);

  const respP = page.waitForResponse(
    (r) => r.url().includes('/auth/login') && r.request().method() === 'POST',
    { timeout: 20000 },
  );
  await page.evaluate(({ u, p, c }) => {
    const ins = [...document.querySelectorAll('input')];
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
    const set = (el, v) => { setter.call(el, v); el.dispatchEvent(new Event('input', { bubbles: true })); };
    set(ins[0], u); set(ins[1], p); set(ins[2], c);
    document.querySelector('.submit-btn').click();
  }, { u: username, p: PASS, c: CAPTCHA });

  const resp = await respP.catch(() => null);
  if (!resp) return null;
  const body = await resp.json().catch(() => null);
  const tk = body?.data?.accessToken || body?.data?.token || null;
  if (tk) {
    tokenCache[roleKey] = tk;
    writeFileSync('.tokens.json', JSON.stringify(tokenCache, null, 2));
    console.log(`login ${roleKey} (${username}) ok`);
  }
  return tk;
}

const report = [];
let currentRole = null;

async function applyToken(roleKey) {
  if (currentRole === roleKey) return true;
  const tk = await loginAs(page, roleKey, ROLE_USERS[roleKey]);
  if (!tk) return false;
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
  await page.evaluate(({ t }) => {
    localStorage.setItem('access_token', t);
    localStorage.setItem('refresh_token', 'ac2-refresh');
    for (const k of Object.keys(localStorage)) {
      if (k.startsWith('oaiss_guided_tour_done')) localStorage.setItem(k, '1');
    }
    localStorage.setItem('oaiss_guided_tour_done', '1');
  }, { t: tk });
  currentRole = roleKey;
  return true;
}

const ROLE_FILTER = process.env.ROLE_FILTER;
for (const [route, penRef, roleKey] of PAGES) {
  if (ROLE_FILTER && roleKey && roleKey !== ROLE_FILTER) continue;
  const safe = penRef.replace(/[^\w一-龥]+/g, '_');
  const file = `${OUT}/${safe}.png`;
  try {
    if (roleKey && !(await applyToken(roleKey))) {
      report.push({ route, penRef, roleKey, ok: false, why: 'no token for role' });
      console.log(`SKIP ${penRef} (no ${roleKey} token)`);
      continue;
    }
    await page.goto(`${BASE}${route}`, { waitUntil: 'networkidle', timeout: 25000 });
    await page.waitForTimeout(1200);
    await page.evaluate(() => {
      const skip = [...document.querySelectorAll('button')].find((b) => /跳过|skip/i.test(b.textContent || ''));
      if (skip) skip.click();
      const close = document.querySelector('.el-dialog__headerbtn');
      if (close) close.click();
    }).catch(() => {});
    await page.waitForTimeout(400);
    const landed = new URL(page.url()).pathname;
    const expected = route;
    const matched = landed === expected || (expected.includes('/') && landed.startsWith(expected.split('/').slice(0, 3).join('/')));
    if (landed.startsWith('/login')) {
      report.push({ route, penRef, roleKey, ok: false, why: 'redirected to login' });
      console.log(`SKIP ${penRef} (login redirect)`);
      continue;
    }
    await page.screenshot({ path: file, fullPage: false });
    report.push({ route, penRef, roleKey, ok: true, file, landed, landedMatch: landed === expected });
    console.log(`${landed === expected ? 'OK  ' : 'DIFF'} ${penRef}  ${landed}`);
  } catch (e) {
    report.push({ route, penRef, roleKey, ok: false, why: String(e).slice(0, 120) });
    console.log(`FAIL ${penRef}  ${String(e).slice(0, 80)}`);
  }
}

writeFileSync(`${OUT}/capture-report.json`, JSON.stringify(report, null, 2));
const ok = report.filter((r) => r.ok).length;
console.log(`\n捕获 ${ok}/${report.length}`);
await browser.close();
process.exit(ok === report.length ? 0 : 1);