import { chromium } from 'playwright';

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173';
const API_URL = process.env.API_BASE_URL || 'http://localhost:8080/api/v1';

async function testNotification() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  page.on('console', msg => console.log(`[Browser Console] ${msg.type()}: ${msg.text()}`));

  // 1. 先登录
  const r = await page.request.post(`${API_URL}/auth/login`, {
    data: { username: 'enterprise001', password: 'admin123' }
  });
  const res = await r.json();
  const token = res.data.accessToken;
  const refresh = res.data.refreshToken;

  await page.addInitScript(({ t, ref }) => {
    localStorage.setItem('access_token', t);
    localStorage.setItem('refresh_token', ref);
    localStorage.setItem('oaiss_guided_tour_done', '1');
    localStorage.setItem('locale', 'zh-CN');
  }, { t: token, ref: refresh });

  await page.goto(`${BASE_URL}/enterprise/company/dashboard`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1500);

  console.log('Current URL:', page.url());

  const headerHtml = await page.locator('.top-header').innerHTML().catch(() => 'NO_HEADER');
  console.log('Header HTML snippet:', headerHtml.slice(0, 300));

  const allButtons = await page.locator('button').allTextContents();
  console.log('All buttons on page:', allButtons.slice(0, 10));

  // 检查是否有带 Bell 的 icon 或者 badge
  const bells = await page.locator('[class*="bell"]').all();
  console.log('Elements with "bell" in class:', bells.length);

  await browser.close();
}

testNotification().catch(console.error);
