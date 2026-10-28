import { chromium } from 'playwright';
import fs from 'fs';
import path from 'path';

const BASE_URL = 'http://localhost:5173';
const API_BASE = 'http://localhost:8080/api/v1';

async function run() {
  console.log('--- 开始拓展性 browse 业务验收 ---');
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  // Login
  const loginRes = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'enterprise001', password: 'admin123' })
  });
  const loginData = await loginRes.json();
  const token = loginData.data.accessToken;
  const refresh = loginData.data.refreshToken;

  await page.goto(`${BASE_URL}/login`);
  await page.evaluate(({ t, r }) => {
    localStorage.setItem('access_token', t);
    localStorage.setItem('refresh_token', r);
    localStorage.setItem('oaiss_guided_tour_done', '1');
  }, { t: token, r: refresh });

  // 1. Dashboard 概览验收
  await page.goto(`${BASE_URL}/enterprise/company/dashboard`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(2000);
  const cards = await page.locator('.stat-card, .data-card, .el-card').count();
  await page.screenshot({ path: '.playwright-mcp/browse-dashboard.png' });
  console.log(`[Dashboard] 渲染大屏卡片数: ${cards}`);

  // 2. 个人中心验收
  await page.goto(`${BASE_URL}/enterprise/user/profile`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(2000);
  const profileHint = await page.locator('.page-hint').textContent();
  await page.screenshot({ path: '.playwright-mcp/browse-profile.png' });
  console.log(`[Profile] 个人中心 PageHint 渲染: ${profileHint.trim().substring(0, 50)}...`);

  // Admin login 
  const adminRes = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' })
  });
  const adminData = await adminRes.json();
  await page.evaluate(({ t, r }) => {
    localStorage.setItem('access_token', t);
    localStorage.setItem('refresh_token', r);
  }, { t: adminData.data.accessToken, r: adminData.data.refreshToken });

  // 3. 证书签发表单交互验收
  await page.goto(`${BASE_URL}/admin/certificates`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(2000);
  await page.locator('main button').filter({ hasText: /签发|签发第一张证书/ }).first().click();
  await page.waitForTimeout(1000);
  await page.screenshot({ path: '.playwright-mcp/browse-issue-cert.png' });
  
  const dialogVisible = await page.locator('.el-dialog').isVisible();
  console.log(`[Admin Cert] 证书签发弹窗渲染可见: ${dialogVisible}`);

  await browser.close();
  console.log('--- 拓展性验收顺利完成 ---');
}

run().catch(console.error);