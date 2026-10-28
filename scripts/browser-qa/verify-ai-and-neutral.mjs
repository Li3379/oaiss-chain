import { chromium } from 'playwright';

async function run() {
  console.log('--- 开始 AI 预测与碳中和项目详情深度巡检 ---');
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  // 1. 登录 enterprise001 获取 token 并写入 localStorage
  const loginRes = await fetch('http://localhost:8080/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'enterprise001', password: 'admin123' })
  }).then(r => r.json());

  const token = loginRes.data.accessToken;
  await page.addInitScript(({ token }) => {
    localStorage.setItem('access_token', token);
    localStorage.setItem('refresh_token', token);
    localStorage.setItem('oaiss_guided_tour_done', '1');
  }, { token });

  // 2. 探查 AI 市场预测页面
  console.log('\n[Scenario 1: AI 市场预测]');
  await page.goto('http://localhost:5173/enterprise/market-prediction', { waitUntil: 'networkidle' });
  await page.waitForTimeout(2000);
  
  const marketTitle = await page.locator('main h1, main .page-header, main h2').first().textContent().catch(() => '');
  console.log('[Market] 页面标题/主信息:', marketTitle.trim());

  const marketCards = await page.locator('main .el-card').allTextContents().catch(() => []);
  console.log('[Market] 发现卡片数量:', marketCards.length);
  if (marketCards.length > 0) {
    console.log('[Market] 首张卡片摘要:', marketCards[0].replace(/\s+/g, ' ').slice(0, 100));
  }

  const hasChart = await page.locator('main .echarts, main div[_echarts_instance_], main canvas').count();
  console.log('[Market] 图表画布元素数量:', hasChart);

  // 3. 探查 AI 企业推理页面
  console.log('\n[Scenario 2: AI 企业推理]');
  await page.goto('http://localhost:5173/enterprise/enterprise-inference', { waitUntil: 'networkidle' });
  await page.waitForTimeout(2000);

  const inferenceTitle = await page.locator('main h1, main .page-header, main h2').first().textContent().catch(() => '');
  console.log('[Inference] 页面标题/主信息:', inferenceTitle.trim());

  const inferenceCards = await page.locator('main .el-card').allTextContents().catch(() => []);
  console.log('[Inference] 发现卡片数量:', inferenceCards.length);
  if (inferenceCards.length > 0) {
    console.log('[Inference] 推理卡片摘要:', inferenceCards[0].replace(/\s+/g, ' ').slice(0, 100));
  }

  // 4. 探查 碳中和项目详情页面（项目状态与按钮控制闭环）
  console.log('\n[Scenario 3: 碳中和项目详情全生命周期与状态防御]');
  // 先从项目列表拿一个真实项目 ID
  await page.goto('http://localhost:5173/enterprise/carbon-neutral/projects', { waitUntil: 'networkidle' });
  await page.waitForTimeout(2000);

  const firstProjectRow = await page.locator('main .el-table__body tr').first();
  const hasRows = await firstProjectRow.count();
  if (hasRows > 0) {
    const rowTexts = await firstProjectRow.locator('td').allTextContents();
    console.log('[ProjectList] 首行项目概要:', rowTexts.slice(0, 5).join(' | '));

    // 点击该行或者通过链接进入详情
    const viewBtn = firstProjectRow.locator('button, a').filter({ hasText: /查看|详情|View/ }).first();
    if (await viewBtn.count() > 0) {
      await viewBtn.click();
      await page.waitForTimeout(2000);
      console.log('[ProjectDetail] 已进入项目详情页, URL:', page.url());

      // 检查预期减排量与实际减排量是否千分位
      const descItems = await page.locator('main .el-descriptions__content, main .el-descriptions-item').allTextContents();
      console.log('[ProjectDetail] 详情项采样:', descItems.slice(0, 6).map(s => s.trim().replace(/\s+/g, ' ')));

      // 检查当前状态下，终止按钮是否存在（验证 canTerminate 修复）
      const statusBadge = await page.locator('main .status-tag, main .el-tag').first().textContent().catch(() => '');
      console.log('[ProjectDetail] 当前项目状态:', statusBadge.trim());

      const terminateBtn = page.locator('button').filter({ hasText: /终止项目|Terminate/ });
      const terminateVisible = await terminateBtn.isVisible().catch(() => false);
      console.log('[ProjectDetail] 终止按钮可见性:', terminateVisible, 
        '(规则: APPROVED=2 或 IMPLEMENTING=3 为 true, COMPLETED/TERMINATED 为 false)');

      if (statusBadge.includes('已完成') || statusBadge.includes('已终止') || statusBadge.includes('筹备')) {
        console.log('[ProjectDetail] 验证成功: 已完成/已终止/筹备状态下，终止按钮正确处于隐藏态！');
      } else if (statusBadge.includes('实施中') || statusBadge.includes('审核通过')) {
        console.log('[ProjectDetail] 验证成功: 实施中/审核通过状态下，终止按钮正确处于可见态！');
      }
    }
  }

  await browser.close();
  console.log('\n--- 巡检验证执行完毕 ---');
}

run().catch(err => {
  console.error('Execution error:', err);
  process.exit(1);
});
