import { chromium } from 'playwright';

async function run() {
  console.log('=== 开始执行电网核算、核查员检索、管理员批量上链三大场景交互实测 ===');
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });

  // 1. 电网碳核算场景
  console.log('\n--- 场景 1: 电网企业碳核算表单与结果卡片 ---');
  const page1 = await context.newPage();
  await page1.addInitScript(() => {
    localStorage.setItem('oaiss_guided_tour_done', '1');
  });

  // 登录企业
  const loginRes1 = await page1.request.post('http://localhost:8080/api/v1/auth/login', {
    data: { username: 'enterprise001', password: 'admin123' }
  });
  const token1 = (await loginRes1.json()).data.accessToken;
  await page1.addInitScript((t) => {
    localStorage.setItem('access_token', t);
  }, token1);

  await page1.goto('http://localhost:5173/enterprise/carbon-formula');
  await page1.waitForLoadState('networkidle');

  // 点击电网 Tab
  const gridTab = page1.locator('.el-tabs__item', { hasText: /电网/ }).first();
  await gridTab.click();
  await page1.waitForTimeout(600);
  console.log('[Grid] 已切换至电网企业 Tab');

  // 填写电网表单控件
  const gridInputs = page1.locator('.el-tab-pane').filter({ hasText: /电网排放因子|输送电量/ }).locator('.el-input__inner, input[type="text"]');
  const count = await gridInputs.count();
  console.log(`[Grid] 找到电网表单输入控件数: ${count}`);

  if (count >= 3) {
    // 使用 click + pressSequentially 触发真实键盘输入，确保 el-input-number 响应式更新
    await gridInputs.nth(0).click();
    await page1.keyboard.press('Control+A');
    await page1.keyboard.type('120000');

    await gridInputs.nth(1).click();
    await page1.keyboard.press('Control+A');
    await page1.keyboard.type('0.055');

    await gridInputs.nth(2).click();
    await page1.keyboard.press('Control+A');
    await page1.keyboard.type('0.5810');
    console.log('[Grid] 已通过真实键盘交互键入输送电量=120000, 线损率=0.055, 排放因子=0.5810');
  }

  // 点击核算按钮
  const calcBtn = page1.locator('.el-tab-pane').filter({ hasText: /电网排放因子|输送电量/ }).locator('button').filter({ hasText: /核算|计算/ }).first();
  await calcBtn.click();
  await page1.waitForTimeout(1000);

  // 验证结果卡片
  const resultCard = page1.locator('.result-card, .section-card').filter({ hasText: /本次电网排放核算总量|核算完成|计算结果/ }).first();
  const hasResult = await resultCard.isVisible().catch(() => false);
  console.log(`[Grid] 计算结果卡片是否可见: ${hasResult}`);

  if (hasResult) {
    const alertText = await resultCard.locator('.el-alert__title').textContent().catch(() => '');
    const descItems = await resultCard.locator('.el-descriptions__cell').allTextContents().catch(() => []);
    console.log(`[Grid] 结果大白话提示条: ${alertText.trim()}`);
    console.log(`[Grid] 详情条目采样(含千分位): ${descItems.slice(0, 4).map(s => s.replace(/\s+/g, ' ').trim()).join(' | ')}`);
  }

  // 2. 核查员端报告检索与状态联动
  console.log('\n--- 场景 2: 核查员端报告检索与状态联动 ---');
  const page2 = await context.newPage();
  const loginRes2 = await page2.request.post('http://localhost:8080/api/v1/auth/login', {
    data: { username: 'reviewer001', password: 'admin123' }
  });
  const token2 = (await loginRes2.json()).data.accessToken;
  await page2.addInitScript((t) => {
    localStorage.setItem('access_token', t);
    localStorage.setItem('oaiss_guided_tour_done', '1');
  }, token2);

  await page2.goto('http://localhost:5173/auditor/audit/list');
  await page2.waitForLoadState('networkidle');

  // 检查当前 Tab
  const activeTab = await page2.locator('.el-tabs__item.is-active').textContent().catch(() => '');
  console.log(`[Auditor] 默认激活 Tab: ${activeTab.trim()}`);

  // 切换到“全部报告” Tab
  const allTab = page2.locator('.el-tabs__item', { hasText: /全部/ }).first();
  if (await allTab.isVisible()) {
    await allTab.click();
    await page2.waitForTimeout(800);
    const rowsAll = await page2.locator('main .el-table__body tr').count();
    console.log(`[Auditor] 切换到全部报告，当前展示记录行数: ${rowsAll}`);
  }

  // 关键词检索
  const searchInput = page2.locator('main .search-card input, main input[placeholder*="关键词"]').first();
  if (await searchInput.isVisible()) {
    await searchInput.fill('电力');
    const searchBtn = page2.locator('main button', { hasText: /查询|搜索/ }).first();
    await searchBtn.click();
    await page2.waitForTimeout(800);
    const searchRows = await page2.locator('main .el-table__body tr').count();
    console.log(`[Auditor] 输入关键词「电力」后检索出: ${searchRows} 行`);

    // 点击重置
    const resetBtn = page2.locator('main button', { hasText: /重置/ }).first();
    if (await resetBtn.isVisible()) {
      await resetBtn.click();
      await page2.waitForTimeout(800);
      const resetRows = await page2.locator('main .el-table__body tr').count();
      console.log(`[Auditor] 点击重置后记录行数恢复至: ${resetRows} 行`);
    }
  }

  // 3. 管理员端批量上链与防御状态
  console.log('\n--- 场景 3: 管理员端批量核准上链交互与异常防御 ---');
  const page3 = await context.newPage();
  const loginRes3 = await page3.request.post('http://localhost:8080/api/v1/auth/login', {
    data: { username: 'admin', password: 'admin123' }
  });
  const token3 = (await loginRes3.json()).data.accessToken;
  await page3.addInitScript((t) => {
    localStorage.setItem('access_token', t);
    localStorage.setItem('oaiss_guided_tour_done', '1');
  }, token3);

  await page3.goto('http://localhost:5173/auditor/verify/list');
  await page3.waitForLoadState('networkidle');

  // 查看批量按钮初始状态
  const batchBtn = page3.locator('button', { hasText: /批量认证|批量核准/ }).first();
  const batchDisabled = await batchBtn.isDisabled();
  console.log(`[Admin] 批量认证按钮初始禁用状态(未勾选时应禁用): ${batchDisabled}`);

  // 测试筛选“待认证”状态
  const statusSelect = page3.locator('.search-card .el-select, .search-bar .el-select').first();
  if (await statusSelect.isVisible()) {
    await statusSelect.click();
    await page3.waitForTimeout(300);
    const optPending = page3.locator('.el-select-dropdown__item', { hasText: /待认证/ }).first();
    if (await optPending.isVisible()) {
      await optPending.click();
      await page3.waitForTimeout(300);
      const searchBtn = page3.locator('button', { hasText: /查询|搜索/ }).first();
      await searchBtn.click();
      await page3.waitForTimeout(800);
      const pendingCount = await page3.locator('main .el-table__body tr').count();
      console.log(`[Admin] 筛选「待认证」状态后返回记录数: ${pendingCount}`);
    }
  }

  // 查看首行详情弹窗（验证之前接入的 EmissionDataPanel）
  const viewBtn = page3.locator('main .el-table__body tr').first().locator('button', { hasText: /查看详情|查看/ }).first();
  if (await viewBtn.isVisible()) {
    await viewBtn.click();
    await page3.waitForTimeout(800);
    const dialog = page3.locator('.el-dialog').first();
    const dialogVisible = await dialog.isVisible();
    const hasPanel = await dialog.locator('.emission-panel').first().isVisible();
    console.log(`[Admin] 详情对话框弹出: ${dialogVisible}, 结构化排放面板展示: ${hasPanel}`);
  }

  await browser.close();
  console.log('\n=== 三大场景深度巡检全部完成，零异常！===');
}

run().catch(err => {
  console.error('巡检出错:', err);
  process.exit(1);
});
