import { chromium } from 'playwright';

async function run() {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  // 1. API 登录并注入 token
  await page.goto('http://localhost:5173/login');
  await page.evaluate(async () => {
    const r = await fetch('http://localhost:8080/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'enterprise001', password: 'admin123' })
    });
    const res = await r.json();
    localStorage.setItem('access_token', res.data.accessToken);
    localStorage.setItem('refresh_token', res.data.refreshToken);
    localStorage.setItem('oaiss_guided_tour_done', '1');
  });

  // 2. 跳转到碳核算公式计算器
  await page.goto('http://localhost:5173/enterprise/carbon-formula');
  await page.waitForLoadState('networkidle');

  // 3. 切换到电网行业 Tab
  const gridTab = page.locator('.el-tabs__item').filter({ hasText: /电网/ });
  await gridTab.click();
  await page.waitForTimeout(500);

  // 4. 填写电网参数（输送电量 120,000 MWh，线损率 0.055，排放因子 0.5810）
  await page.evaluate(() => {
    function setInput(selector, val) {
      const el = document.querySelector(selector);
      if (el) {
        el.value = val;
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        el.dispatchEvent(new Event('blur', { bubbles: true }));
      }
    }
    const pane = document.querySelector('.el-tab-pane:not([style*="display: none"])');
    const inputs = pane ? pane.querySelectorAll('input.el-input__inner') : [];
    // inputs[0] 是年份, inputs[1] 是企业名称, inputs[2] 是输送电量, inputs[3] 是线损率, inputs[4] 是排放因子
    if (inputs[2]) {
      inputs[2].value = '120000';
      inputs[2].dispatchEvent(new Event('input', { bubbles: true }));
      inputs[2].dispatchEvent(new Event('change', { bubbles: true }));
      inputs[2].dispatchEvent(new Event('blur', { bubbles: true }));
    }
    if (inputs[3]) {
      inputs[3].value = '0.055';
      inputs[3].dispatchEvent(new Event('input', { bubbles: true }));
      inputs[3].dispatchEvent(new Event('change', { bubbles: true }));
      inputs[3].dispatchEvent(new Event('blur', { bubbles: true }));
    }
    if (inputs[4]) {
      inputs[4].value = '0.5810';
      inputs[4].dispatchEvent(new Event('input', { bubbles: true }));
      inputs[4].dispatchEvent(new Event('change', { bubbles: true }));
      inputs[4].dispatchEvent(new Event('blur', { bubbles: true }));
    }
  });

  await page.waitForTimeout(400);

  // 5. 点击“核算”按钮
  const calcBtn = page.locator('.el-tab-pane:not([style*="display: none"]) button').filter({ hasText: /核算|计算/ }).first();
  await calcBtn.click();
  await page.waitForTimeout(1500);

  // 6. 检查结果卡片
  const resultCard = page.locator('.el-tab-pane:not([style*="display: none"]) .el-card').filter({ hasText: /计算结果/ });
  const isVisible = await resultCard.isVisible();
  console.log('[Grid Live] 结果卡片可见:', isVisible);

  if (isVisible) {
    const hintText = await resultCard.locator('.page-hint').textContent().catch(() => '');
    console.log('[Grid Live] 结果大白话提示:', hintText.trim());

    const items = await resultCard.locator('.el-descriptions__cell').allTextContents();
    console.log('[Grid Live] 结果条目内容:', items.map(s => s.trim()).filter(Boolean));
  }

  await browser.close();
}

run().catch(e => {
  console.error('Error:', e);
  process.exit(1);
});
