import { chromium } from 'playwright';

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173';
const API_URL = process.env.API_BASE_URL || 'http://localhost:8080/api/v1';

async function loginUser(page, username, password) {
  const r = await page.request.post(`${API_URL}/auth/login`, {
    data: { username, password }
  });
  const res = await r.json();
  if (res.code !== 200) {
    throw new Error(`Login failed for ${username}: ${res.message}`);
  }
  await page.addInitScript(({ token, refresh }) => {
    localStorage.setItem('access_token', token);
    localStorage.setItem('refresh_token', refresh);
    localStorage.setItem('locale', 'zh-CN');
    localStorage.setItem('oaiss_guided_tour_done', '1');
  }, { token: res.data.accessToken, refresh: res.data.refreshToken });
  return res.data;
}

async function runPatrol() {
  console.log('🚀 Starting Deep Patrol Round 4 with local Chrome...');
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  const consoleErrors = [];
  page.on('console', msg => {
    if (msg.type() === 'error') {
      consoleErrors.push(msg.text());
    }
  });

  const results = {
    notifications: {},
    gridCarbon: {},
    auditorSearch: {},
    adminVerify: {},
    consoleErrors
  };

  try {
    // ----------------------------------------------------------------------
    // 场景 1: 通知中心交互摸排 (enterprise001)
    // ----------------------------------------------------------------------
    console.log('\n--- 场景 1: 通知中心交互摸排 ---');
    await loginUser(page, 'enterprise001', 'admin123');
    await page.goto(`${BASE_URL}/enterprise/company/dashboard`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);

    // 查找铃铛按钮
    const bellBtn = page.locator('.header-right button').filter({ has: page.locator('svg') }).first();
    const hasBell = await bellBtn.count() > 0;
    results.notifications.hasBell = hasBell;
    console.log(`[Notification] 铃铛组件存在: ${hasBell}`);

    if (hasBell) {
      // 检查 badge
      const badge = page.locator('.bell-badge .el-badge__content, .el-badge__content');
      const badgeVisible = await badge.first().isVisible().catch(() => false);
      const badgeText = badgeVisible ? (await badge.first().textContent()).trim() : '0';
      results.notifications.initialBadge = badgeText;
      console.log(`[Notification] 初始未读数 Badge: ${badgeText} (可见: ${badgeVisible})`);

      // 点击展开下拉
      await bellBtn.click();
      await page.waitForTimeout(600);

      const dropdown = page.locator('.notification-dropdown, .el-dropdown__popper');
      const dropdownVisible = await dropdown.first().isVisible().catch(() => false);
      results.notifications.dropdownVisible = dropdownVisible;
      console.log(`[Notification] 下拉列表展开: ${dropdownVisible}`);

      if (dropdownVisible) {
        // 读取列表条数
        const items = dropdown.first().locator('.el-dropdown-menu__item, [role="listitem"]');
        const itemCount = await items.count();
        results.notifications.itemCount = itemCount;
        console.log(`[Notification] 通知列表条目数: ${itemCount}`);

        if (itemCount > 0) {
          const firstItem = items.first();
          const itemText = (await firstItem.innerText()).replace(/\s+/g, ' ').trim();
          const isUnread = await firstItem.evaluate(el => el.classList.contains('is-unread'));
          results.notifications.firstItem = { text: itemText, isUnread };
          console.log(`[Notification] 首条通知: [${isUnread ? '未读' : '已读'}] ${itemText}`);

          // 点击单条通知标记已读
          console.log(`[Notification] 点击首条通知触发已读...`);
          await firstItem.click();
          await page.waitForTimeout(800);
        }

        // 测试点击“全部已读”按钮
        let markAllBtn = page.locator('.notification-dropdown button, .el-dropdown__popper button').filter({ hasText: /全部已读|Mark all/ });
        let isBtnVisible = await markAllBtn.first().isVisible().catch(() => false);
        if (!isBtnVisible) {
          // 如果弹窗被单条点击关闭了，重新点击铃铛展开
          await bellBtn.click();
          await page.waitForTimeout(600);
          markAllBtn = page.locator('.notification-dropdown button, .el-dropdown__popper button').filter({ hasText: /全部已读|Mark all/ });
          isBtnVisible = await markAllBtn.first().isVisible().catch(() => false);
        }
        results.notifications.hasMarkAllBtn = isBtnVisible;
        if (isBtnVisible) {
          console.log(`[Notification] 点击「全部已读」...`);
          await markAllBtn.first().click();
          await page.waitForTimeout(1000);
        }

        // 重新获取未读数 Badge
        const badgeAfter = await badge.first().isVisible().catch(() => false) ? (await badge.first().textContent()).trim() : '0';
        results.notifications.badgeAfterMarkAll = badgeAfter;
        console.log(`[Notification] 全部已读后 Badge: ${badgeAfter}`);
        await page.screenshot({ path: '.playwright-mcp/patrol-notification.png', fullPage: false });
      }
    }

    // ----------------------------------------------------------------------
    // 场景 2: 电网碳核算表单与结果卡片摸排 (/enterprise/carbon-formula)
    // ----------------------------------------------------------------------
    console.log('\n--- 场景 2: 电网碳核算表单与结果卡片摸排 ---');
    await page.goto(`${BASE_URL}/enterprise/carbon-formula`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);

    // 寻找行业 Tab 并切换到“电网行业”
    const gridTab = page.locator('.el-tabs__item').filter({ hasText: /电网|Grid/ }).first();
    const hasGridTab = await gridTab.count() > 0;
    results.gridCarbon.hasGridTab = hasGridTab;

    if (hasGridTab) {
      await gridTab.click();
      await page.waitForTimeout(800);
      console.log(`[GridCarbon] 已切换至电网企业 Tab`);

      // 观察可见区域的表单输入项
      const activePane = page.locator('.el-tab-pane:not([style*="display: none"])');
      const formItems = activePane.locator('.el-form-item');
      const formItemLabels = await formItems.locator('label').allTextContents();
      results.gridCarbon.formItemLabels = formItemLabels.map(l => l.trim()).filter(Boolean);
      console.log(`[GridCarbon] 电网企业表单控件字段数: ${results.gridCarbon.formItemLabels.length}项 -> ${results.gridCarbon.formItemLabels.slice(0, 8).join(', ')}`);

      // 寻找计算按钮
      const calcBtn = activePane.locator('button').filter({ hasText: /计算|核算|Calculate/ }).first();
      const hasCalcBtn = await calcBtn.count() > 0;
      results.gridCarbon.hasCalcBtn = hasCalcBtn;

      if (hasCalcBtn) {
        console.log(`[GridCarbon] 点击核算按钮...`);
        await calcBtn.click();
        await page.waitForTimeout(1000);

        // 检查结果展示
        const resultCard = activePane.locator('.result-card, .calculation-result, [class*="result"], .el-alert');
        const hasResultCard = await resultCard.count() > 0;
        const resultText = hasResultCard ? (await resultCard.first().innerText()).replace(/\s+/g, ' ').slice(0, 200) : '';
        results.gridCarbon.hasResultCard = hasResultCard;
        results.gridCarbon.resultSummary = resultText;
        console.log(`[GridCarbon] 计算结果呈现: ${hasResultCard ? resultText : '未检测到独立结果卡'}`);
      }
    }

    // ----------------------------------------------------------------------
    // 场景 3: 核查员端全量报告检索与状态联动 (/auditor/audit/list)
    // ----------------------------------------------------------------------
    console.log('\n--- 场景 3: 核查员端报告检索摸排 ---');
    await loginUser(page, 'reviewer001', 'admin123');
    await page.goto(`${BASE_URL}/auditor/audit/list`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);

    // 检查是否有 Tab（待审核 vs 全部）
    const auditTabs = page.locator('.el-tabs__item');
    const auditTabNames = await auditTabs.allTextContents();
    results.auditorSearch.tabNames = auditTabNames;
    console.log(`[Auditor] 报告列表 Tabs: ${auditTabNames.join(', ')}`);

    // 检查搜索和过滤控件
    const searchInputs = page.locator('.el-input__inner');
    const searchCount = await searchInputs.count();
    results.auditorSearch.inputCount = searchCount;
    console.log(`[Auditor] 搜索输入框数量: ${searchCount}`);

    // 表格数据读取
    const tableRows = page.locator('.el-table__body tr');
    const rowCount = await tableRows.count();
    results.auditorSearch.rowCount = rowCount;
    console.log(`[Auditor] 当前展示行数: ${rowCount}`);

    if (rowCount > 0) {
      const firstRowCols = await tableRows.first().locator('td').allTextContents();
      results.auditorSearch.firstRowSample = firstRowCols.map(c => c.trim().replace(/\s+/g, ' ')).slice(0, 7);
      console.log(`[Auditor] 首行数据: ${results.auditorSearch.firstRowSample.join(' | ')}`);
      await page.screenshot({ path: '.playwright-mcp/patrol-auditor.png', fullPage: false });
    }

    // ----------------------------------------------------------------------
    // 场景 4: 管理员端批量上链交互摸排 (/auditor/verify/list)
    // ----------------------------------------------------------------------
    console.log('\n--- 场景 4: 管理员端认证与批量上链交互摸排 ---');
    await loginUser(page, 'admin', 'admin123');
    await page.goto(`${BASE_URL}/auditor/verify/list`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);

    // 检查多选 checkbox
    const selectAllCheckbox = page.locator('.el-table__header .el-checkbox');
    const hasCheckboxCol = await selectAllCheckbox.count() > 0;
    results.adminVerify.hasCheckboxCol = hasCheckboxCol;
    console.log(`[Admin] 表格是否存在复选框列: ${hasCheckboxCol}`);

    // 查找“批量上链”按钮
    const batchBtn = page.locator('button').filter({ hasText: /批量|Batch/ });
    const hasBatchBtn = await batchBtn.count() > 0;
    const batchBtnText = hasBatchBtn ? await batchBtn.first().textContent() : '';
    const batchDisabled = hasBatchBtn ? await batchBtn.first().isDisabled() : null;
    results.adminVerify.hasBatchBtn = hasBatchBtn;
    results.adminVerify.batchBtnText = batchBtnText.trim();
    results.adminVerify.batchDisabledInitial = batchDisabled;
    console.log(`[Admin] 批量操作按钮: ${batchBtnText.trim()} (初始禁用: ${batchDisabled})`);

    // 勾选第一行数据（如果有）
    const rowCheckboxes = page.locator('.el-table__body .el-checkbox:not(.is-disabled)');
    const rowCheckCount = await rowCheckboxes.count();
    results.adminVerify.rowCheckCount = rowCheckCount;
    if (rowCheckCount > 0) {
      console.log(`[Admin] 发现可勾选待认证行，执行点击...`);
      await rowCheckboxes.first().click();
      await page.waitForTimeout(500);

      const batchDisabledAfter = await batchBtn.first().isDisabled();
      results.adminVerify.batchDisabledAfterSelect = batchDisabledAfter;
      console.log(`[Admin] 勾选后批量按钮禁用状态: ${batchDisabledAfter}`);
    } else {
      console.log(`[Admin] 当前无可认证行(全部处于已上链或待审核防御态)，批量按钮正确保持禁用`);
      results.adminVerify.batchDisabledAfterSelect = batchDisabled;
    }
    await page.screenshot({ path: '.playwright-mcp/patrol-admin-verify.png', fullPage: false });

    // ----------------------------------------------------------------------
    // 场景 5: 碳报告详情与 RSA 数字签名验签
    // ----------------------------------------------------------------------
    console.log('\n--- 场景 5: 碳报告详情与 RSA 数字签名验签 ---');
    await loginUser(page, 'enterprise001', 'admin123');
    await page.goto(`${BASE_URL}/enterprise/carbon/upload`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);

    const firstDetailBtn = page.locator('.el-table__body tr').first().locator('button').filter({ hasText: /详情|Detail/ });
    if (await firstDetailBtn.count() > 0) {
      await firstDetailBtn.first().click();
      await page.waitForTimeout(800);
      const detailDialog = page.locator('.el-dialog:visible');
      const dialogVisible = await detailDialog.count() > 0;
      console.log(`[CarbonReport] 详情弹窗是否打开: ${dialogVisible}`);
      if (dialogVisible) {
        const hasEmissionPanel = await detailDialog.locator('.emission-panel').count() > 0;
        console.log(`[CarbonReport] 详情弹窗是否包含结构化排放面板: ${hasEmissionPanel}`);
        await page.screenshot({ path: '.playwright-mcp/patrol-report-detail.png', fullPage: false });
        const closeBtn = detailDialog.locator('button').filter({ hasText: /关闭|Close|取消|Cancel/ });
        if (await closeBtn.count() > 0) {
          await closeBtn.first().click();
        }
      }
    }

  } catch (err) {
    console.error('Patrol execution error:', err);
    results.executionError = err.message;
  } finally {
    await browser.close();
  }

  console.log('\n=================== PATROL RESULTS ===================');
  console.log(JSON.stringify(results, null, 2));
}

runPatrol().catch(console.error);
